//! Spelling and grammar for Java (crates/grammar, Harper), through FFM:
//!
//! - `wv_grammar_new(tag, len)`: a checker for a language tag (en-US, en-GB, en-AU, en-CA, en-IN), or null.
//! - `wv_grammar_check(checker, text, len, &out, &out_len)`: the mistakes as UTF-8 JSON (positions in UTF-16, as
//!   Java's String counts), given back with `wv_store_free(out, out_len)`. 1 on success, -1 on bad input.
//! - `wv_grammar_free(checker)`.
//!
//! A checker can be used from any thread (it locks). A panic inside Harper does not cross into Java.

use std::panic::{self, AssertUnwindSafe};

/// A checker made by `wv_grammar_new`, null if Harper could not start.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn wv_grammar_new(tag: *const u8, len: usize) -> *mut grammar::Checker {
    let tag = if tag.is_null() { "" } else { std::str::from_utf8(unsafe { std::slice::from_raw_parts(tag, len) }).unwrap_or("") };
    panic::catch_unwind(|| Box::into_raw(Box::new(grammar::Checker::new(grammar::Dialect::from_tag(tag))))).unwrap_or(std::ptr::null_mut())
}

#[unsafe(no_mangle)]
pub unsafe extern "C" fn wv_grammar_check(
    checker: *const grammar::Checker,
    text: *const u8,
    len: usize,
    out: *mut *mut u8,
    out_len: *mut usize,
) -> i32 {
    if checker.is_null() || out.is_null() || out_len.is_null() || (text.is_null() && len > 0) {
        return -1;
    }
    let checker = unsafe { &*checker };
    let bytes: &[u8] = if len == 0 { &[] } else { unsafe { std::slice::from_raw_parts(text, len) } };
    let Ok(source) = std::str::from_utf8(bytes) else { return -1 };
    let json = panic::catch_unwind(AssertUnwindSafe(|| checker.check_json(source))).unwrap_or_else(|_| "[]".into());
    let boxed = json.into_bytes().into_boxed_slice();
    unsafe {
        *out_len = boxed.len();
        *out = Box::into_raw(boxed) as *mut u8;
    }
    1
}

#[unsafe(no_mangle)]
pub unsafe extern "C" fn wv_grammar_free(checker: *mut grammar::Checker) {
    if !checker.is_null() {
        let _ = panic::catch_unwind(AssertUnwindSafe(|| drop(unsafe { Box::from_raw(checker) })));
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn through_the_c_functions() {
        let tag = b"en-US";
        let checker = unsafe { wv_grammar_new(tag.as_ptr(), tag.len()) };
        assert!(!checker.is_null());
        let text = "This is an test.";
        let mut out = std::ptr::null_mut();
        let mut out_len = 0usize;
        assert_eq!(unsafe { wv_grammar_check(checker, text.as_ptr(), text.len(), &mut out, &mut out_len) }, 1);
        let json = std::str::from_utf8(unsafe { std::slice::from_raw_parts(out, out_len) }).unwrap().to_string();
        unsafe { crate::store::wv_store_free(out, out_len) };
        assert!(json.contains("\"from\":8") && json.contains("\"a\""), "{json}");
        unsafe { wv_grammar_free(checker) };
    }
}
