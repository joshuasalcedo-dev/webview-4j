//! The page's bridge to Java: `window.__webview4j` (bridge.js). A page's `invoke` becomes the
//! message `view.<view>.invoke.<command>` for Java, which answers on `view.<view>.call.<call>`;
//! `view.<view>.emit.<topic>` reaches the page's listeners (PROTOCOL.md §2.3).
//! Unlike the inspector's reports, this channel is always on.
//!
//! Until the page speaks frames itself (step 3), answers and emits reach it as scripts. Values
//! are always parsed and written back out as JSON first, never pasted in as received.

use serde_json::Value;

pub fn script() -> &'static str {
    include_str!("bridge.js")
}

/// A page's call of a Java command.
#[derive(Debug, PartialEq)]
pub struct Invoke {
    pub call: u64,
    pub command: String,
    pub args: Value,
}

/// The page's `invoke` message, or None for any other message or a malformed one.
pub fn invoke(raw: &str) -> Option<Invoke> {
    let envelope: Value = serde_json::from_str(raw).ok()?;
    if envelope.get("event")?.as_str()? != "invoke" {
        return None;
    }
    let payload = envelope.get("payload")?.as_object()?;
    Some(Invoke {
        call: payload.get("id")?.as_u64()?,
        command: payload.get("command")?.as_str().filter(|command| !command.is_empty())?.to_string(),
        args: payload.get("args").cloned().unwrap_or(Value::Null),
    })
}

/// The page script that settles call `call`: with `value` when `ok`, else rejected with `error`.
pub fn resolve_script(call: u64, answer: Result<&Value, &str>) -> String {
    let (ok, value) = match answer {
        Ok(value) => (true, value.clone()),
        Err(error) => (false, Value::String(error.to_string())),
    };
    format!("window.__webview4j && window.__webview4j.resolve({call}, {ok}, {value});")
}

/// The page script that hands `data` to the listeners of `topic`.
pub fn emit_script(topic: &str, data: &Value) -> String {
    let topic = Value::String(topic.to_string());
    format!("window.__webview4j && window.__webview4j.emit({topic}, {data});")
}

#[cfg(test)]
mod tests {
    use super::*;
    use serde_json::json;

    #[test]
    fn invokes_carry_their_call_command_and_arguments() {
        let call = invoke(r#"{"event":"invoke","payload":{"id":7,"command":"users.find","args":{"id":42}}}"#).unwrap();
        assert_eq!(call, Invoke { call: 7, command: "users.find".into(), args: json!({"id": 42}) });
    }

    #[test]
    fn other_and_malformed_messages_are_not_invokes() {
        for raw in [
            "nope",
            r#"{"event":"click","payload":{}}"#,
            r#"{"event":"invoke","payload":{"command":"x"}}"#,
            r#"{"event":"invoke","payload":{"id":1,"command":""}}"#,
        ] {
            assert!(invoke(raw).is_none(), "{raw}");
        }
    }

    #[test]
    fn scripts_embed_values_as_json() {
        assert_eq!(
            resolve_script(3, Ok(&json!({"name": "a\"b"}))),
            r#"window.__webview4j && window.__webview4j.resolve(3, true, {"name":"a\"b"});"#
        );
        assert!(resolve_script(4, Err("no command 'x'")).ends_with(r#"resolve(4, false, "no command 'x'");"#));
        assert_eq!(
            emit_script("orders.updated", &json!([1, 2])),
            r#"window.__webview4j && window.__webview4j.emit("orders.updated", [1,2]);"#
        );
    }
}
