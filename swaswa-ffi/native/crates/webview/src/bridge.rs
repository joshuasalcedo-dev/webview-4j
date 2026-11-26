//! The page's bridge to Java. Every channel answers only the window's allowed origins. UI actions
//! take the fast channel; data takes the custom protocol (PROTOCOL.md §2.4):
//!
//! ```text
//! a small call (UI)   page --IPC frame--> Rust --> Java --> Rust --settle(...) script--> page
//!                     The answer's value is parsed and written back out as JSON before it goes
//!                     into the script, never pasted in as received. An answer over FAST_LIMIT is
//!                     settled with where to fetch it: POST <base>calls/<page call>.
//! a large call (data) POST <base>invoke/<command>: the body goes to Java unread, Java's
//!                     answer is the response, its Status and bytes unchanged
//! listen              POST <base>events, held until Java emits; answered with the waiting
//!                     view.<view>.emit.<topic> messages as frames
//! ```
//!
//! The page script is bridge.js, `window.__webview4j`, wrapped by @webview4j/api.

use std::collections::HashMap;
use std::sync::{Arc, Mutex};

use serde_json::Value;
use wry::RequestAsyncResponder;
use wry::http::{Request, Response, header};

use crate::frame::{self, Message};
use crate::payload::Outcome;

/// The custom protocol's name.
pub const SCHEME: &str = "webview4j";

/// Calls and answers up to this size take the fast channel; larger ones are data.
pub const FAST_LIMIT: usize = 64 * 1024;

/// Emits held for a page that is listening but not polling right now; beyond this, emits fail.
const MAX_QUEUED: usize = 16 * 1024 * 1024;

/// Where the protocol is reached from a page: Windows and Android map custom schemes onto a
/// `.localhost` host (https, so a secure page never makes an insecure request).
pub fn base_url() -> &'static str {
    if cfg!(any(target_os = "windows", target_os = "android")) {
        "https://webview4j.localhost/"
    } else {
        "webview4j://localhost/"
    }
}

pub fn script() -> String {
    include_str!("bridge.js")
        .replace("__WEBVIEW4J_BASE__", base_url())

        .replace("__WEBVIEW4J_FAST_LIMIT__", &FAST_LIMIT.to_string())
}

/// The origin of `url`, as a browser writes it in an `Origin` header; None for URLs without one.
/// Where a link a page follows goes.
#[derive(Debug, PartialEq, Eq)]
pub enum Navigation {
    /// One of the app's own pages (or about:, data:, blob:): in the window.
    Stay,
    /// Someone else's site, or an e-mail: in the system's browser or mail app, the app's page left as it is.
    Browser,
    /// Anything else (file:, a custom scheme): nowhere.
    Refuse,
}

/// Where following `url` goes, for a window whose own pages are on the `home` origins.
pub fn navigation(url: &str, home: &[String]) -> Navigation {
    let lower = url.trim().to_ascii_lowercase();
    if lower.starts_with("about:") || lower.starts_with("data:") || lower.starts_with("blob:") {
        return Navigation::Stay;
    }
    if let Some(origin) = origin_of(url) {
        if home.iter().any(|h| h.eq_ignore_ascii_case(&origin)) {
            return Navigation::Stay;
        }
    }
    // `*`: a browser, which goes to any website itself (the bridge still answers only its origins).
    if home.iter().any(|h| h == "*") && browsable(url) && !url.trim().to_ascii_lowercase().starts_with("mailto:") {
        return Navigation::Stay;
    }
    if browsable(url) { Navigation::Browser } else { Navigation::Refuse }
}

/// An http(s) or mailto address in one piece: what the system's browser is given (as one argument, no shell).
pub fn browsable(url: &str) -> bool {
    let lower = url.trim().to_ascii_lowercase();
    (lower.starts_with("https://") || lower.starts_with("http://") || lower.starts_with("mailto:"))
        && !url.trim().chars().any(|c| c.is_control() || c == ' ' || c == '"')
}

pub fn origin_of(url: &str) -> Option<String> {
    let uri: wry::http::Uri = url.parse().ok()?;
    let scheme = uri.scheme_str()?;
    // The custom protocol's own origin counts too: the app's files are served from it (`app/`).
    if !matches!(scheme, "http" | "https" | SCHEME) {
        return None;
    }
    Some(format!("{scheme}://{}", uri.authority()?.as_str().to_ascii_lowercase()))
}

/// The origin of the page at `url`: its scheme and host, or `null` (opaque) for `about:`,
/// `data:`, given HTML and the like.
pub fn page_origin(url: &str) -> String {
    origin_of(url).unwrap_or_else(|| "null".to_string())
}

/// What became of a message the page sent over IPC.
pub enum FromPage {
    /// A call to send Java.
    Java(Message),
    /// An answer for the page right away (a refusal), as the script that settles it.
    Page(String),
    /// Not a bridge message, or not one anybody can answer.
    Nothing,
}

