//! The app's local store: keys and bytes in one file, with redb (an embedded ACID database, pure Rust). Java's
//! state (`ui.state`: server state, stores, saved screen state) keeps what it persists here; what the bytes mean is
//! Java's, this only keeps them, safely: a write is all there or not at all, even if the app is killed mid-way.
//!
//! ```text
//! wv_store_open(path, len)                         a handle (> 0), or 0 if the file cannot be opened
//! wv_store_put(h, key, klen, value, vlen)          0 written, -1 failed
//! wv_store_get(h, key, klen, &out, &outlen)        1 found (free `out` with wv_store_free), 0 none, -1 failed
//! wv_store_delete(h, key, klen)                    1 deleted, 0 none, -1 failed
//! wv_store_keys(h, prefix, plen, &out, &outlen)    the keys starting with prefix, NUL-separated; 0, or -1 failed
//! wv_store_free(ptr, len)                          frees what get and keys gave
//! wv_store_close(h)                                closes the file
//! ```

use redb::{Database, ReadableDatabase, ReadableTable, TableDefinition};
use std::collections::HashMap;
use std::panic::{self, AssertUnwindSafe};
use std::sync::atomic::{AtomicU64, Ordering};
use std::sync::{Arc, Mutex, OnceLock};

const ENTRIES: TableDefinition<&str, &[u8]> = TableDefinition::new("entries");

fn stores() -> &'static Mutex<HashMap<u64, Arc<Database>>> {
    static STORES: OnceLock<Mutex<HashMap<u64, Arc<Database>>>> = OnceLock::new();
    STORES.get_or_init(|| Mutex::new(HashMap::new()))
}

static NEXT: AtomicU64 = AtomicU64::new(1);

fn store(handle: u64) -> Option<Arc<Database>> {
    stores().lock().ok()?.get(&handle).cloned()
}

/// # Safety
/// `ptr` points to `len` readable bytes (or is null with `len` 0).
unsafe fn text<'a>(ptr: *const u8, len: usize) -> Option<&'a str> {
    if ptr.is_null() {
        return if len == 0 { Some("") } else { None };
    }
    std::str::from_utf8(unsafe { std::slice::from_raw_parts(ptr, len) }).ok()
}

/// Hands `bytes` to the caller: it frees them with [`wv_store_free`].
///
/// # Safety
/// `out` and `out_len` are writable.
unsafe fn give(bytes: Vec<u8>, out: *mut *mut u8, out_len: *mut usize) {
    let boxed = bytes.into_boxed_slice();
    let len = boxed.len();
    let ptr = Box::into_raw(boxed) as *mut u8;
    unsafe {
        *out = ptr;
        *out_len = len;
    }
}

/// Opens (or makes) the store in the file at `path` (UTF-8): a handle, or 0.
///
/// # Safety
/// `path` points to `len` readable bytes.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn wv_store_open(path: *const u8, len: usize) -> u64 {
    panic::catch_unwind(AssertUnwindSafe(|| {
        let Some(path) = (unsafe { text(path, len) }) else { return 0 };
        if let Some(parent) = std::path::Path::new(path).parent() {
            let _ = std::fs::create_dir_all(parent);
        }
        let Ok(db) = Database::create(path) else { return 0 };
        // The table exists from the start, so a read before any write finds it.
        if let Ok(tx) = db.begin_write() {
            if tx.open_table(ENTRIES).is_err() || tx.commit().is_err() {
                return 0;
            }
        } else {
            return 0;
        }
        let handle = NEXT.fetch_add(1, Ordering::Relaxed);
        match stores().lock() {
            Ok(mut s) => {
                s.insert(handle, Arc::new(db));
                handle
            }
            Err(_) => 0,
        }
    }))
    .unwrap_or(0)
}

/// # Safety
/// `key` and `value` point to `key_len` and `value_len` readable bytes.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn wv_store_put(handle: u64, key: *const u8, key_len: usize, value: *const u8, value_len: usize) -> i32 {
    panic::catch_unwind(AssertUnwindSafe(|| {
        let (Some(db), Some(key)) = (store(handle), unsafe { text(key, key_len) }) else { return -1 };
        let value: &[u8] = if value.is_null() { &[] } else { unsafe { std::slice::from_raw_parts(value, value_len) } };
        let write = || -> Result<(), redb::Error> {
            let tx = db.begin_write()?;
            {
                let mut table = tx.open_table(ENTRIES)?;
                table.insert(key, value)?;
            }
            tx.commit()?;
            Ok(())
        };
        if write().is_ok() { 0 } else { -1 }
    }))
    .unwrap_or(-1)
}

/// # Safety
/// `key` points to `key_len` readable bytes; `out` and `out_len` are writable.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn wv_store_get(handle: u64, key: *const u8, key_len: usize, out: *mut *mut u8, out_len: *mut usize) -> i32 {
    panic::catch_unwind(AssertUnwindSafe(|| {
        let (Some(db), Some(key)) = (store(handle), unsafe { text(key, key_len) }) else { return -1 };
        if out.is_null() || out_len.is_null() {
            return -1;
        }
        let read = || -> Result<Option<Vec<u8>>, redb::Error> {
            let tx = db.begin_read()?;
            let table = tx.open_table(ENTRIES)?;
            Ok(table.get(key)?.map(|v| v.value().to_vec()))
        };
        match read() {
            Ok(Some(bytes)) => {
                unsafe { give(bytes, out, out_len) };
                1
            }
            Ok(None) => 0,
            Err(_) => -1,
        }
    }))
    .unwrap_or(-1)
}

