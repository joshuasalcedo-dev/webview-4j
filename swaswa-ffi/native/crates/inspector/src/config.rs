use std::collections::BTreeMap;

use serde::Deserialize;

pub struct WebInspectorConfg {
    pub log_prefix: &'static str,
    pub host_pattern: &'static str,
    pub api_pattern: &'static str,
    pub capture_pattern: &'static str,
    pub trace_event: &'static str,
    pub click_event: &'static str,
    pub capture_event: &'static str,
    pub ws_pattern: &'static str,
    pub ws_event: &'static str,
}

/// Normalized header bag. Keys are lowercased by the JS shim before send;
/// values are clipped (per-value + total) to keep payloads small. A
/// `__truncated__` sentinel key may be present when the cap was hit.
pub type Headers = BTreeMap<String, String>;

#[derive(Debug, Clone, Deserialize)]
pub struct InterceptedWsFrame {
    pub url: String,
    pub frame: String,
}

#[derive(Debug, Clone, Deserialize)]
pub struct InterceptedResponse {
    pub url: String,
    pub status: u16,
    pub body: String,

    /// Request headers captured at the call site. `None` when the JS
    /// shim couldn't extract any (e.g. fetch called with no headers).
    #[serde(rename = "requestHeaders", default)]
    pub request_headers: Option<Headers>,

    /// Response headers as the browser saw them.
    #[serde(rename = "responseHeaders", default)]
    pub response_headers: Option<Headers>,

    /// Snapshot of `document.cookie` at request time — i.e. every
    /// non-`HttpOnly` cookie the page can see for the current origin.
    /// HttpOnly / third-party cookies that the browser appends silently
    /// are not visible from JS, so they won't show up here.
    #[serde(default)]
    pub cookies: Option<Headers>,

    /// `navigator.userAgent` snapshot. Captured separately because
    /// `User-Agent` is a "forbidden header name" in the fetch spec — it
    /// can't be read from `init.headers` / `Request.headers` even when
    /// the browser is about to send it.
    #[serde(rename = "userAgent", default)]
    pub user_agent: Option<String>,

    /// Opaque per-window identifier the host (Rust) baked into the
    /// page via `window.__INSPECTOR_SESSION_KEY__`. Lets the listener
    /// attribute captures back to the originating webview without
    /// relying on Tauri exposing source-window info on global events.
    /// Platform decides what string lives here (e.g. OF uses the
    /// account username; UUIDs for pre-login windows).
    #[serde(rename = "sessionKey", default)]
    pub session_key: Option<String>,
}