/// Where Java's answer to a call goes.
enum Waiting {
    /// A fetch from the page, still open.
    Fetch(RequestAsyncResponder, String),
    /// A call that came over IPC: the answer settles it by the page's own number.
    Posted(u64),
    /// A request for one of the app's files, with the part of it asked for (a `Range` header), if any.
    Asset(RequestAsyncResponder, String, Option<String>),
}

/// The page script that settles the page's call `page_call`: `value` is the answer (200) or what
/// went wrong; `fetch`, when given, is where to fetch an answer too large for a script. Everything
/// is written in as JSON (a number, values serde wrote out), never as text it was given.
pub fn settle_script(page_call: u64, status: u16, value: &Value, fetch: Option<&str>) -> String {
    let fetch = fetch.map_or(Value::Null, |path| Value::String(path.to_string()));
    format!("window.__webview4j && window.__webview4j.settle({page_call}, {status}, {value}, {fetch});")
}

/// Where core's jar keeps webview-4j's own components (`@webview4j/ui`, built), served to every
/// window at `<base>ui/`. Not a package name, so the module system leaves it readable; Java serves
/// this class path directory from the start.
pub const UI_ROOT: &str = "webview4j-ui";

/// One view's side of the bridge: its open calls, its listener, and who may use it.
pub struct Link {
    view: String,
    origins: Vec<String>,
    /// The app's files, as Java named them; None serves none.
    assets: Option<String>,
    next_call: u64,
    calls: HashMap<u64, Waiting>,
    /// Answers too large to post, waiting for the page to fetch them, by the page's call number.
    large: HashMap<u64, Message>,
    poll: Option<(RequestAsyncResponder, String)>,
    queued: Vec<u8>,
    listening: bool,
    /// The number of the last emit that went out, by either channel.
    next_emit: u64,
}

/// Where an emit goes.
pub enum Emit {
    /// Small: run this script in the page now.
    Script(String),
    /// Large, or refused: handed to the custom protocol, with this outcome.
    Done(Outcome),
}

impl Link {
    #[cfg(test)]
    pub fn new(view: &str, origins: Vec<String>) -> Arc<Mutex<Link>> {
        Self::with_assets(view, origins, None)
    }

    /// A link that also serves the app's files, `assets`, at `<base>app/`.
    pub fn with_assets(view: &str, origins: Vec<String>, assets: Option<String>) -> Arc<Mutex<Link>> {
        Arc::new(Mutex::new(Link {
            view: view.to_string(),
            origins,
            assets,
            next_call: 0,
            calls: HashMap::new(),
            large: HashMap::new(),
            poll: None,
            queued: Vec::new(),
            listening: false,
            next_emit: 0,
        }))
    }

    pub fn allows(&self, origin: &str) -> bool {
        self.origins.iter().any(|allowed| allowed == origin)
    }

    /// A call the page sent over IPC: `MSG invoke.<command> ...` with a `Call` header, from the
    /// page at `page_url`.
    pub fn from_ipc(&mut self, raw: &str, page_url: &str) -> FromPage {
        let Ok(call) = frame::decode(raw.as_bytes()) else { return FromPage::Nothing };
        let Some(page_call) = call.get("Call").and_then(|c| c.parse::<u64>().ok()) else { return FromPage::Nothing };
        let Some(command) = call.subject.strip_prefix("invoke.") else { return FromPage::Nothing };
        if !self.allows(&page_origin(page_url)) {
            return FromPage::Page(settle_script(page_call, 403, &Value::String("this page's origin may not use the bridge".into()), None));
        }
        let subject = format!("view.{}.invoke.{command}", self.view);
        if !frame::valid_subject(&subject) {
            let refusal = format!("'{command}' is not a valid command name");
            return FromPage::Page(settle_script(page_call, 400, &Value::String(refusal), None));
        }
        self.next_call += 1;
        let java_call = self.next_call;
        self.calls.insert(java_call, Waiting::Posted(page_call));
        FromPage::Java(
            Message::new(subject)
                .header("Reply-To", format!("view.{}.call.{java_call}", self.view))
                .header("Content-Type", "application/json")
                .payload(call.payload),
        )
    }

