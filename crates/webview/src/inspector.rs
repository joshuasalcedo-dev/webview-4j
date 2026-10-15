use std::sync::Mutex;

use ::inspector::{config::WebInspectorConfg, script};

use crate::payload::ApiCallRecord;

pub fn initialization_script() -> String {
    let api_script = script::render(&WebInspectorConfg {
        log_prefix: "swaswa-webview",
        host_pattern: ".*",
        api_pattern: ".*",
        capture_pattern: "(?!)",
        trace_event: "api-call",
        click_event: "click",
        capture_event: "capture",
        ws_pattern: "(?!)",
        ws_event: "ws",
    });
    format!("{api_script}\n{}", include_str!("dom.js"))
}

pub fn capture_dom(raw: &str, view_id: &str, events: &Mutex<Vec<ApiCallRecord>>) {
    let Ok(envelope) = serde_json::from_str::<serde_json::Value>(raw) else {
        return;
    };
    if envelope.get("event").and_then(|v| v.as_str()) != Some("dom-event") {
        return;
    }
    let Some(payload) = envelope.get("payload").and_then(|v| v.as_object()) else {
        return;
    };
    if payload.get("type").and_then(|v| v.as_str()).is_none()
        || payload.get("element").and_then(|v| v.as_object()).is_none()
    {
        return;
    }
    let mut payload = payload.clone();
    payload.remove("viewId");
    events.lock().unwrap().push(ApiCallRecord {
        view_id: view_id.to_string(),
        payload,
    });
}

pub fn capture(raw: &str, view_id: &str, calls: &Mutex<Vec<ApiCallRecord>>) {
    let Ok(envelope) = serde_json::from_str::<serde_json::Value>(raw) else {
        return;
    };
    if envelope.get("event").and_then(|v| v.as_str()) != Some("api-call") {
        return;
    }
    let Some(payload) = envelope.get("payload").and_then(|v| v.as_object()) else {
        return;
    };
    if payload.get("url").and_then(|v| v.as_str()).is_none()
        || payload.get("method").and_then(|v| v.as_str()).is_none()
        || payload.get("status").and_then(|v| v.as_u64()).is_none()
        || payload.get("durationMs").and_then(|v| v.as_u64()).is_none()
    {
        return;
    }
    let mut payload = payload.clone();
    payload.remove("viewId");
    calls.lock().unwrap().push(ApiCallRecord {
        view_id: view_id.to_string(),
        payload,
    });
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn accepts_api_calls_and_preserves_host_view_identity() {
        let calls = Mutex::new(Vec::new());
        capture(
            r#"{"event":"api-call","payload":{"viewId":"spoofed","method":"GET","url":"https://example.com/api","status":200,"durationMs":5}}"#,
            "wv-1",
            &calls,
        );
        let calls = calls.lock().unwrap();
        assert_eq!(calls.len(), 1);
        let serialized = serde_json::to_value(&calls[0]).unwrap();
        assert_eq!(serialized["viewId"], "wv-1");
        assert_eq!(serialized["status"], 200);
    }

    #[test]
    fn ignores_malformed_and_unrelated_events() {
        let calls = Mutex::new(Vec::new());
        for raw in [
            "invalid",
            r#"{"event":"click","payload":{}}"#,
            r#"{"event":"api-call","payload":{}}"#,
        ] {
            capture(raw, "wv-1", &calls);
        }
        assert!(calls.lock().unwrap().is_empty());
    }
}
