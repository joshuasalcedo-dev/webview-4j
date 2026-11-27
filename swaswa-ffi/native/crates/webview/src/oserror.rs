//! What the operating system refused, said so the app's developer knows what to do: access denied (and whether
//! administrator rights would change that), or another system failure (a path that is not there, a full disk),
//! with the system's own error code. Java throws PermissionException or SystemException for these.

use std::path::Path;

use crate::payload::Outcome;

/// Whether this process runs with administrator rights (Windows: an elevated token; elsewhere: root).
pub fn elevated() -> bool {
    #[cfg(windows)]
    {
        use windows_sys::Win32::Foundation::{CloseHandle, HANDLE};
        use windows_sys::Win32::Security::{GetTokenInformation, TOKEN_ELEVATION, TOKEN_QUERY, TokenElevation};
        use windows_sys::Win32::System::Threading::{GetCurrentProcess, OpenProcessToken};
        let mut token: HANDLE = std::ptr::null_mut();
        if unsafe { OpenProcessToken(GetCurrentProcess(), TOKEN_QUERY, &mut token) } == 0 {
            return false;
        }
        let mut elevation = TOKEN_ELEVATION { TokenIsElevated: 0 };
        let mut size = 0u32;
        let ok = unsafe {
            GetTokenInformation(token, TokenElevation, &mut elevation as *mut _ as *mut _, std::mem::size_of::<TOKEN_ELEVATION>() as u32, &mut size)
        };
        unsafe { CloseHandle(token) };
        ok != 0 && elevation.TokenIsElevated != 0
    }
    #[cfg(unix)]
    {
        unsafe { libc::geteuid() == 0 }
    }
    #[cfg(not(any(windows, unix)))]
    {
        false
    }
}

/// A place only an administrator may write: the system's own folders.
pub fn protected(path: &Path) -> bool {
    let path = path.to_string_lossy().to_ascii_lowercase().replace('/', "\\");
    #[cfg(windows)]
    let roots: Vec<String> = ["SystemRoot", "ProgramFiles", "ProgramFiles(x86)", "ProgramW6432"]
        .iter()
        .filter_map(|v| std::env::var(v).ok())
        .map(|r| r.to_ascii_lowercase())
        .collect();
    #[cfg(not(windows))]
    let roots: Vec<String> = ["\\system", "\\library", "\\applications", "\\usr", "\\etc", "\\opt", "\\bin", "\\sbin", "\\var"]
        .iter()
        .map(|r| r.to_string())
        .collect();
    roots.iter().any(|root| path.starts_with(root.as_str()))
}

/// The outcome for `error`, met doing `what` (to `path`, if about one).
pub fn outcome(what: &str, error: &std::io::Error, path: Option<&Path>) -> Outcome {
    let code = error.raw_os_error();
    let place = path.map(|p| p.display().to_string()).unwrap_or_default();
    // 5 access denied, 740 the operation needs elevation, 1314 a privilege the process does not hold (Windows)
    let needs_rights = matches!(code, Some(740) | Some(1314));
    let denied = error.kind() == std::io::ErrorKind::PermissionDenied || matches!(code, Some(5) | Some(740) | Some(1314));
    let mut outcome = if denied {
        let elevation = needs_rights || (!elevated() && path.is_some_and(protected));
        let hint = if elevation {
            format!(
                "Only an administrator may write {place}: use a folder of the user's own (the app's data folder, NativeStore.directory(app), \
                 or the user's Documents), or run the app as administrator."
            )
        } else {
            format!(
                "The system refused access to {place}: it may be read-only, open in another program, or another user's. \
                 Choose another place, or give this user access to it."
            )
        };
        Outcome::error(403, format!("{what}: access denied ({error})")).kind("permission").hint(hint).header("Elevation", if elevation { "required" } else { "no" })
    } else {
        // Windows says "not found" for a path through a file: say which part is the file
        let in_the_way = path.and_then(|p| p.ancestors().skip(1).find(|a| a.is_file()));
        let hint = match error.kind() {
            _ if in_the_way.is_some() => format!(
                "{} is a file where a folder should be: choose another path (or move that file).",
                in_the_way.expect("found").display()
            ),
            std::io::ErrorKind::NotFound => format!("{place}, or a folder on the way to it, is not there: check the drive and the path."),
            std::io::ErrorKind::StorageFull => "The disk is full: free some space, or choose another drive.".to_string(),
            std::io::ErrorKind::NotADirectory | std::io::ErrorKind::AlreadyExists => {
                format!("Part of {place} is a file where a folder should be (or the other way round): choose another path.")
            }
            std::io::ErrorKind::ReadOnlyFilesystem => format!("{place} is on a read-only drive: choose another."),
            _ => format!("The system could not do it ({error}): check {place}."),
        };
        Outcome::error(500, format!("{what}: {error}")).kind("system").hint(hint)
    };
    if let Some(code) = code {
        outcome = outcome.header("Os-Error", code.to_string());
    }
    outcome
}