    /// A request from the page over the custom protocol. Returns the message to send Java, if it is a call.
    pub fn request(&mut self, request: Request<Vec<u8>>, responder: RequestAsyncResponder) -> Option<Message> {
        let origin = request_origin(request.headers());
        if let Some(path) = request.uri().path().strip_prefix("/app/") {
            // The app's own files: a page loading itself and what it links to sends no Origin, so
            // these answer before the origin check. They are the app's code, not its data.
            let Some(root) = self.assets.clone() else {
                responder.respond(response(404, None, "text/plain", b"this window serves no files".to_vec()));
                return None;
            };
            let path = path.to_string();
            return self.asset_request(&request, root, path, origin, responder);
        }
        if let Some(path) = request.uri().path().strip_prefix("/ui/") {
            // webview-4j's own components, from core's jar: public library code, so any page in the
            // window may load them, from any origin.
            let origin = if origin.is_empty() { origin } else { "*".to_string() };
            let path = path.to_string();
            return self.asset_request(&request, UI_ROOT.to_string(), path, origin, responder);
        }
        if !self.allows(&origin) {
            responder.respond(response(403, None, "text/plain", format!("origin '{origin}' may not use this window's bridge").into_bytes()));
            return None;
        }
        if request.method() == "OPTIONS" {
            responder.respond(response(204, Some(&origin), "text/plain", Vec::new()));
            return None;
        }
        if let Some(path) = request.uri().path().strip_prefix("/api/") {
            let path = format!("/{path}");
            return self.api_request(&request, path, origin, responder);
        }
        if request.method() != "POST" {
            responder.respond(response(405, Some(&origin), "text/plain", b"POST only".to_vec()));
            return None;
        }
        let path = request.uri().path().trim_start_matches('/').to_string();
        if path == "events" {
            self.listening = true;
            if !self.queued.is_empty() {
                let batch = std::mem::take(&mut self.queued);
                responder.respond(response(200, Some(&origin), "application/x-webview4j-frames", batch));
            } else if let Some((superseded, superseded_origin)) = self.poll.replace((responder, origin)) {
                // A page polls once at a time; an older poll is from a page that reloaded.
                superseded.respond(response(204, Some(&superseded_origin), "text/plain", Vec::new()));
            }
            return None;
        }
        if let Some(page_call) = path.strip_prefix("calls/") {
            // A large answer to a call that came over IPC, fetched once.
            match page_call.parse::<u64>().ok().and_then(|n| self.large.remove(&n)) {
                Some(answer) => respond_with(responder, &origin, answer),
                None => responder.respond(response(404, Some(&origin), "text/plain", b"no such answer".to_vec())),
            }
            return None;
        }
        let Some(command) = path.strip_prefix("invoke/") else {
            responder.respond(response(404, Some(&origin), "text/plain", format!("no route '/{path}'").into_bytes()));
            return None;
        };
        let subject = format!("view.{}.invoke.{command}", self.view);
        if command.is_empty() || !frame::valid_subject(&subject) {
            responder.respond(response(400, Some(&origin), "text/plain", format!("'{command}' is not a valid command name").into_bytes()));
            return None;
        }
        let body = request.body();
        if body.len() as u64 > frame::MAX_PAYLOAD {
            responder.respond(response(413, Some(&origin), "text/plain", b"the arguments are too large".to_vec()));
            return None;
        }
        // bridge.js sends its JSON as text/plain, which keeps each call a CORS "simple" request
        // (no preflight); by the bridge's own rule that body is JSON.
        let content_type = match request.headers().get(header::CONTENT_TYPE).and_then(|t| t.to_str().ok()) {
            None => "application/json".to_string(),
            Some(t) if t.starts_with("text/plain") => "application/json".to_string(),
            Some(t) => t.to_string(),
        };
        self.next_call += 1;
        let call = self.next_call;
        let message = Message::new(subject)
            .header("Reply-To", format!("view.{}.call.{call}", self.view))
            .header("Content-Type", content_type)
            .payload(body.clone());
        self.calls.insert(call, Waiting::Fetch(responder, origin));
        Some(message)
    }

    /// A request for one of the app's files, `GET <base>app/<path>`: to Java as `view.<view>.asset`
    /// with `Root` (the assets the window was opened with) and `Path` headers; Java's answer is the
    /// response. A path that climbs out (`..`), has a backslash, a drive or a control character is
    /// refused here; an empty path is `index.html`.
    fn asset_request(
        &mut self,
        request: &Request<Vec<u8>>,
        root: String,
        path: String,
        origin: String,
        responder: RequestAsyncResponder,
    ) -> Option<Message> {
        if !matches!(request.method().as_str(), "GET" | "HEAD") {
            responder.respond(response(405, None, "text/plain", b"GET only".to_vec()));
            return None;
        }
        let path = if path.is_empty() || path.ends_with('/') { format!("{path}index.html") } else { path };
        if !safe_asset_path(&path) {
            responder.respond(response(400, None, "text/plain", b"not a file path".to_vec()));
            return None;
        }
        self.next_call += 1;
        let call = self.next_call;
        let message = Message::new(format!("view.{}.asset", self.view))
            .header("Reply-To", format!("view.{}.call.{call}", self.view))
            .header("Root", root)
            .header("Path", path);
        // Java sends the whole file; the part a player seeks to is cut from it here.
        let range = request.headers().get(header::RANGE).and_then(|r| r.to_str().ok()).map(str::to_owned);
        self.calls.insert(call, Waiting::Asset(responder, origin, range));
        Some(message)
    }

