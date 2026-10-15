//! The page's bridge to Java, over the `webview4j` custom protocol: no port, nothing outside the
//! webview can reach it, and nothing the page or Java sends is ever turned into a script.
//!
//! ```text
//! POST <base>invoke/<command>   the body (the page's arguments) goes to Java unread, as
//!                               view.<view>.invoke.<command> with Reply-To view.<view>.call.<n>;
//!                               Java's answer is the response: its Status, its bytes
//! POST <base>events             held open until Java emits; answered with the waiting
//!                               view.<view>.emit.<topic> messages, as frames (PROTOCOL.md §1)
//! ```
//!
//! Every request must come from an allowed origin (`403` otherwise). The page script that uses
//! this is bridge.js, `window.__webview4j`, wrapped by @webview4j/api.

use std::collections::HashMap;
use std::sync::{Arc, Mutex};

use wry::RequestAsyncResponder;
use wry::http::{Request, Response, header};

use crate::frame::{self, Message};
use crate::payload::Outcome;

/// The custom protocol's name.
pub const SCHEME: &str = "webview4j";

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
    include_str!("bridge.js").replace("__WEBVIEW4J_BASE__", base_url())
}

/// The origin of `url`, as a browser writes it in an `Origin` header; None for URLs without one.
pub fn origin_of(url: &str) -> Option<String> {
    let uri: wry::http::Uri = url.parse().ok()?;
    let scheme = uri.scheme_str()?;
    if !matches!(scheme, "http" | "https") {
        return None;
    }
    Some(format!("{scheme}://{}", uri.authority()?.as_str().to_ascii_lowercase()))
}

/// One view's side of the bridge: its open calls, its listener, and who may use it.
pub struct Link {
    view: String,
    origins: Vec<String>,
    next_call: u64,
    calls: HashMap<u64, (RequestAsyncResponder, String)>,
    poll: Option<(RequestAsyncResponder, String)>,
    queued: Vec<u8>,
    listening: bool,
}

impl Link {
    pub fn new(view: &str, origins: Vec<String>) -> Arc<Mutex<Link>> {
        Arc::new(Mutex::new(Link {
            view: view.to_string(),
            origins,
            next_call: 0,
            calls: HashMap::new(),
            poll: None,
            queued: Vec::new(),
            listening: false,
        }))
    }

    /// A request from the page. Returns the message to send Java, if it is a call.
    pub fn request(&mut self, request: Request<Vec<u8>>, responder: RequestAsyncResponder) -> Option<Message> {
        let origin = request.headers().get(header::ORIGIN).and_then(|o| o.to_str().ok()).unwrap_or("").to_string();
        if !self.origins.iter().any(|allowed| allowed == &origin) {
            responder.respond(response(403, None, "text/plain", format!("origin '{origin}' may not use this window's bridge").into_bytes()));
            return None;
        }
        if request.method() == "OPTIONS" {
            responder.respond(response(204, Some(&origin), "text/plain", Vec::new()));
            return None;
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
        self.calls.insert(call, (responder, origin));
        Some(message)
    }

    /// Java's answer to call `call`: becomes the page's response, status and bytes unchanged.
    pub fn answer(&mut self, call: u64, answer: Message) {
        let Some((responder, origin)) = self.calls.remove(&call) else { return };
        let status = answer.get("Status").and_then(|s| s.parse::<u16>().ok()).unwrap_or(500);
        let content_type = answer.get("Content-Type").unwrap_or("application/json").to_string();
        responder.respond(response(status, Some(&origin), &content_type, answer.payload));
    }

    /// Java's emit, already a frame: to the waiting poll, held for the next one, or dropped when
    /// the page does not listen at all (like calling no listeners).
    pub fn emit(&mut self, frame: Vec<u8>) -> Outcome {
        if !self.listening {
            return Outcome::ok();
        }
        if let Some((poll, origin)) = self.poll.take() {
            poll.respond(response(200, Some(&origin), "application/x-webview4j-frames", frame));
            return Outcome::ok();
        }
        if self.queued.len() + frame.len() > MAX_QUEUED {
            return Outcome::error(503, "the page is not taking emits: too many are waiting");
        }
        self.queued.extend_from_slice(&frame);
        Outcome::ok()
    }

    /// The window is closing: nothing more will come.
    pub fn close(&mut self) {
        if let Some((poll, origin)) = self.poll.take() {
            poll.respond(response(410, Some(&origin), "text/plain", b"the window closed".to_vec()));
        }
        for (_, (responder, origin)) in self.calls.drain() {
            responder.respond(response(503, Some(&origin), "text/plain", b"the window closed".to_vec()));
        }
    }
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
            .header(header::ACCESS_CONTROL_ALLOW_METHODS, "POST")
            .header(header::ACCESS_CONTROL_ALLOW_HEADERS, "Content-Type");
    }
    response.body(body).expect("a valid response")
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn origins_are_scheme_and_authority() {
        assert_eq!(origin_of("https://Example.com:8443/a/b?c").as_deref(), Some("https://example.com:8443"));
        assert_eq!(origin_of("http://localhost:5173/").as_deref(), Some("http://localhost:5173"));
        assert_eq!(origin_of("about:blank"), None);
        assert_eq!(origin_of("file:///C:/app/index.html"), None);
    }

    #[test]
    fn the_script_knows_where_the_protocol_is() {
        assert!(script().contains(base_url()));
        assert!(!script().contains("__WEBVIEW4J_BASE__"));
    }
}
