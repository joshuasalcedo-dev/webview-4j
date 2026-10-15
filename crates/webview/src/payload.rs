//! What crosses the FFM boundary: action payloads in (JSON), replies and events out (JSON).
//!
//! A reply is `{"ok":true,...}` or `{"ok":false,"error":"..."}`. Submitted actions get theirs as
//! an event, `{"type":"reply","request":42,"ok":true,...}`; the other events are listed in
//! `runtime.rs`.

use serde::{Deserialize, Serialize};
use serde_json::{Value, json};

/// Payload of `window open`. `url` wins over `html`; with neither the webview opens blank.
#[derive(Debug, Clone, Deserialize)]
#[serde(rename_all = "camelCase")]
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
    #[serde(default)]
    pub initialization_script: Option<String>,
    #[serde(default)]
    pub data_directory: Option<std::path::PathBuf>,
    /// Native handle (HWND) of the window that owns this one. An owned window stays above its
    /// owner, hides with it and has no taskbar entry, so a host can dock it over its own UI.
    #[serde(default)]
    pub owner_window: Option<isize>,
    /// `false` removes the title bar and borders.
    #[serde(default = "default_true")]
    pub decorations: bool,
    /// What the inspector reports from the start; only the console unless given.
    #[serde(default)]
    pub capture: Option<CaptureBody>,
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

/// Payload of `inspector capture`: each kind of report on or off; kinds left out keep their setting.
#[derive(Debug, Clone, Copy, Default, Deserialize)]
#[serde(rename_all = "camelCase", deny_unknown_fields)]
pub struct CaptureBody {
    pub api: Option<bool>,
    pub clicks: Option<bool>,
    pub dom_events: Option<bool>,
    pub websocket: Option<bool>,
    pub console: Option<bool>,
}

/// One open webview, as `window list` reports it.
#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct WebViewInfo {
    pub id: String,
    pub title: String,
    pub url: String,
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

#[derive(Deserialize)]
pub struct UrlBody {
    pub url: String,
}

#[derive(Deserialize)]
pub struct HtmlBody {
    pub html: String,
}

#[derive(Deserialize)]
pub struct ScriptBody {
    pub script: String,
}

#[derive(Deserialize)]
pub struct TitleBody {
    pub title: String,
}

#[derive(Deserialize)]
pub struct SizeBody {
    pub width: f64,
    pub height: f64,
}

#[derive(Deserialize)]
pub struct PositionBody {
    pub x: f64,
    pub y: f64,
}

/// Position and size in one step, in physical screen pixels.
#[derive(Deserialize)]
pub struct BoundsBody {
    pub x: i32,
    pub y: i32,
    pub width: u32,
    pub height: u32,
}

/// Payload of `webview websocket-send`: a text frame for the page's socket number `socket`.
#[derive(Deserialize)]
pub struct SocketBody {
    pub socket: u64,
    pub data: String,
}

/// The answer to an action.
pub enum Reply {
    Opened { id: String },
    Ok,
    /// The JS result of an eval, already serialized as JSON by the webview.
    EvalResult(String),
    WebViews(Vec<WebViewInfo>),
    Cookies(Vec<CookieRecord>),
    Error(String),
}

impl Reply {
    pub fn to_value(&self) -> Value {
        match self {
            Reply::Opened { id } => json!({"ok": true, "id": id}),
            Reply::Ok => json!({"ok": true}),
            Reply::EvalResult(value) => {
                let result = serde_json::from_str(value).unwrap_or(Value::Null);
                json!({"ok": true, "result": result})
            }
            Reply::WebViews(list) => json!({"ok": true, "webviews": list}),
            Reply::Cookies(cookies) => json!({"ok": true, "cookies": cookies}),
            Reply::Error(message) => json!({"ok": false, "error": message}),
        }
    }

    pub fn to_json(&self) -> String {
        self.to_value().to_string()
    }

    /// This reply as the event that answers submitted request `request`.
    pub fn to_event(&self, request: u64) -> Value {
        let mut value = self.to_value();
        value["type"] = json!("reply");
        value["request"] = json!(request);
        value
    }
}

/// Parses an action's payload; an empty payload reads as `{}`.
pub fn parse<T: serde::de::DeserializeOwned>(payload: &str) -> Result<T, Reply> {
    let payload = if payload.trim().is_empty() { "{}" } else { payload };
    serde_json::from_str(payload).map_err(|e| Reply::Error(format!("invalid payload: {e}")))
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn replies_become_events_tagged_with_their_request() {
        let event = Reply::EvalResult("2".into()).to_event(42);
        assert_eq!(event, json!({"type": "reply", "request": 42, "ok": true, "result": 2}));
        assert_eq!(Reply::EvalResult(String::new()).to_value(), json!({"ok": true, "result": null}));
        assert_eq!(Reply::Error("no".into()).to_json(), r#"{"error":"no","ok":false}"#);
    }
}