    /// A request to a controller route, `<METHOD> <base>api/<path>`: to Java as
    /// `view.<view>.request` with `Method`, `Path` and `Query` headers, the body unread; Java's
    /// answer is the response.
    fn api_request(&mut self, request: &Request<Vec<u8>>, path: String, origin: String, responder: RequestAsyncResponder) -> Option<Message> {
        let method = request.method().as_str().to_string();
        if !matches!(method.as_str(), "GET" | "POST" | "PUT" | "PATCH" | "DELETE") {
            responder.respond(response(405, Some(&origin), "text/plain", format!("{method} is not a route method").into_bytes()));
            return None;
        }
        let body = request.body();
        if body.len() as u64 > frame::MAX_PAYLOAD {
            responder.respond(response(413, Some(&origin), "text/plain", b"the body is too large".to_vec()));
            return None;
        }
        // Header values cannot hold line breaks; a URL's path and query never do.
        let query = request.uri().query().unwrap_or("").to_string();
        if path.contains(['\r', '\n']) || query.contains(['\r', '\n']) {
            responder.respond(response(400, Some(&origin), "text/plain", b"bad request line".to_vec()));
            return None;
        }
        let content_type = match request.headers().get(header::CONTENT_TYPE).and_then(|t| t.to_str().ok()) {
            None => "application/json".to_string(),
            Some(t) if t.starts_with("text/plain") => "application/json".to_string(),
            Some(t) => t.to_string(),
        };
        self.next_call += 1;
        let call = self.next_call;
        let message = Message::new(format!("view.{}.request", self.view))
            .header("Reply-To", format!("view.{}.call.{call}", self.view))
            .header("Method", method)
            .header("Path", path)
            .header("Query", query)
            .header("Content-Type", content_type)
            .payload(body.clone());
        self.calls.insert(call, Waiting::Fetch(responder, origin));
        Some(message)
    }

    /// Java's answer to call `call`. A fetch is answered here; for a call that came over IPC,
    /// returns the script that settles it in the page.
    pub fn answer(&mut self, call: u64, answer: Message) -> Option<String> {
        match self.calls.remove(&call)? {
            Waiting::Fetch(responder, origin) => {
                respond_with(responder, &origin, answer);
                None
            }
            Waiting::Asset(responder, origin, range) => {
                responder.respond(asset_response(&origin, range.as_deref(), answer));
                None
            }
            Waiting::Posted(page_call) => {
                // A number, whatever the header said: no header text can reach the script.
                let status = answer.get("Status").and_then(|s| s.parse::<u16>().ok()).unwrap_or(500);
                if answer.payload.len() > FAST_LIMIT {
                    // Data, not a UI answer: the page fetches it over the custom protocol.
                    self.large.insert(page_call, answer);
                    return Some(settle_script(page_call, status, &Value::Null, Some(&format!("calls/{page_call}"))));
                }
                Some(match status {
                    200 => match serde_json::from_slice::<Value>(&answer.payload) {
                        Ok(value) => settle_script(page_call, 200, &value, None),
                        Err(_) if answer.payload.is_empty() => settle_script(page_call, 200, &Value::Null, None),
                        Err(_) => settle_script(page_call, 500, &Value::String("the command's answer is not JSON".into()), None),
                    },
                    _ => settle_script(page_call, status, &Value::String(String::from_utf8_lossy(&answer.payload).into_owned()), None),
                })
            }
        }
    }

    /// Java's emit on `topic`. A small one becomes a script that delivers it now; a large one goes
    /// to the waiting poll, waits for the next one, or is dropped when the page does not listen
    /// (like calling no listeners). Every emit that goes out is numbered, so the page delivers them
    /// in order whichever channel each took.
    pub fn emit(&mut self, topic: &str, content_type: &str, payload: &[u8]) -> Emit {
        if payload.len() <= FAST_LIMIT {
            // Parsed and written out again, like an answer: never pasted into the script as received.
            let data = match payload {
                [] => Value::Null,
                bytes => match serde_json::from_slice::<Value>(bytes) {
                    Ok(data) => data,
                    Err(_) => return Emit::Done(Outcome::error(400, "an emit's data must be JSON")),
                },
            };
            self.next_emit += 1;
            let (seq, topic) = (self.next_emit, Value::String(topic.to_string()));
            return Emit::Script(format!("window.__webview4j && window.__webview4j.deliver({seq}, {topic}, {data});"));
        }
        if !self.listening {
            return Emit::Done(Outcome::ok());
        }
        let frame = Message::new(format!("view.{}.emit.{topic}", self.view))
            .header("Seq", (self.next_emit + 1).to_string())
            .header("Content-Type", content_type)
            .payload(payload.to_vec())
            .encode();
        if let Some((poll, origin)) = self.poll.take() {
            poll.respond(response(200, Some(&origin), "application/x-webview4j-frames", frame));
        } else if self.queued.len() + frame.len() > MAX_QUEUED {
            return Emit::Done(Outcome::error(503, "the page is not taking emits: too many are waiting"));
        } else {
            self.queued.extend_from_slice(&frame);
        }
        self.next_emit += 1;
        Emit::Done(Outcome::ok())
    }

