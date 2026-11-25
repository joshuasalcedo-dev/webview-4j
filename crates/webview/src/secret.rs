//! Secrets in the system's own vault: Windows' Credential Manager, the macOS Keychain, the Secret Service on Linux.
//! For what must not sit in a file: a signed-in person's refresh token, an API key. The system keeps it encrypted
//! for the signed-in user; this only puts, gets and deletes it.
//!
//! ```text
//! wv_secret_set(service, slen, account, alen, secret, len)   0 kept, -1 failed
//! wv_secret_get(service, slen, account, alen, &out, &outlen)  1 found (free with wv_store_free), 0 none, -1 failed
//! wv_secret_delete(service, slen, account, alen)              1 deleted, 0 none, -1 failed
//! ```

use std::panic::{self, AssertUnwindSafe};

/// # Safety
/// `ptr` points to `len` readable bytes.
unsafe fn text<'a>(ptr: *const u8, len: usize) -> Option<&'a str> {
    if ptr.is_null() {
        return None;
    }
    std::str::from_utf8(unsafe { std::slice::from_raw_parts(ptr, len) }).ok()
}

fn entry(service: &str, account: &str) -> Option<keyring::Entry> {
    keyring::Entry::new(service, account).ok()
}

/// # Safety
/// Each pointer points to its length's readable bytes.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn wv_secret_set(service: *const u8, slen: usize, account: *const u8, alen: usize, secret: *const u8, len: usize) -> i32 {
    panic::catch_unwind(AssertUnwindSafe(|| {
        let (Some(s), Some(a)) = (unsafe { text(service, slen) }, unsafe { text(account, alen) }) else { return -1 };
        let Some(secret) = (unsafe { text(secret, len) }) else { return -1 };
        match entry(s, a).map(|e| e.set_password(secret)) {
            Some(Ok(())) => 0,
            _ => -1,
        }
    }))
    .unwrap_or(-1)
}

/// # Safety
/// The names point to their lengths' readable bytes; `out` and `out_len` are writable.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn wv_secret_get(service: *const u8, slen: usize, account: *const u8, alen: usize, out: *mut *mut u8, out_len: *mut usize) -> i32 {
    panic::catch_unwind(AssertUnwindSafe(|| {
        let (Some(s), Some(a)) = (unsafe { text(service, slen) }, unsafe { text(account, alen) }) else { return -1 };
        if out.is_null() || out_len.is_null() {
            return -1;
        }
        match entry(s, a).map(|e| e.get_password()) {
            Some(Ok(secret)) => {
                let boxed = secret.into_bytes().into_boxed_slice();
                let n = boxed.len();
                unsafe {
                    *out = Box::into_raw(boxed) as *mut u8;
                    *out_len = n;
                }
                1
            }
            Some(Err(keyring::Error::NoEntry)) => 0,
            _ => -1,
        }
    }))
    .unwrap_or(-1)
}

/// # Safety
/// The names point to their lengths' readable bytes.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn wv_secret_delete(service: *const u8, slen: usize, account: *const u8, alen: usize) -> i32 {
    panic::catch_unwind(AssertUnwindSafe(|| {
        let (Some(s), Some(a)) = (unsafe { text(service, slen) }, unsafe { text(account, alen) }) else { return -1 };
        match entry(s, a).map(|e| e.delete_credential()) {
            Some(Ok(())) => 1,
            Some(Err(keyring::Error::NoEntry)) => 0,
            _ => -1,
        }
    }))
    .unwrap_or(-1)
}

#[cfg(all(test, windows))]
mod tests {
    use super::*;

    #[test]
    fn keeps_gets_and_deletes_a_secret() {
        let (s, a) = ("webview4j-test", "secret-roundtrip");
        let secret = "a refresh token";
        unsafe {
            assert_eq!(wv_secret_set(s.as_ptr(), s.len(), a.as_ptr(), a.len(), secret.as_ptr(), secret.len()), 0);
            let mut out = std::ptr::null_mut();
            let mut len = 0usize;
            assert_eq!(wv_secret_get(s.as_ptr(), s.len(), a.as_ptr(), a.len(), &mut out, &mut len), 1);
            assert_eq!(std::slice::from_raw_parts(out, len), secret.as_bytes());
            crate::store::wv_store_free(out, len);
            assert_eq!(wv_secret_delete(s.as_ptr(), s.len(), a.as_ptr(), a.len()), 1);
            assert_eq!(wv_secret_get(s.as_ptr(), s.len(), a.as_ptr(), a.len(), &mut out, &mut len), 0);
        }
    }
}
