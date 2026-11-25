//! The Chrome DevTools Protocol of a view's page (Windows: WebView2's): Java calls its methods
//! (`view.<view>.cdp.call`) and hears its events (`view.<view>.cdp.listen` → `view.<view>.cdp`). A
//! JavaFX node draws the page from `Page.screencastFrame` and sends it the person's input with
//! `Input.dispatchMouseEvent` / `Input.dispatchKeyEvent`; anything else the protocol does works too.

use std::collections::HashSet;
use std::sync::Mutex;

use wry::WebView;

/// The events already listened to, by `<view>/<event>`: listening twice does not report twice.
static LISTENING: Mutex<Option<HashSet<String>>> = Mutex::new(None);

/// Whether `view` already listens to `event`; marks it as listening.
fn first_listen(view: &str, event: &str) -> bool {
    let mut listening = LISTENING.lock().unwrap();
    listening.get_or_insert_with(HashSet::new).insert(format!("{view}/{event}"))
}

/// Forgets a closed view's listeners.
pub fn forget(view: &str) {
    if let Some(listening) = LISTENING.lock().unwrap().as_mut() {
        let prefix = format!("{view}/");
        listening.retain(|key| !key.starts_with(&prefix));
    }
}

/// Calls `method` with `params` (a JSON object); `done` gets its result as JSON, or the protocol's error.
#[cfg(target_os = "windows")]
pub fn call(webview: &WebView, method: &str, params: &str, done: impl FnOnce(Result<String, String>) + 'static) -> Result<(), String> {
    use webview2_com::CallDevToolsProtocolMethodCompletedHandler;
    use windows::core::HSTRING;
    use wry::WebViewExtWindows;

    let core = webview.webview();
    let handler = CallDevToolsProtocolMethodCompletedHandler::create(Box::new(move |error, json| {
        done(match error {
            Ok(()) => Ok(json),
            // The protocol's own error is JSON too ({"code":...,"message":...}) when it says one.
            Err(e) => Err(if json.trim().is_empty() { e.message().to_string() } else { json }),
        });
        Ok(())
    }));
    // SAFETY: the webview is this live view's, on the event loop's thread, where WebView2 wants it.
    unsafe { core.CallDevToolsProtocolMethod(&HSTRING::from(method), &HSTRING::from(params), &handler) }
        .map_err(|e| e.message().to_string())
}

/// Reports each `event` of the page to `on`, as its parameters' JSON.
#[cfg(target_os = "windows")]
pub fn listen(view: &str, webview: &WebView, event: &str, on: impl Fn(String) + 'static) -> Result<(), String> {
    use webview2_com::{take_pwstr, DevToolsProtocolEventReceivedEventHandler};
    use windows::core::{HSTRING, PWSTR};
    use wry::WebViewExtWindows;

    if !first_listen(view, event) {
        return Ok(());
    }
    let core = webview.webview();
    // SAFETY: as in `call`.
    let receiver = unsafe { core.GetDevToolsProtocolEventReceiver(&HSTRING::from(event)) }.map_err(|e| e.message().to_string())?;
    let handler = DevToolsProtocolEventReceivedEventHandler::create(Box::new(move |_, args| {
        let Some(args) = args else { return Ok(()) };
        let mut json = PWSTR::null();
        // SAFETY: WebView2 fills `json` with a string this side owns; take_pwstr frees it.
        unsafe { args.ParameterObjectAsJson(&mut json)? };
        on(take_pwstr(json));
        Ok(())
    }));
    let mut token: i64 = 0;
    // SAFETY: as in `call`; the handler lives as long as the webview.
    unsafe { receiver.add_DevToolsProtocolEventReceived(&handler, &mut token) }.map_err(|e| e.message().to_string())
}

#[cfg(not(target_os = "windows"))]
pub fn call(_webview: &WebView, _method: &str, _params: &str, _done: impl FnOnce(Result<String, String>) + 'static) -> Result<(), String> {
    Err("the DevTools Protocol is given on Windows only, for now".to_string())
}

#[cfg(not(target_os = "windows"))]
pub fn listen(_view: &str, _webview: &WebView, _event: &str, _on: impl Fn(String) + 'static) -> Result<(), String> {
    Err("the DevTools Protocol is given on Windows only, for now".to_string())
}

/// A window drawn for Java, not for the person: still on the screen (so the engine keeps drawing it, and its
/// pop-ups, a select's list, open in the right place) but all but invisible, and the mouse goes through it to the
/// window below. Its page is seen in Java (a JavaFX node) from `Page.screencastFrame`.
#[cfg(target_os = "windows")]
pub fn offscreen(window: &tao::window::Window) {
    use tao::platform::windows::WindowExtWindows;
    use windows_sys::Win32::UI::WindowsAndMessaging::{SetLayeredWindowAttributes, LWA_ALPHA};
    // tao keeps WS_EX_LAYERED | WS_EX_TRANSPARENT on it from here on, whatever else it changes.
    let _ = window.set_ignore_cursor_events(true);
    // SAFETY: the handle is this live, now layered window's. 1 of 255, not 0: a window that is not drawn at all
    // could be taken for one that is hidden.
    unsafe { SetLayeredWindowAttributes(window.hwnd() as _, 0, 1, LWA_ALPHA) };
}

#[cfg(not(target_os = "windows"))]
pub fn offscreen(_window: &tao::window::Window) {}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn an_event_is_listened_to_once_per_view() {
        assert!(first_listen("wv-t1", "Page.screencastFrame"));
        assert!(!first_listen("wv-t1", "Page.screencastFrame"));
        assert!(first_listen("wv-t2", "Page.screencastFrame"));
        forget("wv-t1");
        assert!(first_listen("wv-t1", "Page.screencastFrame"));
    }
}
