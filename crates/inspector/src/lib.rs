//! Web inspector — a Tauri webview API listener, harvested from the legacy
//! monorepo (`src-tauri/src/common/web_inspector`).
//!
//! It has two halves, and is deliberately **host-agnostic** (no `tauri`
//! dependency — the consuming app owns the glue):
//!
//! 1. [`script::render`] bakes a config into an init script that hooks
//!    `fetch` / `XMLHttpRequest` / `WebSocket` / clicks inside a webview and
//!    emits JSON envelopes as events. The host attaches it as a webview
//!    initialization script.
//! 2. [`processors`] turns those raw JSON envelopes into logs and, optionally,
//!    NATS publishes via [`SchemaPublisher`]. The host listens for the events
//!    (e.g. via Tauri's event system) and feeds the raw strings to
//!    `handle_trace` / `handle_click` / `handle_ws` / `handle_capture`.
//!
//! Captured traffic is published under
//! `schema.raw.{platform}.inspector.{channel}` for downstream schema
//! discovery.

pub mod config;
pub mod error;
pub mod processors;
pub mod publisher;
pub mod script;

pub use error::{AppResult, InspectorError};
pub use publisher::SchemaPublisher;