    /// The window is closing: nothing more will come.
    pub fn close(&mut self) {
        if let Some((poll, origin)) = self.poll.take() {
            poll.respond(response(410, Some(&origin), "text/plain", b"the window closed".to_vec()));
        }
        for (_, waiting) in self.calls.drain() {
            if let Waiting::Fetch(responder, origin) | Waiting::Asset(responder, origin, _) = waiting {
                responder.respond(response(503, Some(&origin), "text/plain", b"the window closed".to_vec()));
            }
        }
        self.large.clear();
    }
}

/// The origin a request came from. Browsers leave `Origin` off a same-origin `GET`, which is what
/// a page served from the protocol itself (`app/`) sends for its data. Without an `Origin`, the
/// browser's own word decides, from headers pages cannot set: `Sec-Fetch-Site: same-origin` is
/// the protocol's own origin, and otherwise the origin of the `Referer` (a page can drop its
/// referrer, never claim another's). With neither, the empty origin, which no window allows.
pub fn request_origin(headers: &header::HeaderMap) -> String {
    let given = headers.get(header::ORIGIN).and_then(|o| o.to_str().ok()).unwrap_or("");
    if !given.is_empty() {
        return given.to_string();
    }
    let same_origin = headers.get("sec-fetch-site").and_then(|s| s.to_str().ok()) == Some("same-origin");
    if same_origin {
        return origin_of(base_url()).unwrap_or_default();
    }
    headers
        .get(header::REFERER)
        .and_then(|r| r.to_str().ok())
        .and_then(origin_of)
        .unwrap_or_default()
}

/// Whether `path` names a file inside the app's files: segments of letters, digits and `._-~@+`,
/// none empty, none starting with a dot (no `..`, no hidden files), and nothing percent-encoded, so
/// no encoding can smuggle a separator or a climb past this check.
pub fn safe_asset_path(path: &str) -> bool {
    !path.is_empty()
        && path.len() <= 512
        && path.split('/').all(|segment| {
            !segment.is_empty()
                && !segment.starts_with('.')
                && segment.chars().all(|c| c.is_ascii_alphanumeric() || "._-~@+".contains(c))
        })
}

/// Answers a fetch with Java's answer: its Status, Content-Type and bytes, unread.
fn respond_with(responder: RequestAsyncResponder, origin: &str, answer: Message) {
    let status = answer.get("Status").and_then(|s| s.parse::<u16>().ok()).unwrap_or(500);
    let content_type = answer.get("Content-Type").unwrap_or("application/json").to_string();
    // A request without an Origin (a page loading its own files) gets no CORS headers.
    let origin = Some(origin).filter(|o| !o.is_empty());
    responder.respond(response(status, origin, &content_type, answer.payload));
}

/// One of the app's files, from Java's answer (the whole file): all of it, or for a `Range` request the part asked
/// for (`206`, so a player can seek). Every file says it can be asked for in parts (`Accept-Ranges`).
fn asset_response(origin: &str, range: Option<&str>, answer: Message) -> Response<Vec<u8>> {
    let status = answer.get("Status").and_then(|s| s.parse::<u16>().ok()).unwrap_or(500);
    let content_type = answer.get("Content-Type").unwrap_or("application/octet-stream").to_string();
    let origin = Some(origin).filter(|o| !o.is_empty());
    let mut body = answer.payload;
    let length = body.len() as u64;
    let (status, content_range) = match (status, range) {
        (200, Some(range)) => match byte_range(range, length) {
            Some((start, end)) => {
                body = body[start as usize..=end as usize].to_vec();
                (206, Some(format!("bytes {start}-{end}/{length}")))
            }
            None => {
                body = Vec::new();
                (416, Some(format!("bytes */{length}")))
            }
        },
        (status, _) => (status, None),
    };
    let mut response = response(status, origin, &content_type, body);
    let headers = response.headers_mut();
    if status == 200 || status == 206 || status == 416 {
        headers.insert(header::ACCEPT_RANGES, header::HeaderValue::from_static("bytes"));
    }
    if let Some(value) = content_range.and_then(|v| header::HeaderValue::from_str(&v).ok()) {
        headers.insert(header::CONTENT_RANGE, value);
    }
    response
}

