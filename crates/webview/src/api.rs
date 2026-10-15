//! The sidecar's HTTP API: request/response types, routing, and the bridge
//! that forwards commands into the tao event loop.
//!
//! Every operation the sidecar supports is one route on `127.0.0.1`:
//!
//! ```text
//! GET  /health                  -> {"ok":true,"version":"..."}
//! GET  /ready                   -> {"ok":true} (event loop readiness, no window)
//! GET  /events                  -> {"ok":true,"events":[{"type":"closed","id":"wv-1"}]}
//! GET  /api-calls               -> {"ok":true,"calls":[{"viewId","method","url","status",...}]}
//! GET  /dom-events              -> {"ok":true,"events":[{"viewId","type","element",...}]}
//! GET  /webview                 -> {"ok":true,"webviews":[{"id","title","url"}]}
//! POST /webview                 -> open; body OpenRequest -> {"ok":true,"id":"wv-1"}
//! POST /webview/{id}/navigate   {"url":"..."}
//! POST /webview/{id}/html       {"html":"..."}
//! POST /webview/{id}/eval       {"script":"..."} -> {"ok":true,"result":<json>}
//! POST /webview/{id}/cookies    {"url":"..."} -> {"ok":true,"cookies":[...]}
//! POST /webview/{id}/title      {"title":"..."}
//! POST /webview/{id}/size       {"width":800,"height":600}
//! POST /webview/{id}/position   {"x":100,"y":100}
//! POST /webview/{id}/show
//! POST /webview/{id}/hide
//! POST /webview/{id}/focus
//! POST /webview/{id}/close
//! POST /shutdown
//! ```
//!
//! Failures come back as `{"ok":false,"error":"..."}` with a 4xx/5xx status.
//! Webview work has to happen on the event loop thread, so the HTTP thread
//! sends each command through an [`EventLoopProxy`] and waits on a channel
//! for the reply.

use std::sync::mpsc::{self, Sender};
use std::io::Read;
use std::sync::{Arc, Mutex};
use std::time::Duration;

use serde::{Deserialize, Serialize};
use tao::event_loop::EventLoopProxy;
use tiny_http::{Header, Method, Response, Server};

pub const VERSION: &str = env!("CARGO_PKG_VERSION");

/// How long the HTTP thread waits for the event loop to answer a command.
const REPLY_TIMEOUT: Duration = Duration::from_secs(10);

/// Body of `POST /webview`. `url` wins over `html`; with neither the
/// webview opens blank.
#[derive(Debug, Clone, Deserialize)]
pub struct OpenRequest {
    pub url: Option<String>,
    pub html: Option<String>,
    #[serde(default = "default_title")]
    pub title: String,
    #[serde(default = "default_width")]
    pub width: f64,
    #[serde(default = "default_height")]
    pub height: f64,
    #[serde(default = "default_true")]
    pub resizable: bool,
    #[serde(default)]
    pub devtools: bool,
    #[serde(default)]
    pub headless: bool,
    #[serde(default, rename = "initializationScript")]
    pub initialization_script: Option<String>,
    #[serde(default, rename = "dataDirectory")]
    pub data_directory: Option<std::path::PathBuf>,
    /// Native handle (HWND) of the window that owns this one. An owned window stays above its
    /// owner, hides with it and has no taskbar entry, so a host can dock it over its own UI.
    #[serde(default, rename = "ownerWindow")]
    pub owner_window: Option<isize>,
    /// `false` removes the title bar and borders.
    #[serde(default = "default_true")]
    pub decorations: bool,
}

fn default_title() -> String {
    "WebView".to_string()
}

fn default_width() -> f64 {
    1024.0
}

fn default_height() -> f64 {
    768.0
}

fn default_true() -> bool {
    true
}

/// One open webview, as reported by `GET /webview`.
#[derive(Debug, Clone, Serialize)]
pub struct WebViewInfo {
    pub id: String,
    pub title: String,
    pub url: String,
    #[serde(rename = "dataDirectory")]
    pub data_directory: Option<std::path::PathBuf>,
}

/// Cookie values are credentials. Deliberately omit Debug to avoid accidental logging.
#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub struct CookieRecord {
    pub name: String,
    pub value: String,
    pub domain: String,
    pub path: String,
    pub http_only: bool,
    pub secure: bool,
}

/// One thing that happened outside a request, drained by `GET /events`.
#[derive(Debug, Clone, Serialize)]
pub struct EventRecord {
    #[serde(rename = "type")]
    pub kind: String,
    pub id: String,
}

#[derive(Debug, Serialize)]
pub struct ApiCallRecord {
    #[serde(rename = "viewId")]
    pub view_id: String,
    #[serde(flatten)]
    pub payload: serde_json::Map<String, serde_json::Value>,
}

