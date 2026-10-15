//! Inline web-inspector JS, parameterised by `WebInspectorConfg`.
//!
//! The script lives as separate `.js` files in `src/script/`, one per
//! interception concern. `render()` concatenates them in order and then
//! substitutes the `__PLACEHOLDER__` markers from the config so a single
//! piece of JS serves every platform.

use super::config::WebInspectorConfg;

/// IIFE opening + regex/event constants + session-key bootstrap.
const INTRO: &str = include_str!("script/intro.js");
/// Shared helpers: `tag`, `emit`, `resolve`, `isApiCall`, `clipBody`, `trace`.
const HELPERS: &str = include_str!("script/helpers.js");
/// `window.fetch` wrapper that traces matching API calls.
const FETCH: &str = include_str!("script/fetch.js");
/// `XMLHttpRequest` wrapper with the same tracing behavior.
const XHR: &str = include_str!("script/xhr.js");
/// `WebSocket` text-frame forwarder for matching URLs.
const WEBSOCKET: &str = include_str!("script/websocket.js");
/// Bubbling click capture with element description + selector.
const CLICK: &str = include_str!("script/click.js");
/// Final `installed` log + IIFE closing.
const OUTRO: &str = include_str!("script/outro.js");

/// Concatenation order is significant: helpers must precede the hooks
/// that call into them; intro/outro frame the IIFE.
const PARTS: &[&str] = &[INTRO, HELPERS, FETCH, XHR, WEBSOCKET, CLICK, OUTRO];

/// Bake a config into a ready-to-attach init script.
pub fn render(cfg: &WebInspectorConfg) -> String {
    apply_placeholders(&PARTS.join("\n"), cfg)
}

fn apply_placeholders(src: &str, cfg: &WebInspectorConfg) -> String {
    src.replace("__LOG_PREFIX__", cfg.log_prefix)
        .replace("__HOST_PATTERN__", cfg.host_pattern)
        .replace("__API_PATTERN__", cfg.api_pattern)
        .replace("__CAPTURE_PATTERN__", cfg.capture_pattern)
        .replace("__WS_PATTERN__", cfg.ws_pattern)
        .replace("__TRACE_EVENT__", cfg.trace_event)
        .replace("__CLICK_EVENT__", cfg.click_event)
        .replace("__CAPTURE_EVENT__", cfg.capture_event)
        .replace("__WS_EVENT__", cfg.ws_event)
}
