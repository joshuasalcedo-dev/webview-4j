//! webview4j: the native library the Java side loads through FFM. It speaks the protocol in
//! PROTOCOL.md: frames in through [`wv_send`], frames out through the callback given to [`wv_run`].
//!
//! ```text
//! wv_run(on_message)        runs the event loop on this thread until app.quit; returns its exit
//!                           code, or -1 if a loop already runs. Frames arrive on the callback.
//! wv_send(frame, length)    hands one frame to the loop from any thread: 0 taken, 1 no loop is
//!                           running, 2 the frame is malformed or over a limit.
//! wv_version()              a string the library owns; never freed.
//! ```
//!
//! No panic crosses the boundary: each function catches it and answers with an error code instead.

mod bridge;
pub mod frame;
mod inspector;
mod logging;
pub mod payload;
pub mod runtime;

use std::ffi::{CStr, c_char};
use std::panic::{self, AssertUnwindSafe};

/// Runs the event loop on the calling thread until `app.quit`; see [`runtime::run`].
/// Returns the loop's exit code, or -1 if a loop is already running or it panicked.
#[unsafe(no_mangle)]
pub extern "C" fn wv_run(on_message: Option<runtime::MessageCallback>) -> i32 {
    match panic::catch_unwind(|| runtime::run(on_message)) {
        Ok(Ok(code)) => code,
        Ok(Err(_)) | Err(_) => -1,
    }
}

/// Hands one frame to the event loop and returns at once: 0 taken, 1 no loop is running, 2 the
/// frame is malformed or over a limit (PROTOCOL.md §1). Anything wrong with what the frame asks
/// for comes back as a reply.
///
/// # Safety
/// `frame` points to `length` readable bytes, valid for the duration of the call.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn wv_send(frame: *const u8, length: usize) -> i32 {
    if frame.is_null() {
        return 2;
    }
    let bytes = unsafe { std::slice::from_raw_parts(frame, length) };
    panic::catch_unwind(AssertUnwindSafe(|| match frame::decode(bytes) {
        Err(_) => 2,
        Ok(message) => match runtime::send(message) {
            Ok(()) => 0,
            Err(runtime::NotSent::NotRunning) => 1,
        },
    }))
    .unwrap_or(2)
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

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn version_is_the_crate_version() {
        let version = unsafe { CStr::from_ptr(wv_version()) };
        assert_eq!(version.to_str().unwrap(), env!("CARGO_PKG_VERSION"));
    }

    #[test]
    fn send_refuses_bad_frames_and_a_stopped_loop() {
        let good = frame::Message::new("app.list").header("Reply-To", "_inbox.1").encode();
        let bad = b"MSG app..list 2 0\r\n\r\n";
        unsafe {
            assert_eq!(wv_send(good.as_ptr(), good.len()), 1, "no loop is running");
            assert_eq!(wv_send(bad.as_ptr(), bad.len()), 2);
            assert_eq!(wv_send(std::ptr::null(), 0), 2);
        }
    }
}