/// Every operation the event loop can perform on behalf of a request.
#[derive(Debug)]
pub enum Command {
    Ping,
    Open(OpenRequest),
    Navigate { id: String, url: String },
    LoadHtml { id: String, html: String },
    Eval { id: String, script: String },
    Cookies { id: String, url: String },
    SetTitle { id: String, title: String },
    SetSize { id: String, width: f64, height: f64 },
    SetPosition { id: String, x: f64, y: f64 },
    /// Position and size in one step, in physical screen pixels.
    SetBounds { id: String, x: i32, y: i32, width: u32, height: u32 },
    Show { id: String },
    Hide { id: String },
    Focus { id: String },
    Close { id: String },
    List,
    Shutdown,
}

/// A command plus the channel its reply must be sent on.
pub struct Request {
    pub command: Command,
    pub reply: Sender<Reply>,
}

/// The event loop's answer to a [`Command`].
pub enum Reply {
    Opened {
        id: String,
    },
    Ok,
    /// The JS result of an eval, already serialized as JSON by the webview.
    EvalResult(String),
    WebViews(Vec<WebViewInfo>),
    Cookies(Vec<CookieRecord>),
    Error(String),
}

#[derive(Deserialize)]
struct NavigateBody {
    url: String,
}

#[derive(Deserialize)]
struct HtmlBody {
    html: String,
}

#[derive(Deserialize)]
struct EvalBody {
    script: String,
}

#[derive(Deserialize)]
struct TitleBody {
    title: String,
}

#[derive(Deserialize)]
struct SizeBody {
    width: f64,
    height: f64,
}

#[derive(Deserialize)]
struct BoundsBody {
    x: i32,
    y: i32,
    width: u32,
    height: u32,
}

#[derive(Deserialize)]
struct PositionBody {
    x: f64,
    y: f64,
}

/// Serves the API until the process exits. Runs on its own thread; every
/// webview command crosses to the event loop through `proxy`.
pub fn serve(
    server: Server,
    proxy: EventLoopProxy<Request>,
    events: Arc<Mutex<Vec<EventRecord>>>,
    api_calls: Arc<Mutex<Vec<ApiCallRecord>>>,
    dom_events: Arc<Mutex<Vec<ApiCallRecord>>>,
) {
    for mut request in server.incoming_requests() {
        // An Upgrade request's reader is the raw socket, not a framed body. Reading it to EOF
        // deadlocks with clients waiting for our response (notably Java's default h2c upgrade).
        let length = request.body_length();
        let upgrade = request.headers().iter().any(|header| header.field.equiv("Upgrade"));
        let body = match read_framed_body(request.as_reader(), length, upgrade) {
            Ok(body) => body,
            Err(_) => {
                let _ = request.respond(Response::from_string(error_body("could not read request body"))
                    .with_status_code(400));
                continue;
            }
        };
        let (status, response_body) = respond(
            request.method(),
            request.url(),
            &body,
            &proxy,
            &events,
            &api_calls,
            &dom_events,
        );
        let response = Response::from_string(response_body)
            .with_status_code(status)
            .with_header(
                Header::from_bytes(&b"Content-Type"[..], &b"application/json"[..]).unwrap(),
            );
        let _ = request.respond(response);
    }
}

fn read_framed_body(reader: &mut dyn Read, length: Option<usize>, upgrade: bool) -> std::io::Result<String> {
    let mut body = String::new();
    match length {
        Some(length) => { reader.take(length as u64).read_to_string(&mut body)?; }
        None if upgrade => {}
        None => { reader.read_to_string(&mut body)?; }
    }
    Ok(body)
}

