//! Error type for the web-inspector crate.
//!
//! Listener handlers return `AppResult<()>`. Callers — typically a host
//! that wires the inspector events into their event system — are
//! responsible for logging or surfacing failures.

use thiserror::Error;

#[derive(Debug, Error)]
pub enum InspectorError {
    /// An event payload could not be parsed as the expected envelope.
    #[error("payload parse failed: {0}")]
    Payload(#[from] serde_json::Error),

    /// Escape hatch for arbitrary handler-side failures.
    #[error("{0}")]
    Handler(String),
}

impl InspectorError {
    pub fn handler(msg: impl Into<String>) -> Self {
        Self::Handler(msg.into())
    }
}

pub type AppResult<T> = Result<T, InspectorError>;
