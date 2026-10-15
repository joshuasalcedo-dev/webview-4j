//! The page's bridge to Java: `window.__webview4j` (bridge.js). A page's `invoke` becomes an
//! `invoke` event for Java; Java answers with `webview resolve`, and pushes with `webview emit`.
//! Unlike the inspector's reports, this channel is always on.

use serde_json::{Value, json};

use crate::payload::{EmitBody, ResolveBody};

pub fn script() -> &'static str {
    include_str!("bridge.js")
}

/// The event a page's `invoke` becomes:
/// `{"type":"invoke","view":"wv-1","call":7,"command":"users.find","args":{...}}`.
/// None for any other message, or a malformed one.
pub fn event(raw: &str, view: &str) -> Option<Value> {
    let envelope: Value = serde_json::from_str(raw).ok()?;
    if envelope.get("event")?.as_str()? != "invoke" {
        return None;
    }
    let payload = envelope.get("payload")?.as_object()?;
    let call = payload.get("id")?.as_u64()?;
    let command = payload.get("command")?.as_str().filter(|command| !command.is_empty())?;
    let args = payload.get("args").cloned().unwrap_or(Value::Null);
    Some(json!({"type": "invoke", "view": view, "call": call, "command": command, "args": args}))
}

/// The page script that settles call `body.call`: with the value, or rejected with the error.
pub fn resolve_script(body: &ResolveBody) -> String {
    let value = if body.ok {
        body.value.clone()
    } else {
        Value::String(body.error.clone().unwrap_or_else(|| "the command failed".into()))
    };
    format!("window.__webview4j && window.__webview4j.resolve({}, {}, {value});", body.call, body.ok)
}

/// The page script that hands `body.data` to the listeners of `body.topic`.
pub fn emit_script(body: &EmitBody) -> String {
    let topic = Value::String(body.topic.clone());
    format!("window.__webview4j && window.__webview4j.emit({topic}, {});", body.data)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn invokes_carry_the_host_view_and_their_arguments() {
        let event = event(r#"{"event":"invoke","payload":{"id":7,"command":"users.find","args":{"id":42}}}"#, "wv-1").unwrap();
        assert_eq!(event, json!({"type": "invoke", "view": "wv-1", "call": 7, "command": "users.find", "args": {"id": 42}}));
    }

    #[test]
    fn other_and_malformed_messages_are_not_invokes() {
        for raw in [
            "nope",
            r#"{"event":"click","payload":{}}"#,
            r#"{"event":"invoke","payload":{"command":"x"}}"#,
            r#"{"event":"invoke","payload":{"id":1,"command":""}}"#,
        ] {
            assert!(event(raw, "wv-1").is_none(), "{raw}");
        }
    }

    #[test]
    fn scripts_embed_values_as_json() {
        let ok = ResolveBody { call: 3, ok: true, value: json!({"name": "a\"b"}), error: None };
        assert_eq!(resolve_script(&ok), r#"window.__webview4j && window.__webview4j.resolve(3, true, {"name":"a\"b"});"#);
        let failed = ResolveBody { call: 4, ok: false, value: Value::Null, error: Some("no command 'x'".into()) };
        assert!(resolve_script(&failed).ends_with(r#"resolve(4, false, "no command 'x'");"#));
        let emit = EmitBody { topic: "orders.updated".into(), data: json!([1, 2]) };
        assert_eq!(emit_script(&emit), r#"window.__webview4j && window.__webview4j.emit("orders.updated", [1,2]);"#);
    }
}
