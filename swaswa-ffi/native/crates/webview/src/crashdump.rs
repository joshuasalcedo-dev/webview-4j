//! An opt-in crash recorder, for crashes nothing else reports (Windows): with `SWASWA_CRASH_DUMPS=<folder>`, an
//! access violation in any DLL but the JVM's own is written down as it happens, in the thread it happens in: one line
//! in `<folder>/crashes.log` (the DLL, the address, the thread) and a minidump a debugger reads (`cdb -z`).
//!
//! The JVM makes access violations on purpose (its null checks, its safepoints), in `jvm.dll` and in the code it
//! compiled (in no DLL at all): those are left alone. Nothing is changed: the exception goes on to whoever handles it.

#[cfg(windows)]
mod windows {
    use std::ffi::c_void;
    use std::path::PathBuf;
    use std::sync::OnceLock;
    use std::sync::atomic::{AtomicU32, Ordering};
    use windows_sys::Win32::Foundation::{EXCEPTION_ACCESS_VIOLATION, GENERIC_WRITE, HANDLE, HMODULE, INVALID_HANDLE_VALUE};
    use windows_sys::Win32::Storage::FileSystem::{CreateFileW, CREATE_ALWAYS, FILE_ATTRIBUTE_NORMAL, OPEN_ALWAYS, FILE_APPEND_DATA, WriteFile};
    use windows_sys::Win32::System::Diagnostics::Debug::{
        AddVectoredExceptionHandler, MiniDumpWithFullMemory, MiniDumpWriteDump, EXCEPTION_POINTERS, MINIDUMP_EXCEPTION_INFORMATION,
    };
    use windows_sys::Win32::System::LibraryLoader::{
        GetModuleFileNameW, GetModuleHandleExW, GET_MODULE_HANDLE_EX_FLAG_FROM_ADDRESS, GET_MODULE_HANDLE_EX_FLAG_UNCHANGED_REFCOUNT,
    };
    use windows_sys::Win32::System::Threading::{GetCurrentProcess, GetCurrentProcessId, GetCurrentThreadId};

    const CONTINUE_SEARCH: i32 = 0;
    static FOLDER: OnceLock<PathBuf> = OnceLock::new();
    static WRITTEN: AtomicU32 = AtomicU32::new(0);

    fn wide(text: &str) -> Vec<u16> {
        text.encode_utf16().chain(std::iter::once(0)).collect()
    }

    /// The DLL `address` is in, as a path; None for code in no DLL (what the JVM compiled).
    unsafe fn module_of(address: *const c_void) -> Option<String> {
        let mut module: HMODULE = std::ptr::null_mut();
        let found = unsafe {
            GetModuleHandleExW(
                GET_MODULE_HANDLE_EX_FLAG_FROM_ADDRESS | GET_MODULE_HANDLE_EX_FLAG_UNCHANGED_REFCOUNT,
                address as *const u16,
                &mut module,
            )
        };
        if found == 0 {
            return None;
        }
        let mut name = [0u16; 512];
        let length = unsafe { GetModuleFileNameW(module, name.as_mut_ptr(), name.len() as u32) } as usize;
        Some(String::from_utf16_lossy(&name[..length]))
    }

    unsafe fn append(folder: &PathBuf, line: &str) {
        let path = wide(&folder.join("crashes.log").to_string_lossy());
        let file: HANDLE = unsafe {
            CreateFileW(path.as_ptr(), FILE_APPEND_DATA, 0, std::ptr::null(), OPEN_ALWAYS, FILE_ATTRIBUTE_NORMAL, std::ptr::null_mut())
        };
        if file == INVALID_HANDLE_VALUE {
            return;
        }
        let mut written = 0u32;
        unsafe {
            WriteFile(file, line.as_ptr(), line.len() as u32, &mut written, std::ptr::null_mut());
            windows_sys::Win32::Foundation::CloseHandle(file);
        }
    }

    unsafe extern "system" fn on_exception(info: *mut EXCEPTION_POINTERS) -> i32 {
        let Some(folder) = FOLDER.get() else { return CONTINUE_SEARCH };
        let record = unsafe { &*(*info).ExceptionRecord };
        if record.ExceptionCode != EXCEPTION_ACCESS_VIOLATION {
            return CONTINUE_SEARCH;
        }
        let at = record.ExceptionAddress as *const c_void;
        let Some(module) = (unsafe { module_of(at) }) else { return CONTINUE_SEARCH }; // the JVM's compiled code
        let lower = module.to_ascii_lowercase();
        if lower.ends_with("\\jvm.dll") || lower.ends_with("\\libjvm.dll") {
            return CONTINUE_SEARCH; // the JVM's own, on purpose
        }
        let n = WRITTEN.fetch_add(1, Ordering::SeqCst);
        if n >= 5 {
            return CONTINUE_SEARCH; // a few are enough
        }
        let (kind, target) = if record.NumberParameters >= 2 {
            (record.ExceptionInformation[0], record.ExceptionInformation[1])
        } else {
            (0, 0)
        };
        let doing = match kind { 0 => "reading", 1 => "writing", 8 => "running", _ => "touching" };
        let line = format!(
            "access violation {doing} 0x{target:x} at 0x{:x} in {module} (thread {}, process {})\r\n",
            at as usize,
            unsafe { GetCurrentThreadId() },
            unsafe { GetCurrentProcessId() }
        );
        unsafe { append(folder, &line) };
        let dump = wide(&folder.join(format!("crash-{}-{n}.dmp", unsafe { GetCurrentProcessId() })).to_string_lossy());
        let file: HANDLE = unsafe {
            CreateFileW(dump.as_ptr(), GENERIC_WRITE, 0, std::ptr::null(), CREATE_ALWAYS, FILE_ATTRIBUTE_NORMAL, std::ptr::null_mut())
        };
        if file != INVALID_HANDLE_VALUE {
            let exception = MINIDUMP_EXCEPTION_INFORMATION { ThreadId: unsafe { GetCurrentThreadId() }, ExceptionPointers: info, ClientPointers: 0 };
            unsafe {
                MiniDumpWriteDump(GetCurrentProcess(), GetCurrentProcessId(), file, MiniDumpWithFullMemory, &exception, std::ptr::null(), std::ptr::null());
                windows_sys::Win32::Foundation::CloseHandle(file);
            }
        }
        CONTINUE_SEARCH
    }

    pub fn install() {
        let Ok(folder) = std::env::var("SWASWA_CRASH_DUMPS") else { return };
        let folder = PathBuf::from(folder);
        if std::fs::create_dir_all(&folder).is_err() || FOLDER.set(folder).is_err() {
            return;
        }
        unsafe { AddVectoredExceptionHandler(1, Some(on_exception)) };
        log::info!("crashes are recorded in {:?}", FOLDER.get());
    }
}

/// Starts recording crashes when `SWASWA_CRASH_DUMPS` names a folder; otherwise nothing.
pub fn install() {
    #[cfg(windows)]
    windows::install();
}