/// The first and last byte a `Range: bytes=...` header asks for, in a file of `length` bytes: one range
/// (`a-b`, `a-`, `-n`, the end clamped to the file). None when it cannot be met (416); several ranges are
/// answered with the first.
fn byte_range(header: &str, length: u64) -> Option<(u64, u64)> {
    let spec = header.trim().strip_prefix("bytes=")?.split(',').next()?.trim();
    let (from, to) = spec.split_once('-')?;
    if length == 0 {
        return None;
    }
    let (start, end) = match (from.trim(), to.trim()) {
        ("", "") => return None,
        ("", suffix) => {
            let n: u64 = suffix.parse().ok()?;
            if n == 0 {
                return None;
            }
            (length.saturating_sub(n), length - 1)
        }
        (from, "") => (from.parse().ok()?, length - 1),
        (from, to) => (from.parse().ok()?, to.parse::<u64>().ok()?.min(length - 1)),
    };
    (start <= end && start < length).then_some((start, end))
}

fn response(status: u16, origin: Option<&str>, content_type: &str, body: Vec<u8>) -> Response<Vec<u8>> {
    let mut response = Response::builder()
        .status(status)
        .header(header::CONTENT_TYPE, content_type)
        .header(header::CACHE_CONTROL, "no-store")
        .header(header::VARY, "Origin");
    if let Some(origin) = origin {
        response = response
            .header(header::ACCESS_CONTROL_ALLOW_ORIGIN, origin)
            .header(header::ACCESS_CONTROL_ALLOW_METHODS, "GET, POST, PUT, PATCH, DELETE")
            .header(header::ACCESS_CONTROL_ALLOW_HEADERS, "Content-Type");
    }
    response.body(body).expect("a valid response")
}

#[cfg(test)]
mod tests {
    use super::*;

    fn ipc_call(command: &str, page_call: u64, args: &str) -> String {
        String::from_utf8(Message::new(format!("invoke.{command}")).header("Call", page_call.to_string()).payload(args).encode()).unwrap()
    }

    #[test]
    fn a_same_origin_request_without_origin_comes_from_the_protocol_itself() {
        let mut headers = header::HeaderMap::new();
        assert_eq!(request_origin(&headers), "", "no Origin, no word from the browser: nobody");
        headers.insert("sec-fetch-site", "cross-site".parse().unwrap());
        assert_eq!(request_origin(&headers), "", "a cross-site request without an Origin is nobody");
        headers.insert(header::REFERER, "https://evil.example/page".parse().unwrap());
        assert_eq!(request_origin(&headers), "https://evil.example", "a referrer names its own origin, never ours");
        headers.insert(header::REFERER, format!("{}app/index.html", base_url()).parse().unwrap());
        assert_eq!(request_origin(&headers), origin_of(base_url()).unwrap(), "the app's own page, by its referrer");
        headers.remove(header::REFERER);
        headers.insert("sec-fetch-site", "same-origin".parse().unwrap());
        assert_eq!(request_origin(&headers), origin_of(base_url()).unwrap(), "the app's own page");
        headers.insert(header::ORIGIN, "https://app.example".parse().unwrap());
        assert_eq!(request_origin(&headers), "https://app.example", "a given Origin wins");
    }

    #[test]
    fn asset_paths_stay_inside_the_apps_files() {
        assert!(safe_asset_path("index.html"));
        assert!(safe_asset_path("assets/index-B3x_9a.js"));
        assert!(safe_asset_path("fonts/inter@4.woff2"));
        for bad in ["", "../secret", "a/../../b", "a//b", ".env", "a/.git/config", "a\\b", "C:/x", "a%2F..%2Fb", "a b", "a\u{0}b"] {
            assert!(!safe_asset_path(bad), "{bad:?} is refused");
        }
    }

    #[test]
    fn a_link_out_of_the_app_goes_to_the_browser_not_into_the_window() {
        let home = vec!["https://webview4j.localhost".to_string(), "http://localhost:8080".to_string()];
        assert_eq!(navigation("https://webview4j.localhost/ui/index.html", &home), Navigation::Stay);
        assert_eq!(navigation("http://localhost:8080/orders?page=2", &home), Navigation::Stay);
        assert_eq!(navigation("HTTP://LOCALHOST:8080/", &home), Navigation::Stay);
        assert_eq!(navigation("about:blank", &home), Navigation::Stay);
        assert_eq!(navigation("data:text/html,hi", &home), Navigation::Stay);
        assert_eq!(navigation("https://github.com/joshuasalcedo-dev/webview-4j", &home), Navigation::Browser);
        assert_eq!(navigation("http://localhost:9090/", &home), Navigation::Browser, "another port is another site");
        assert_eq!(navigation("mailto:ana@example.com", &home), Navigation::Browser);
        for refused in ["file:///C:/Windows/win.ini", "ms-settings:privacy", "javascript:alert(1)", "https://a.example/x y", "https://a\n.example"] {
            assert_eq!(navigation(refused, &home), Navigation::Refuse, "{refused:?}");
        }
    }

