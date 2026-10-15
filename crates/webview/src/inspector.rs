//! The inspector inside each webview: the scripts that watch the page, and the events their
//! messages become. Each view has its own [`Capture`] switches; reports switched off are dropped
//! here, before they cross to Java.

use std::sync::atomic::{AtomicU8, Ordering};

use ::inspector::{config::WebInspectorConfg, script};
use serde_json::{Map, Value};

use crate::payload::CaptureBody;

/// Which reports a view sends, as bits; shared between the view's IPC handler and the actions.
#[derive(Debug, Default)]
pub struct Capture(AtomicU8);

impl Capture {
    pub const API: u8 = 1;
    pub const CLICKS: u8 = 1 << 1;
    pub const DOM_EVENTS: u8 = 1 << 2;
    pub const WEBSOCKET: u8 = 1 << 3;
    pub const CONSOLE: u8 = 1 << 4;

    /// The console only, unless the body says otherwise.
    pub fn new(body: Option<CaptureBody>) -> Self {
        let capture = Capture(AtomicU8::new(Self::CONSOLE));
        if let Some(body) = body {
            capture.apply(body);
        }
        capture
    }

    pub fn apply(&self, body: CaptureBody) {
        let mut bits = self.0.load(Ordering::Relaxed);
        for (setting, bit) in [
            (body.api, Self::API),
            (body.clicks, Self::CLICKS),
            (body.dom_events, Self::DOM_EVENTS),
            (body.websocket, Self::WEBSOCKET),
            (body.console, Self::CONSOLE),
        ] {
            match setting {
                Some(true) => bits |= bit,
                Some(false) => bits &= !bit,
                None => {}
            }
        }
        self.0.store(bits, Ordering::Relaxed);
    }

    fn has(&self, bit: u8) -> bool {
        self.0.load(Ordering::Relaxed) & bit != 0
    }
}

/// Every script a webview starts with: the console forwarder, the inspector's hooks
/// (fetch, XHR, WebSocket, clicks), and the DOM event reporter.
pub fn initialization_script() -> String {
    let inspector = script::render(&WebInspectorConfg {
        log_prefix: "webview4j",
        host_pattern: ".*",
        api_pattern: ".*",
        capture_pattern: "(?!)",
        trace_event: "api-call",
        click_event: "click",
        capture_event: "capture",
        ws_pattern: ".*",
        ws_event: "ws",
    });
    format!("{}\n{inspector}\n{}", include_str!("console.js"), include_str!("dom.js"))
}

/// The report a page message becomes, if it is well formed and switched on: its kind, the last
/// token of its subject `view.<view>.<kind>` (PROTOCOL.md §2.2), and its JSON payload.
///
/// ```text
/// api-call    {"method","url","status","durationMs",...}
/// click       {"selector","tag","text",...}
/// dom-event   {"kind":"hover","element":{...},...}
/// websocket   {"socket":3,"url","direction":"open|in|out|close","frame"}
/// log         {"level":"warn","message":"..."}
/// ```
pub fn event(raw: &str, capture: &Capture) -> Option<(&'static str, Value)> {
    let envelope: Value = serde_json::from_str(raw).ok()?;
    let mut payload: Map<String, Value> = envelope.get("payload")?.as_object()?.clone();
    // The page may not name the view or session it came from: the host knows.
    payload.remove("viewId");
    payload.remove("sessionKey");
    let has_str = |payload: &Map<String, Value>, key: &str| payload.get(key).and_then(Value::as_str).is_some();
    let kind = match envelope.get("event")?.as_str()? {
        "api-call" if capture.has(Capture::API) => {
            let valid = has_str(&payload, "url")
                && has_str(&payload, "method")
                && payload.get("status").and_then(Value::as_u64).is_some()
                && payload.get("durationMs").and_then(Value::as_u64).is_some();
            if !valid {
                return None;
            }
            "api-call"
        }
        "click" if capture.has(Capture::CLICKS) => "click",
        "dom-event" if capture.has(Capture::DOM_EVENTS) => {
            if !has_str(&payload, "type") || !payload.get("element").is_some_and(Value::is_object) {
                return None;
            }
            // The page's own event type ("hover", "keyDown") becomes the kind of this event.
            let dom_type = payload.remove("type")?;
            payload.insert("kind".into(), dom_type);
            "dom-event"
        }
        "ws" if capture.has(Capture::WEBSOCKET) => {
            if payload.get("socket").and_then(Value::as_u64).is_none() || !has_str(&payload, "direction") {
                return None;
            }
            "websocket"
        }
        "console" if capture.has(Capture::CONSOLE) => {
            if !has_str(&payload, "level") || !has_str(&payload, "message") {
                return None;
            }
            "log"
        }
        _ => return None,
    };
    Some((kind, Value::Object(payload)))
}

#[cfg(test)]
mod tests {
    use super::*;
    use serde_json::json;

    fn all() -> Capture {
        let capture = Capture::new(None);
        capture.apply(CaptureBody {
            api: Some(true),
            clicks: Some(true),
            dom_events: Some(true),
            websocket: Some(true),
            console: Some(true),
        });
        capture
    }

    #[test]
    fn api_calls_drop_what_the_page_claims_about_its_view() {
        let (kind, payload) = event(
            r#"{"event":"api-call","payload":{"viewId":"spoofed","sessionKey":"x","method":"GET","url":"https://example.com/api","status":200,"durationMs":5}}"#,
            &all(),
        )
        .unwrap();
        assert_eq!(kind, "api-call");
        assert_eq!(payload["status"], 200);
        assert!(payload.get("viewId").is_none() && payload.get("sessionKey").is_none());
    }

    #[test]
    fn dom_events_keep_their_type_as_kind() {
        let (kind, payload) = event(r#"{"event":"dom-event","payload":{"type":"hover","element":{"tag":"a"}}}"#, &all()).unwrap();
        assert_eq!(kind, "dom-event");
        assert_eq!(payload["kind"], "hover");
    }

    #[test]
    fn switched_off_and_malformed_reports_are_dropped() {
        let console_only = Capture::new(None);
        assert!(event(r##"{"event":"click","payload":{"selector":"#a"}}"##, &console_only).is_none());
        let log = event(r#"{"event":"console","payload":{"level":"warn","message":"hi"}}"#, &console_only).unwrap();
        assert_eq!(log, ("log", json!({"level": "warn", "message": "hi"})));
        for raw in ["invalid", r#"{"event":"api-call","payload":{}}"#, r#"{"event":"ws","payload":{"frame":"x"}}"#] {
            assert!(event(raw, &all()).is_none(), "{raw}");
        }
    }

    #[test]
    fn capture_switches_only_what_the_body_names() {
        let capture = Capture::new(Some(CaptureBody { websocket: Some(true), ..Default::default() }));
        assert!(capture.has(Capture::WEBSOCKET) && capture.has(Capture::CONSOLE));
        capture.apply(CaptureBody { console: Some(false), ..Default::default() });
        assert!(capture.has(Capture::WEBSOCKET) && !capture.has(Capture::CONSOLE));
    }
}
