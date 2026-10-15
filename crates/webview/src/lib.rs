//! webview4j: the native library the Java side loads through FFM.
//!
//! The C API, all functions `extern "C"` and found by name:
//!
//! ```text
//! wv_run(on_event)              runs the event loop on this thread until wv_quit; returns its exit code,
//!                               or -1 if it is already running. Events arrive as JSON on the loop thread.
//! wv_submit(action, view, json, request)
//!                               hands an action (see action.rs) to the loop and returns at once: NULL when
//!                               taken, its reply arriving later as a {"type":"reply","request":...} event,
//!                               or a JSON error to free with wv_free. Callable from any thread.
//! wv_call(action, view, json)   performs an action from any thread but the loop's and waits; returns the
//!                               JSON reply, which the caller frees with wv_free.
//! wv_quit()                     closes every window and makes wv_run return.
//! wv_free(string)               frees a string wv_call returned.
//! wv_version(), wv_actions()    strings the library owns; never freed.
//! ```
//!
//! No panic crosses the boundary: each function catches it and answers with an error instead.

pub mod action;
mod bridge;
mod inspector;
mod logging;
pub mod payload;
pub mod runtime;

use std::ffi::{CStr, CString, c_char};
use std::panic::{self, AssertUnwindSafe};
use std::sync::OnceLock;

use action::Action;
use payload::Reply;

/// Runs the event loop on the calling thread until [`wv_quit`]; see [`runtime::run`].
/// Returns the loop's exit code, or -1 if a loop is already running or it panicked.
#[unsafe(no_mangle)]
pub extern "C" fn wv_run(on_event: Option<runtime::EventCallback>) -> i32 {
    match panic::catch_unwind(|| runtime::run(on_event)) {
        Ok(Ok(code)) => code,
        Ok(Err(_)) | Err(_) => -1,
    }
}

/// Hands `action` (a code from action.rs) on view `view` (NULL for none) with the JSON `payload`
/// (NULL for none) to the event loop. Returns NULL when taken: the reply arrives as the event
/// `{"type":"reply","request":<request>,...}`. Otherwise returns the JSON error; free it with
/// [`wv_free`].
///
/// # Safety
/// `view` and `payload` are NULL or NUL-terminated strings valid for the duration of the call.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn wv_submit(action: u32, view: *const c_char, payload: *const c_char, request: u64) -> *mut c_char {
    let refused = panic::catch_unwind(AssertUnwindSafe(|| {
        let (action, view, payload) = unsafe { arguments(action, view, payload) }?;
        runtime::submit(action, view, payload, runtime::ReplyTo::Event(request))
    }))
    .unwrap_or_else(|_| Err(Reply::Error("the action panicked".into())));
    match refused {
        Ok(()) => std::ptr::null_mut(),
        Err(error) => into_c(&error),
    }
}

/// Performs `action` on view `view` with the JSON `payload` and waits for the reply, which it
/// returns; free it with [`wv_free`]. Not from the event loop thread: that answers with an error.
///
/// # Safety
/// `view` and `payload` are NULL or NUL-terminated strings valid for the duration of the call.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn wv_call(action: u32, view: *const c_char, payload: *const c_char) -> *mut c_char {
    let reply = panic::catch_unwind(AssertUnwindSafe(|| match unsafe { arguments(action, view, payload) } {
        Ok((action, view, payload)) => runtime::call(action, view, payload),
        Err(error) => error,
    }))
    .unwrap_or_else(|_| Reply::Error("the action panicked".into()));
    into_c(&reply)
}

/// The C arguments of an action as Rust values.
unsafe fn arguments(action: u32, view: *const c_char, payload: *const c_char) -> Result<(Action, Option<String>, String), Reply> {
    let Some(action) = Action::from_code(action) else {
        return Err(Reply::Error(format!("no action with code {action}")));
    };
    let view = unsafe { string(view) }?;
    let payload = unsafe { string(payload) }?.unwrap_or_default();
    Ok((action, view, payload))
}

fn into_c(reply: &Reply) -> *mut c_char {
    CString::new(reply.to_json()).expect("JSON has no NUL byte").into_raw()
}

/// Closes every window and makes [`wv_run`] return. False if no loop is running.
#[unsafe(no_mangle)]
pub extern "C" fn wv_quit() -> bool {
    panic::catch_unwind(runtime::quit).unwrap_or(false)
}

/// Frees a string returned by [`wv_call`]. NULL is ignored.
///
/// # Safety
/// `string` is NULL or a pointer [`wv_call`] returned, not yet freed.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn wv_free(string: *mut c_char) {
    if !string.is_null() {
        drop(unsafe { CString::from_raw(string) });
    }
}

/// The library's version, a NUL-terminated string owned by the library (never freed by the caller).
#[unsafe(no_mangle)]
pub extern "C" fn wv_version() -> *const c_char {
    VERSION.as_ptr()
}

const VERSION: &CStr = match CStr::from_bytes_with_nul(concat!(env!("CARGO_PKG_VERSION"), "\0").as_bytes()) {
    Ok(version) => version,
    Err(_) => panic!("the crate version contains a NUL byte"),
};

/// Every action as `<group> <name> <code>` lines (see [`action::table`]), owned by the library.
/// The Java side checks its enums against it.
#[unsafe(no_mangle)]
pub extern "C" fn wv_actions() -> *const c_char {
    static TABLE: OnceLock<CString> = OnceLock::new();
    TABLE
        .get_or_init(|| CString::new(action::table()).expect("action names contain no NUL byte"))
        .as_ptr()
}

/// A C string argument as an owned String; NULL is None.
unsafe fn string(pointer: *const c_char) -> Result<Option<String>, Reply> {
    if pointer.is_null() {
        return Ok(None);
    }
    unsafe { CStr::from_ptr(pointer) }
        .to_str()
        .map(|s| Some(s.to_owned()))
        .map_err(|_| Reply::Error("arguments must be UTF-8".into()))
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn version_is_the_crate_version() {
        let version = unsafe { CStr::from_ptr(wv_version()) };
        assert_eq!(version.to_str().unwrap(), env!("CARGO_PKG_VERSION"));
    }

    fn call(action: u32, view: Option<&str>, payload: Option<&str>) -> String {
        let view = view.map(|v| CString::new(v).unwrap());
        let payload = payload.map(|p| CString::new(p).unwrap());
        unsafe {
            let reply = wv_call(
                action,
                view.as_ref().map_or(std::ptr::null(), |v| v.as_ptr()),
                payload.as_ref().map_or(std::ptr::null(), |p| p.as_ptr()),
            );
            let json = CStr::from_ptr(reply).to_str().unwrap().to_owned();
            wv_free(reply);
            json
        }
    }

    #[test]
    fn unknown_actions_and_a_stopped_loop_answer_with_errors() {
        assert!(call(999, None, None).contains("no action with code 999"));
        assert!(call(101, None, None).contains("not running"));
        assert!(!wv_quit());
        unsafe {
            let refused = wv_submit(101, std::ptr::null(), std::ptr::null(), 7);
            assert!(CStr::from_ptr(refused).to_str().unwrap().contains("not running"));
            wv_free(refused);
        }
    }
}
