//! What the library can do, in three groups: the window, the webview inside it, and the inspector
//! that watches the page. Each action has a stable numeric code, and the Java enums of the same
//! names carry the same codes, so FFM passes an action as that number.
//!
//! Codes never change once released. A new action takes the next free code in its group:
//! window 1xx, webview 2xx, inspector 3xx.

macro_rules! actions {
    ($(#[$doc:meta])* $name:ident, $group:literal { $($variant:ident = $code:literal, $wire:literal;)* }) => {
        $(#[$doc])*
        #[repr(u32)]
        #[derive(Debug, Clone, Copy, PartialEq, Eq, Hash)]
        pub enum $name {
            $($variant = $code,)*
        }

        impl $name {
            pub const GROUP: &'static str = $group;
            pub const ALL: &'static [$name] = &[$($name::$variant,)*];

            pub fn code(self) -> u32 {
                self as u32
            }

            /// The action as written outside Rust, e.g. `set-title`.
            pub fn name(self) -> &'static str {
                match self {
                    $($name::$variant => $wire,)*
                }
            }

            pub fn from_code(code: u32) -> Option<Self> {
                match code {
                    $($code => Some($name::$variant),)*
                    _ => None,
                }
            }
        }
    };
}

actions! {
    /// The native window: its life, place and visibility.
    WindowAction, "window" {
        Open = 100, "open";
        List = 101, "list";
        SetTitle = 102, "set-title";
        SetSize = 103, "set-size";
        SetPosition = 104, "set-position";
        SetBounds = 105, "set-bounds";
        Show = 106, "show";
        Hide = 107, "hide";
        Focus = 108, "focus";
        Close = 109, "close";
    }
}

actions! {
    /// The page inside a window: what it shows and what runs in it.
    WebViewAction, "webview" {
        Navigate = 200, "navigate";
        LoadHtml = 201, "load-html";
        Eval = 202, "eval";
        Cookies = 203, "cookies";
        WebSocketSend = 204, "websocket-send";
        Resolve = 205, "resolve";
        Emit = 206, "emit";
    }
}

actions! {
    /// What the inspector reports from the page: API calls, clicks, DOM events, WebSocket frames, console.
    InspectorAction, "inspector" {
        Capture = 300, "capture";
    }
}

/// Any action, whichever group it is in.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash)]
pub enum Action {
    Window(WindowAction),
    WebView(WebViewAction),
    Inspector(InspectorAction),
}

impl Action {
    pub fn from_code(code: u32) -> Option<Self> {
        WindowAction::from_code(code)
            .map(Action::Window)
            .or_else(|| WebViewAction::from_code(code).map(Action::WebView))
            .or_else(|| InspectorAction::from_code(code).map(Action::Inspector))
    }
}

/// Every action as `<group> <name> <code>` lines, for the other side to check itself against.
pub fn table() -> String {
    let mut lines = Vec::new();
    for action in WindowAction::ALL {
        lines.push(format!("{} {} {}", WindowAction::GROUP, action.name(), action.code()));
    }
    for action in WebViewAction::ALL {
        lines.push(format!("{} {} {}", WebViewAction::GROUP, action.name(), action.code()));
    }
    for action in InspectorAction::ALL {
        lines.push(format!("{} {} {}", InspectorAction::GROUP, action.name(), action.code()));
    }
    lines.join("\n")
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::collections::HashSet;

    #[test]
    fn codes_round_trip() {
        for action in WindowAction::ALL {
            assert_eq!(WindowAction::from_code(action.code()), Some(*action));
        }
        for action in WebViewAction::ALL {
            assert_eq!(WebViewAction::from_code(action.code()), Some(*action));
        }
        for action in InspectorAction::ALL {
            assert_eq!(InspectorAction::from_code(action.code()), Some(*action));
        }
        assert_eq!(WindowAction::from_code(200), None);
        assert_eq!(Action::from_code(202), Some(Action::WebView(WebViewAction::Eval)));
        assert_eq!(Action::from_code(999), None);
    }

    #[test]
    fn codes_are_unique_across_groups() {
        let codes: Vec<u32> = table().lines().map(|line| line.rsplit(' ').next().unwrap().parse().unwrap()).collect();
        assert_eq!(codes.len(), codes.iter().collect::<HashSet<_>>().len());
    }
}