    #[test]
    fn a_browser_goes_to_any_website_itself() {
        let home = vec!["https://webview4j.localhost".to_string(), "*".to_string()];
        assert_eq!(navigation("https://github.com/joshuasalcedo-dev/webview-4j", &home), Navigation::Stay);
        assert_eq!(navigation("http://localhost:9090/", &home), Navigation::Stay);
        assert_eq!(navigation("mailto:ana@example.com", &home), Navigation::Browser, "mail still goes to the mail app");
        for refused in ["file:///C:/Windows/win.ini", "ms-settings:privacy", "javascript:alert(1)"] {
            assert_eq!(navigation(refused, &home), Navigation::Refuse, "{refused:?}");
        }
    }

    #[test]
    fn byte_ranges_as_a_player_asks_for_them() {
        assert_eq!(byte_range("bytes=0-99", 1000), Some((0, 99)));
        assert_eq!(byte_range("bytes=500-", 1000), Some((500, 999)));
        assert_eq!(byte_range("bytes=-100", 1000), Some((900, 999)), "the last 100");
        assert_eq!(byte_range("bytes=900-5000", 1000), Some((900, 999)), "the end is clamped to the file");
        assert_eq!(byte_range("bytes=0-0, 5-9", 1000), Some((0, 0)), "several: the first");
        assert_eq!(byte_range("bytes=-5000", 1000), Some((0, 999)));
        for bad in ["bytes=1000-", "bytes=5-2", "bytes=-0", "bytes=-", "items=0-1", "bytes=a-b", ""] {
            assert_eq!(byte_range(bad, 1000), None, "{bad:?} cannot be met");
        }
        assert_eq!(byte_range("bytes=0-", 0), None, "an empty file has no bytes to give");
    }

    #[test]
    fn a_file_is_given_whole_or_in_the_part_asked_for() {
        let file = || Message::new("c").header("Status", "200").header("Content-Type", "audio/wav").payload(b"0123456789".to_vec());
        let whole = asset_response("", None, file());
        assert_eq!(whole.status(), 200);
        assert_eq!(whole.headers()[header::ACCEPT_RANGES], "bytes");
        assert_eq!(whole.body(), b"0123456789");

        let part = asset_response("", Some("bytes=2-5"), file());
        assert_eq!(part.status(), 206);
        assert_eq!(part.headers()[header::CONTENT_RANGE], "bytes 2-5/10");
        assert_eq!(part.headers()[header::CONTENT_TYPE], "audio/wav");
        assert_eq!(part.body(), b"2345");

        let past = asset_response("", Some("bytes=20-"), file());
        assert_eq!(past.status(), 416);
        assert_eq!(past.headers()[header::CONTENT_RANGE], "bytes */10");

        let missing = Message::new("c").header("Status", "404").payload(b"no file".to_vec());
        let not_found = asset_response("", Some("bytes=0-1"), missing);
        assert_eq!(not_found.status(), 404, "an error is passed on, not cut");
        assert!(not_found.headers().get(header::ACCEPT_RANGES).is_none());
    }

    #[test]
    fn asset_requests_go_to_java_with_the_windows_root_only_when_it_serves_files() {
        let served = Link::with_assets("wv-1", vec!["https://webview4j.localhost".into()], Some("app".into()));
        let served = served.lock().unwrap();
        assert!(served.assets.is_some());
        let none = Link::new("wv-2", vec!["null".into()]);
        assert!(none.lock().unwrap().assets.is_none(), "a window opened without assets serves none");
        assert_eq!(origin_of("webview4j://localhost/app/index.html").as_deref(), Some("webview4j://localhost"));
        assert_eq!(origin_of("https://webview4j.localhost/app/").as_deref(), Some("https://webview4j.localhost"));
    }

    #[test]
    fn origins_are_scheme_and_authority() {
        assert_eq!(origin_of("https://Example.com:8443/a/b?c").as_deref(), Some("https://example.com:8443"));
        assert_eq!(origin_of("http://localhost:5173/").as_deref(), Some("http://localhost:5173"));
        assert_eq!(origin_of("about:blank"), None);
        assert_eq!(page_origin("data:text/html,hi"), "null");
    }

    #[test]
    fn the_script_knows_where_the_protocol_is() {
        let script = script();
        assert!(script.contains(base_url()));
        assert!(!script.contains("__WEBVIEW4J_"));
    }

