//! NATS publisher for schema discovery.
//!
//! Subject layout: `schema.raw.{platform}.{source}.{channel}[.{eventType}]`.
//!
//! - `platform` — top-level domain (`onlyfans`, `fansly`, …); set once.
//! - `source`   — who's observing; this crate always emits with `inspector`
//!                so it never collides with the Java backend's `userdata`.
//! - `channel`  — `api`, `ws`, `capture`, `click` (caller picks).
//! - `eventType` — optional further discriminator (e.g. `chat.RECEIVE`).
//!
//! The parser in `message-broker/broker/core/schema` buckets by subject, so
//! every new bucket appears as a new `inferred/<path>.json` automatically.

use std::sync::Arc;

use async_nats::Client;
use log::warn;

const SUBJECT_ROOT: &str = "schema.raw";
const SOURCE: &str = "inspector";

#[derive(Clone)]
pub struct SchemaPublisher {
    inner: Arc<Inner>,
}

struct Inner {
    client: Client,
    /// Pre-computed `schema.raw.{platform}.inspector` prefix.
    prefix: String,
    log_target: &'static str,
}

impl SchemaPublisher {
    /// `platform` becomes the top-level domain segment. `log_target` is
    /// reused for any publish-failure warnings so they land in the same
    /// log stream as the rest of the inspector's output.
    pub fn new(client: Client, platform: &str, log_target: &'static str) -> Self {
        let prefix = format!("{SUBJECT_ROOT}.{platform}.{SOURCE}");
        Self {
            inner: Arc::new(Inner {
                client,
                prefix,
                log_target,
            }),
        }
    }

    /// Publish a raw payload under `{prefix}.{channel}` (or
    /// `{prefix}.{channel}.{event_type}` if supplied). Awaits the NATS send;
    /// failures log a warning and are otherwise swallowed — schema discovery
    /// must never block the inspector's main flow.
    pub async fn publish(&self, channel: &str, event_type: Option<&str>, payload: &[u8]) {
        let subject = match event_type {
            Some(et) if !et.is_empty() => format!("{}.{}.{}", self.inner.prefix, channel, et),
            _ => format!("{}.{}", self.inner.prefix, channel),
        };
        if let Err(e) = self
            .inner
            .client
            .publish(subject.clone(), payload.to_vec().into())
            .await
        {
            warn!(
                target: self.inner.log_target,
                "schema publish {subject} failed: {e}"
            );
        }
    }
}
