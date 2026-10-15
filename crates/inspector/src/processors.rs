//! Pure, host-agnostic processors for the inspector's event channels.
//!
//! Each `handle_*` takes the raw JSON envelope a producer would emit and:
//! 1. Parses it (silently dropping malformed envelopes — the JS shim
//!    rarely produces them, and bouncing on them helps nothing).
//! 2. Logs a one-liner under the caller-supplied log target.
//! 3. Optionally forwards the raw payload to NATS via [`SchemaPublisher`].
//! 4. For `ws` / `capture`, invokes the caller's handler with the
//!    deserialized envelope.
//!
//! There is no tie to any particular event system: hosts that want to
//! consume these from Tauri, a channel, a websocket, or unit tests all
//! invoke the same functions. The host owns "how do we get the raw
//! string here"; this module owns "given the string, what do we do with
//! it".

use log::{debug, info};
use serde::Deserialize;

use super::config::{Headers, InterceptedResponse, InterceptedWsFrame};
use super::publisher::SchemaPublisher;

const CHANNEL_API: &str = "api";
const CHANNEL_CLICK: &str = "click";
const CHANNEL_WS: &str = "ws";
const CHANNEL_CAPTURE: &str = "capture";

/* -------------------------------------------------------------------------- */
/*  Trace — every API call: method/url/status/duration                        */
/* -------------------------------------------------------------------------- */

#[derive(Deserialize)]
struct ApiTrace {
    method: String,
    url: String,
    status: u16,
    #[serde(rename = "durationMs")]
    duration_ms: u64,
    #[serde(rename = "requestHeaders", default)]
    request_headers: Option<Headers>,
    #[serde(rename = "responseHeaders", default)]
    response_headers: Option<Headers>,
    #[serde(default)]
    cookies: Option<Headers>,
}

pub async fn handle_trace(
    raw: &str,
    log_target: &'static str,
    publisher: Option<&SchemaPublisher>,
) {
    log_trace(raw, log_target);
    if let Some(p) = publisher {
        p.publish(CHANNEL_API, None, raw.as_bytes()).await;
    }
}

fn log_trace(raw: &str, target: &'static str) {
    let event: ApiTrace = match serde_json::from_str(raw) {
        Ok(v) => v,
        Err(_) => return,
    };
    let level = match event.status {
        200..=299 => log::Level::Info,
        300..=399 => log::Level::Debug,
        400..=499 => log::Level::Warn,
        _ => log::Level::Error,
    };
    log::log!(
        target: target,
        level,
        "{} {} {} {}ms",
        event.method,
        event.status,
        event.url,
        event.duration_ms,
    );
    if let Some(h) = event.request_headers.as_ref() {
        log::debug!(target: target, "  req headers: {}", fmt_headers(h));
    }
    if let Some(h) = event.response_headers.as_ref() {
        log::debug!(target: target, "  res headers: {}", fmt_headers(h));
    }
    if let Some(c) = event.cookies.as_ref() {
        log::debug!(target: target, "  cookies: {}", fmt_headers(c));
    }
}

fn fmt_headers(h: &Headers) -> String {
    h.iter()
        .map(|(k, v)| format!("{k}={v}"))
        .collect::<Vec<_>>()
        .join("; ")
}

/* -------------------------------------------------------------------------- */
/*  Click — every click in the inspected webview                              */
/* -------------------------------------------------------------------------- */

#[derive(Deserialize)]
struct ClickTrace {
    selector: String,
    tag: String,
    #[serde(default)]
    id: Option<String>,
    #[serde(default)]
    role: Option<String>,
    #[serde(default, rename = "ariaLabel")]
    aria_label: Option<String>,
    #[serde(default)]
    text: Option<String>,
    #[serde(default)]
    href: Option<String>,
    #[serde(default)]
    title: Option<String>,
    #[serde(default)]
    classes: Vec<String>,
}

pub async fn handle_click(
    raw: &str,
    log_target: &'static str,
    publisher: Option<&SchemaPublisher>,
) {
    log_click(raw, log_target);
    if let Some(p) = publisher {
        p.publish(CHANNEL_CLICK, None, raw.as_bytes()).await;
    }
}

fn log_click(raw: &str, target: &'static str) {
    let click: ClickTrace = match serde_json::from_str(raw) {
        Ok(v) => v,
        Err(e) => {
            debug!(
                target: target,
                "click envelope parse failed at line {} col {}: {:?}",
                e.line(), e.column(), e.classify()
            );
            return;
        }
    };
    let label = click
        .aria_label
        .as_deref()
        .or(click.title.as_deref())
        .or(click.text.as_deref())
        .unwrap_or("");
    info!(
        target: target,
        "<{}> {} {}{}{}",
        click.tag,
        click.selector,
        if !label.is_empty() { format!("\"{label}\" ") } else { String::new() },
        click.role.as_deref().map(|r| format!("[role={r}] ")).unwrap_or_default(),
        click.href.as_deref().map(|h| format!("[href={h}]")).unwrap_or_default(),
    );
    if !click.classes.is_empty() || click.id.is_some() {
        debug!(
            target: target,
            "  id={:?} classes={:?}",
            click.id, click.classes
        );
    }
}

/* -------------------------------------------------------------------------- */
/*  WS frame                                                                  */
/* -------------------------------------------------------------------------- */

/// Parse a WS-frame envelope, optionally forward the inner frame body to
/// NATS, and invoke `on_frame` with the deserialized envelope. Bad
/// envelopes are silently dropped — `on_frame` is not called in that case.
pub async fn handle_ws<F>(raw: &str, publisher: Option<&SchemaPublisher>, on_frame: F)
where
    F: FnOnce(&InterceptedWsFrame),
{
    let frame: InterceptedWsFrame = match serde_json::from_str(raw) {
        Ok(v) => v,
        Err(_) => return,
    };
    if let Some(p) = publisher {
        // Publish the inner frame, not the {url, frame} envelope —
        // schema discovery cares about the actual WS payload shape.
        p.publish(CHANNEL_WS, None, frame.frame.as_bytes()).await;
    }
    on_frame(&frame);
}

/* -------------------------------------------------------------------------- */
/*  Capture                                                                   */
/* -------------------------------------------------------------------------- */

/// Parse a capture envelope, optionally forward the response body to NATS,
/// and invoke `on_capture` with the deserialized envelope. Bad envelopes
/// are silently dropped.
pub async fn handle_capture<F>(raw: &str, publisher: Option<&SchemaPublisher>, on_capture: F)
where
    F: FnOnce(&InterceptedResponse),
{
    let intercepted: InterceptedResponse = match serde_json::from_str(raw) {
        Ok(v) => v,
        Err(_) => return,
    };
    if let Some(p) = publisher {
        p.publish(CHANNEL_CAPTURE, None, intercepted.body.as_bytes())
            .await;
    }
    on_capture(&intercepted);
}
