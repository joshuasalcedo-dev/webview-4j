//! webview4j: the native library the Java side loads through FFM.
//!
//! Every function exported here is `extern "C"` with `#[unsafe(no_mangle)]`, so Java finds it
//! by name with `SymbolLookup`. The window and event-loop code moves here from `main.rs`
//! as the FFM API grows; the sidecar binary then becomes a thin HTTP layer over it.

use std::ffi::{CStr, c_char};

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
}
