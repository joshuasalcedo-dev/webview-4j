//! Payloads (PROTOCOL.md §5) and replies (§4). JSON payloads reject unknown keys, so a typo
//! is a `400`, not a setting quietly ignored.

use serde::{Deserialize, Serialize};
use serde_json::Value;

use crate::frame::Message;

/// Payload of `app.open`. `url` wins over `html`; with neither the webview opens blank.
#[derive(Debug, Clone, Deserialize)]
#[serde(rename_all = "camelCase", deny_unknown_fields)]
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
    /// The origins whose pages may use the bridge (`invoke`, `listen`), e.g. `https://app.example`.
    /// Unless given: the origin of `url`, or `null` (an opaque origin) for `html`.
    #[serde(default)]
    pub origins: Option<Vec<String>>,
    /// The app's own files, by the name Java gave them (a class path directory): `GET <base>app/<path>`
    /// is asked of Java as `view.<view>.asset`. None serves no files.
    #[serde(default)]
    pub assets: Option<String>,
    /// The window's icon, a PNG in base64: in its title bar, the taskbar and the window switcher.
    /// None leaves the program's own.
    #[serde(default)]
    pub icon: Option<String>,
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

/// Payload of `view.<view>.capture`: each kind of report on or off; kinds left out keep their setting.
#[derive(Debug, Clone, Copy, Default, Deserialize)]
#[serde(rename_all = "camelCase", deny_unknown_fields)]
pub struct CaptureBody {
    pub api: Option<bool>,
    pub clicks: Option<bool>,
    pub dom_events: Option<bool>,
    pub websocket: Option<bool>,
    pub console: Option<bool>,
}

/// One open webview, as `app.list` reports it.
#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct WebViewInfo {
    pub view: String,
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
#[serde(deny_unknown_fields)]
pub struct SizeBody {
    pub width: f64,
    pub height: f64,
}

#[derive(Deserialize)]
#[serde(deny_unknown_fields)]
pub struct PositionBody {
    pub x: f64,
    pub y: f64,
}

/// Position and size in one step, in physical screen pixels.
#[derive(Deserialize)]
#[serde(deny_unknown_fields)]
pub struct BoundsBody {
    pub x: i32,
    pub y: i32,
    pub width: u32,
    pub height: u32,
}

/// How an action went: a `Status` and a payload (PROTOCOL.md §4).
#[derive(Debug, Clone, PartialEq)]
pub struct Outcome {
    pub status: u16,
    pub content_type: &'static str,
    pub body: Vec<u8>,
}

impl Outcome {
    pub fn ok() -> Self {
        Outcome { status: 200, content_type: "application/json", body: Vec::new() }
    }

    pub fn json(value: &impl Serialize) -> Self {
        Outcome { status: 200, content_type: "application/json", body: serde_json::to_vec(value).expect("serializable") }
    }

    /// An error: `status` with a message saying what went wrong.
    pub fn error(status: u16, message: impl Into<String>) -> Self {
        Outcome { status, content_type: "text/plain", body: message.into().into_bytes() }
    }

    /// The reply to send to `reply_to`.
    pub fn to(self, reply_to: &str) -> Message {
        Message::new(reply_to)
            .header("Status", self.status.to_string())
            .header("Content-Type", self.content_type)
            .payload(self.body)
    }
}

/// A JSON payload; empty reads as `{}`.
pub fn json<T: serde::de::DeserializeOwned>(payload: &[u8]) -> Result<T, Outcome> {
    let payload = if payload.iter().all(u8::is_ascii_whitespace) { b"{}".as_slice() } else { payload };
    serde_json::from_slice(payload).map_err(|e| Outcome::error(400, format!("invalid payload: {e}")))
}

/// Any JSON value; empty reads as null.
pub fn value(payload: &[u8]) -> Result<Value, Outcome> {
    if payload.iter().all(u8::is_ascii_whitespace) {
        return Ok(Value::Null);
    }
    serde_json::from_slice(payload).map_err(|e| Outcome::error(400, format!("the payload is not JSON: {e}")))
}

/// A text payload.
pub fn text(payload: &[u8]) -> Result<&str, Outcome> {
    std::str::from_utf8(payload).map_err(|_| Outcome::error(400, "the payload must be UTF-8 text"))
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn replies_carry_status_and_content_type() {
        let reply = Outcome::error(404, "no view 'wv-9'").to("_inbox.3");
        assert_eq!(reply.subject, "_inbox.3");
        assert_eq!(reply.get("status"), Some("404"));
        assert_eq!(reply.get("content-type"), Some("text/plain"));
        assert_eq!(reply.payload, b"no view 'wv-9'");
    }

    #[test]
    fn payloads_refuse_unknown_keys_and_bad_json() {
        assert_eq!(json::<SizeBody>(br#"{"width":1,"height":2,"depth":3}"#).err().unwrap().status, 400);
        assert_eq!(value(b"{oops").err().unwrap().status, 400);
        assert_eq!(value(b" ").unwrap(), Value::Null);
        assert!(json::<CaptureBody>(b"").is_ok());
    }
}