fn respond(
    method: &Method,
    url: &str,
    body: &str,
    proxy: &EventLoopProxy<Request>,
    events: &Arc<Mutex<Vec<EventRecord>>>,
    api_calls: &Arc<Mutex<Vec<ApiCallRecord>>>,
    dom_events: &Arc<Mutex<Vec<ApiCallRecord>>>,
) -> (u16, String) {
    let path = url.split('?').next().unwrap_or(url);
    let segments: Vec<&str> = path.split('/').filter(|s| !s.is_empty()).collect();
    match (method, segments.as_slice()) {
        (&Method::Get, ["dom-events"]) => {
            let drained = std::mem::take(&mut *dom_events.lock().unwrap());
            (
                200,
                serde_json::json!({"ok": true, "events": drained}).to_string(),
            )
        }
        (&Method::Get, ["api-calls"]) => {
            let drained = std::mem::take(&mut *api_calls.lock().unwrap());
            (
                200,
                serde_json::json!({"ok": true, "calls": drained}).to_string(),
            )
        }
        (&Method::Get, ["health"]) => (200, format!(r#"{{"ok":true,"version":"{VERSION}"}}"#)),
        (&Method::Get, ["ready"]) => dispatch(proxy, Command::Ping),
        (&Method::Get, ["events"]) => {
            let drained: Vec<EventRecord> = std::mem::take(&mut *events.lock().unwrap());
            (
                200,
                format!(
                    r#"{{"ok":true,"events":{}}}"#,
                    serde_json::to_string(&drained).unwrap()
                ),
            )
        }
        (&Method::Get, ["webview"]) => dispatch(proxy, Command::List),
        (&Method::Post, ["webview"]) => {
            let payload = if body.trim().is_empty() { "{}" } else { body };
            match serde_json::from_str::<OpenRequest>(payload) {
                Ok(open) => dispatch(proxy, Command::Open(open)),
                Err(e) => bad_request(&e),
            }
        }
        (&Method::Post, ["webview", id, action]) => route_action(proxy, id, action, body),
        (&Method::Post, ["shutdown"]) => dispatch(proxy, Command::Shutdown),
        _ => (404, error_body("no such route")),
    }
}

fn route_action(
    proxy: &EventLoopProxy<Request>,
    id: &str,
    action: &str,
    body: &str,
) -> (u16, String) {
    let id = id.to_string();
    let command = match action {
        "navigate" => match serde_json::from_str::<NavigateBody>(body) {
            Ok(b) => Command::Navigate { id, url: b.url },
            Err(e) => return bad_request(&e),
        },
        "cookies" => match serde_json::from_str::<NavigateBody>(body) {
            Ok(b) => Command::Cookies { id, url: b.url },
            Err(e) => return bad_request(&e),
        },
        "html" => match serde_json::from_str::<HtmlBody>(body) {
            Ok(b) => Command::LoadHtml { id, html: b.html },
            Err(e) => return bad_request(&e),
        },
        "eval" => match serde_json::from_str::<EvalBody>(body) {
            Ok(b) => Command::Eval {
                id,
                script: b.script,
            },
            Err(e) => return bad_request(&e),
        },
        "title" => match serde_json::from_str::<TitleBody>(body) {
            Ok(b) => Command::SetTitle { id, title: b.title },
            Err(e) => return bad_request(&e),
        },
        "size" => match serde_json::from_str::<SizeBody>(body) {
            Ok(b) => Command::SetSize {
                id,
                width: b.width,
                height: b.height,
            },
            Err(e) => return bad_request(&e),
        },
        "position" => match serde_json::from_str::<PositionBody>(body) {
            Ok(b) => Command::SetPosition { id, x: b.x, y: b.y },
            Err(e) => return bad_request(&e),
        },
        "bounds" => match serde_json::from_str::<BoundsBody>(body) {
            Ok(b) => Command::SetBounds {
                id,
                x: b.x,
                y: b.y,
                width: b.width,
                height: b.height,
            },
            Err(e) => return bad_request(&e),
        },
        "show" => Command::Show { id },
        "hide" => Command::Hide { id },
        "focus" => Command::Focus { id },
        "close" => Command::Close { id },
        _ => return (404, error_body("no such action")),
    };
    dispatch(proxy, command)
}

fn dispatch(proxy: &EventLoopProxy<Request>, command: Command) -> (u16, String) {
    let (reply, receiver) = mpsc::channel();
    if proxy.send_event(Request { command, reply }).is_err() {
        return (503, error_body("the event loop is gone"));
    }
    match receiver.recv_timeout(REPLY_TIMEOUT) {
        Ok(Reply::Opened { id }) => (
            200,
            format!(
                r#"{{"ok":true,"id":{}}}"#,
                serde_json::to_string(&id).unwrap()
            ),
        ),
        Ok(Reply::Ok) => (200, r#"{"ok":true}"#.to_string()),
        Ok(Reply::EvalResult(value)) => {
            let result = if value.trim().is_empty() {
                "null".to_string()
            } else {
                value
            };
            (200, format!(r#"{{"ok":true,"result":{result}}}"#))
        }
        Ok(Reply::WebViews(list)) => (
            200,
            format!(
                r#"{{"ok":true,"webviews":{}}}"#,
                serde_json::to_string(&list).unwrap()
            ),
        ),
        Ok(Reply::Cookies(cookies)) => (
            200,
            serde_json::json!({"ok": true, "cookies": cookies}).to_string(),
        ),
        Ok(Reply::Error(message)) => (400, error_body(&message)),
        Err(_) => (504, error_body("timed out waiting for the event loop")),
    }
}

fn bad_request(error: &dyn std::fmt::Display) -> (u16, String) {
    (400, error_body(&format!("invalid request body: {error}")))
}

fn error_body(message: &str) -> String {
    format!(
        r#"{{"ok":false,"error":{}}}"#,
        serde_json::to_string(message).unwrap()
    )
}

#[cfg(test)]
mod tests {
    use super::read_framed_body;
    use std::io::{self, Cursor, Read};

    struct LiveConnection;
    impl Read for LiveConnection {
        fn read(&mut self, _: &mut [u8]) -> io::Result<usize> {
            panic!("an unframed upgrade request must not wait for socket EOF");
        }
    }

    #[test]
    fn upgrade_get_does_not_wait_for_connection_close() {
        assert_eq!(read_framed_body(&mut LiveConnection, None, true).unwrap(), "");
    }

    #[test]
    fn upgrade_post_reads_only_the_declared_body() {
        let mut connection = Cursor::new(b"{}next-request".as_slice());
        assert_eq!(read_framed_body(&mut connection, Some(2), true).unwrap(), "{}");
        assert_eq!(connection.position(), 2);
    }
}