    #[test]
    fn ipc_calls_go_to_java_tagged_with_this_view_and_answers_come_back_tagged_with_the_page_call() {
        let link = Link::new("wv-1", vec!["https://app.example".into()]);
        let mut link = link.lock().unwrap();
        let FromPage::Java(java) = link.from_ipc(&ipc_call("users.find", 7, r#"{"id":42}"#), "https://app.example/page") else {
            panic!("an allowed call goes to Java")
        };
        assert_eq!(java.subject, "view.wv-1.invoke.users.find");
        assert_eq!(java.get("Reply-To"), Some("view.wv-1.call.1"));
        assert_eq!(java.payload, br#"{"id":42}"#);

        let answer = Message::new("view.wv-1.call.1").header("Status", "200").payload(r#"{ "name" : "Ada" }"#);
        assert_eq!(
            link.answer(1, answer).unwrap(),
            r#"window.__webview4j && window.__webview4j.settle(7, 200, {"name":"Ada"}, null);"#,
            "settled by the page's own call number, the value written out again by serde"
        );
        assert!(link.answer(1, Message::new("x")).is_none(), "a call is answered once");
    }

    #[test]
    fn answers_cannot_break_out_of_the_settle_script() {
        let link = Link::new("wv-1", vec!["null".into()]);
        let mut link = link.lock().unwrap();
        for page_call in 1..=3 {
            let FromPage::Java(_) = link.from_ipc(&ipc_call("x", page_call, "null"), "about:blank") else { panic!() };
        }
        let not_json = Message::new("c").header("Status", "200").payload(r#"1); alert("pwned"); ("#);
        assert_eq!(
            link.answer(1, not_json).unwrap(),
            r#"window.__webview4j && window.__webview4j.settle(1, 500, "the command's answer is not JSON", null);"#
        );
        let error = Message::new("c").header("Status", "400").payload(r#"no"); alert("pwned"); ("#);
        assert!(link.answer(2, error).unwrap().contains(r#""no\"); alert(\"pwned\"); (""#), "an error is one JSON string");
        let bad_status = Message::new("c").header("Status", "200); alert(1); (").payload("1");
        assert!(link.answer(3, bad_status).unwrap().contains("settle(3, 500,"), "the status is a number or 500");
    }

    #[test]
    fn large_answers_are_settled_with_where_to_fetch_them() {
        let link = Link::new("wv-1", vec!["null".into()]);
        let mut link = link.lock().unwrap();
        let FromPage::Java(_) = link.from_ipc(&ipc_call("files.read", 3, "null"), "about:blank") else { panic!() };
        let big = Message::new("view.wv-1.call.1").header("Status", "200").payload(vec![b'x'; FAST_LIMIT + 1]);
        assert_eq!(
            link.answer(1, big).unwrap(),
            r#"window.__webview4j && window.__webview4j.settle(3, 200, null, "calls/3");"#
        );
        assert!(link.large.contains_key(&3));
    }

    #[test]
    fn small_emits_are_scripts_large_ones_frames_numbered_in_one_sequence() {
        let link = Link::new("wv-1", vec!["null".into()]);
        let mut link = link.lock().unwrap();
        let Emit::Script(first) = link.emit("orders.updated", "application/json", br#"{ "id" : 1 }"#) else { panic!("small") };
        assert_eq!(first, r#"window.__webview4j && window.__webview4j.deliver(1, "orders.updated", {"id":1});"#);
        let Emit::Done(not_json) = link.emit("x", "application/json", b"alert(1)") else { panic!() };
        assert_eq!(not_json.status, 400, "never pasted into a script");

        let big = vec![b'"'; 2].into_iter().chain(std::iter::repeat_n(b'x', FAST_LIMIT)).collect::<Vec<_>>();
        let Emit::Done(dropped) = link.emit("files", "application/json", &big) else { panic!() };
        assert_eq!(dropped.status, 200, "a page that never listened drops large emits");
        link.listening = true;
        let Emit::Done(queued) = link.emit("files", "application/json", &big) else { panic!() };
        assert_eq!(queued.status, 200);
        let frame = frame::decode(&link.queued).unwrap();
        assert_eq!(frame.get("Seq"), Some("2"), "a dropped emit takes no number");
        let Emit::Script(third) = link.emit("orders.updated", "application/json", b"2") else { panic!() };
        assert!(third.contains("deliver(3,"), "{third}");
    }

    #[test]
    fn ipc_calls_from_other_origins_and_bad_names_are_refused_without_reaching_java() {
        let link = Link::new("wv-1", vec!["https://app.example".into()]);
        let mut link = link.lock().unwrap();
        let FromPage::Page(refusal) = link.from_ipc(&ipc_call("users.find", 1, "null"), "https://evil.example/") else {
            panic!("refused")
        };
        assert!(refusal.contains("settle(1, 403,"), "{refusal}");
        assert!(matches!(link.from_ipc("not a frame", "https://app.example/"), FromPage::Nothing));
        assert!(link.calls.is_empty(), "nothing waits for Java");
    }
}
