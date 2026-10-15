//! What crosses the FFM boundary: action payloads in (JSON), replies and events out (JSON).
//!
//! Replies are `{"ok":true,...}` or `{"ok":false,"error":"..."}`; events are
//! `{"type":"ready"}` and `{"type":"closed","id":"wv-1"}`.

use serde::{Deserialize, Serialize};

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

/// Something the inspector captured: an API call or a DOM event, from the view it happened in.
#[derive(Debug, Serialize)]
pub struct ApiCallRecord {
    #[serde(rename = "viewId")]
    pub view_id: String,
    #[serde(flatten)]
    pub payload: serde_json::Map<String, serde_json::Value>,
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

/// The answer to an action.
pub enum Reply {
    Opened { id: String },
    Ok,
    /// The JS result of an eval, already serialized as JSON by the webview.
    EvalResult(String),
    WebViews(Vec<WebViewInfo>),
    Cookies(Vec<CookieRecord>),
    ApiCalls(Vec<ApiCallRecord>),
    DomEvents(Vec<ApiCallRecord>),
    Error(String),
}

impl Reply {
    pub fn to_json(&self) -> String {
        match self {
            Reply::Opened { id } => serde_json::json!({"ok": true, "id": id}).to_string(),
            Reply::Ok => r#"{"ok":true}"#.to_string(),
            Reply::EvalResult(value) => {
                let result = if value.trim().is_empty() { "null" } else { value };
                format!(r#"{{"ok":true,"result":{result}}}"#)
            }
            Reply::WebViews(list) => serde_json::json!({"ok": true, "webviews": list}).to_string(),
            Reply::Cookies(cookies) => serde_json::json!({"ok": true, "cookies": cookies}).to_string(),
            Reply::ApiCalls(calls) => serde_json::json!({"ok": true, "calls": calls}).to_string(),
            Reply::DomEvents(events) => serde_json::json!({"ok": true, "events": events}).to_string(),
            Reply::Error(message) => serde_json::json!({"ok": false, "error": message}).to_string(),
        }
    }
}

/// Parses an action's payload; an empty payload reads as `{}`.
pub fn parse<T: serde::de::DeserializeOwned>(payload: &str) -> Result<T, Reply> {
    let payload = if payload.trim().is_empty() { "{}" } else { payload };
    serde_json::from_str(payload).map_err(|e| Reply::Error(format!("invalid payload: {e}")))
}
