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
mod crashdump;
mod cdp;
pub mod capture;
#[cfg(windows)]
mod mediafoundation;
mod memory;
pub mod jobs;
mod pointer;
mod dragout;
mod dock;
pub mod oserror;
mod dialog;
mod icon;
pub mod frame;
mod inspector;
mod logging;
mod sound;
pub mod store;
pub mod secret;
pub mod grammar;
pub mod payload;
pub mod runtime;

use std::ffi::{CStr, c_char};
use std::panic::{self, AssertUnwindSafe};

/// Runs the event loop on the calling thread until `app.quit`; see [`runtime::run`].
/// Returns the loop's exit code, or -1 if a loop is already running or it panicked.
#[unsafe(no_mangle)]
pub extern "C" fn wv_run(on_message: Option<runtime::MessageCallback>) -> i32 {
    static CRASHES: std::sync::Once = std::sync::Once::new();
    CRASHES.call_once(crashdump::install);
    let run = move || match panic::catch_unwind(|| runtime::run(on_message)) {
        Ok(Ok(code)) => code,
        Ok(Err(_)) | Err(_) => -1,
    };
    // Not on the caller's stack: a JVM thread's stack is the JVM's (its guard zones, its size), and a window's
    // messages can nest deep in a loop (a debug build's frames are large). The loop gets a native thread of its own,
    // with room, and this call waits for it. macOS: the loop must be on the process's first thread, the caller's.
    #[cfg(not(target_os = "macos"))]
    {
        match std::thread::Builder::new().name("swaswa-event-loop".into()).stack_size(LOOP_STACK).spawn(run) {
            Ok(thread) => thread.join().unwrap_or(-1),
            Err(_) => -1,
        }
    }
    #[cfg(target_os = "macos")]
    {
        run()
    }
}

/// Whether this process runs with administrator rights (Windows: elevated; elsewhere: root): 1 yes, 0 no. Needs no
/// running loop.
#[unsafe(no_mangle)]
pub extern "C" fn wv_elevated() -> i32 {
    i32::from(oserror::elevated())
}

/// Puts process `pid` in the app's job, so it ends with the app however the app ends (Windows): 1 done, 0 not, -1 not
/// on this system. Needs no running loop.
#[unsafe(no_mangle)]
pub extern "C" fn wv_job_assign(pid: u32) -> i32 {
    jobs::assign(pid)
}

/// Holds this process and all it starts from now on to `bytes` of memory together (Windows): 1 done, 0 not, -1 not
/// on this system. Before the browser engine starts. Needs no running loop.
#[unsafe(no_mangle)]
pub extern "C" fn wv_cap_memory(bytes: u64) -> i32 {
    jobs::cap_this_process(bytes)
}

/// The event loop's own stack: 16 MB reserved (committed only as it is used).
#[cfg(not(target_os = "macos"))]
const LOOP_STACK: usize = 16 * 1024 * 1024;

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