/// # Safety
/// `key` points to `key_len` readable bytes.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn wv_store_delete(handle: u64, key: *const u8, key_len: usize) -> i32 {
    panic::catch_unwind(AssertUnwindSafe(|| {
        let (Some(db), Some(key)) = (store(handle), unsafe { text(key, key_len) }) else { return -1 };
        let delete = || -> Result<bool, redb::Error> {
            let tx = db.begin_write()?;
            let removed = {
                let mut table = tx.open_table(ENTRIES)?;
                table.remove(key)?.is_some()
            };
            tx.commit()?;
            Ok(removed)
        };
        match delete() {
            Ok(true) => 1,
            Ok(false) => 0,
            Err(_) => -1,
        }
    }))
    .unwrap_or(-1)
}

/// The keys that start with `prefix`, NUL-separated, in order.
///
/// # Safety
/// `prefix` points to `prefix_len` readable bytes; `out` and `out_len` are writable.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn wv_store_keys(handle: u64, prefix: *const u8, prefix_len: usize, out: *mut *mut u8, out_len: *mut usize) -> i32 {
    panic::catch_unwind(AssertUnwindSafe(|| {
        let (Some(db), Some(prefix)) = (store(handle), unsafe { text(prefix, prefix_len) }) else { return -1 };
        if out.is_null() || out_len.is_null() {
            return -1;
        }
        let list = || -> Result<Vec<u8>, redb::Error> {
            let tx = db.begin_read()?;
            let table = tx.open_table(ENTRIES)?;
            let mut keys = Vec::new();
            for entry in table.range(prefix..)? {
                let (key, _) = entry?;
                let key = key.value();
                if !key.starts_with(prefix) {
                    break;
                }
                if !keys.is_empty() {
                    keys.push(0);
                }
                keys.extend_from_slice(key.as_bytes());
            }
            Ok(keys)
        };
        match list() {
            Ok(keys) => {
                unsafe { give(keys, out, out_len) };
                0
            }
            Err(_) => -1,
        }
    }))
    .unwrap_or(-1)
}

/// Frees bytes [`wv_store_get`] or [`wv_store_keys`] gave.
///
/// # Safety
/// `ptr` and `len` are exactly what one of them gave, and are freed once.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn wv_store_free(ptr: *mut u8, len: usize) {
    if ptr.is_null() {
        return;
    }
    let _ = panic::catch_unwind(AssertUnwindSafe(|| unsafe {
        drop(Box::from_raw(std::ptr::slice_from_raw_parts_mut(ptr, len)));
    }));
}

#[unsafe(no_mangle)]
pub extern "C" fn wv_store_close(handle: u64) {
    let _ = panic::catch_unwind(|| {
        if let Ok(mut s) = stores().lock() {
            s.remove(&handle);
        }
    });
}

#[cfg(test)]
mod tests {
    use super::*;

    fn put(h: u64, k: &str, v: &[u8]) -> i32 {
        unsafe { wv_store_put(h, k.as_ptr(), k.len(), v.as_ptr(), v.len()) }
    }

    fn get(h: u64, k: &str) -> Option<Vec<u8>> {
        let mut out = std::ptr::null_mut();
        let mut len = 0usize;
        let r = unsafe { wv_store_get(h, k.as_ptr(), k.len(), &mut out, &mut len) };
        if r != 1 {
            return None;
        }
        let bytes = unsafe { std::slice::from_raw_parts(out, len) }.to_vec();
        unsafe { wv_store_free(out, len) };
        Some(bytes)
    }

    #[test]
    fn keeps_bytes_by_key_across_opening_again() {
        let dir = std::env::temp_dir().join(format!("wv-store-{}", std::process::id()));
        let path = dir.join("state.redb");
        let p = path.to_string_lossy().to_string();
        let h = unsafe { wv_store_open(p.as_ptr(), p.len()) };
        assert!(h > 0);
        assert_eq!(put(h, "orders.1", b"one"), 0);
        assert_eq!(put(h, "orders.2", b"two"), 0);
        assert_eq!(put(h, "people", b""), 0);
        assert_eq!(get(h, "orders.1").as_deref(), Some(&b"one"[..]));
        assert_eq!(get(h, "missing"), None);

        let mut out = std::ptr::null_mut();
        let mut len = 0usize;
        assert_eq!(unsafe { wv_store_keys(h, b"orders.".as_ptr(), 7, &mut out, &mut len) }, 0);
        assert_eq!(unsafe { std::slice::from_raw_parts(out, len) }, b"orders.1\0orders.2");
        unsafe { wv_store_free(out, len) };

        assert_eq!(unsafe { wv_store_delete(h, b"orders.1".as_ptr(), 8) }, 1);
        assert_eq!(unsafe { wv_store_delete(h, b"orders.1".as_ptr(), 8) }, 0);
        wv_store_close(h);

        let h = unsafe { wv_store_open(p.as_ptr(), p.len()) };
        assert_eq!(get(h, "orders.2").as_deref(), Some(&b"two"[..]));
        assert_eq!(get(h, "orders.1"), None);
        wv_store_close(h);
        let _ = std::fs::remove_dir_all(dir);
    }
}
