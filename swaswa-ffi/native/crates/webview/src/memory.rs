//! How much memory the app uses: its own process (Java and this library) and each process of the browser engine
//! (WebView2's browser, renderers, GPU, utilities: usually most of it), and a view's memory target (low asks the
//! engine to use less: dropping caches, discarding what it can redraw).

use serde::Serialize;
use wry::WebView;

#[derive(Serialize, Debug, Clone, PartialEq, Eq)]
#[serde(rename_all = "camelCase")]
pub struct ProcessMemory {
    pub pid: u32,
    /// `host` (this process), or the engine's: `browser`, `renderer`, `gpu`, `utility`, `sandbox-helper`, `other`.
    pub kind: &'static str,
    /// Memory in RAM now (Windows: the working set).
    pub resident: u64,
    /// Memory only it uses, in RAM or paged out (Windows: private bytes): what it costs the system.
    pub private: u64,
}

/// The memory of `pid`; zeros if it cannot be read (it ended, or is another user's).
#[cfg(windows)]
fn of_process(pid: u32) -> (u64, u64) {
    use windows_sys::Win32::Foundation::CloseHandle;
    use windows_sys::Win32::System::ProcessStatus::{K32GetProcessMemoryInfo, PROCESS_MEMORY_COUNTERS, PROCESS_MEMORY_COUNTERS_EX};
    use windows_sys::Win32::System::Threading::{OpenProcess, PROCESS_QUERY_LIMITED_INFORMATION};
    let process = unsafe { OpenProcess(PROCESS_QUERY_LIMITED_INFORMATION, 0, pid) };
    if process.is_null() {
        return (0, 0);
    }
    let mut counters: PROCESS_MEMORY_COUNTERS_EX = unsafe { std::mem::zeroed() };
    counters.cb = std::mem::size_of::<PROCESS_MEMORY_COUNTERS_EX>() as u32;
    let ok = unsafe {
        K32GetProcessMemoryInfo(process, &mut counters as *mut _ as *mut PROCESS_MEMORY_COUNTERS, counters.cb)
    };
    unsafe { CloseHandle(process) };
    if ok == 0 { (0, 0) } else { (counters.WorkingSetSize as u64, counters.PrivateUsage as u64) }
}

/// This process and the engine's processes behind `webviews` (each profile has its own engine), each once.
#[cfg(windows)]
pub fn processes<'a>(webviews: impl Iterator<Item = &'a WebView>) -> Vec<ProcessMemory> {
    use webview2_com::Microsoft::Web::WebView2::Win32::{
        COREWEBVIEW2_PROCESS_KIND, COREWEBVIEW2_PROCESS_KIND_BROWSER, COREWEBVIEW2_PROCESS_KIND_GPU, COREWEBVIEW2_PROCESS_KIND_RENDERER,
        COREWEBVIEW2_PROCESS_KIND_SANDBOX_HELPER, COREWEBVIEW2_PROCESS_KIND_UTILITY, ICoreWebView2Environment8,
    };
    use windows::core::Interface;
    use wry::WebViewExtWindows;

    let host = std::process::id();
    let (resident, private) = of_process(host);
    let mut all = vec![ProcessMemory { pid: host, kind: "host", resident, private }];
    for webview in webviews {
        let Ok(environment) = webview.environment().cast::<ICoreWebView2Environment8>() else { continue };
        // SAFETY: a live view's environment, on the event loop's thread.
        let Ok(infos) = (unsafe { environment.GetProcessInfos() }) else { continue };
        let mut count = 0u32;
        if unsafe { infos.Count(&mut count) }.is_err() {
            continue;
        }
        for i in 0..count {
            let Ok(info) = (unsafe { infos.GetValueAtIndex(i) }) else { continue };
            let (mut pid, mut kind) = (0i32, COREWEBVIEW2_PROCESS_KIND::default());
            if unsafe { info.ProcessId(&mut pid) }.is_err() || unsafe { info.Kind(&mut kind) }.is_err() {
                continue;
            }
            let pid = pid as u32;
            if all.iter().any(|p| p.pid == pid) {
                continue;
            }
            let kind = match kind {
                COREWEBVIEW2_PROCESS_KIND_BROWSER => "browser",
                COREWEBVIEW2_PROCESS_KIND_RENDERER => "renderer",
                COREWEBVIEW2_PROCESS_KIND_GPU => "gpu",
                COREWEBVIEW2_PROCESS_KIND_UTILITY => "utility",
                COREWEBVIEW2_PROCESS_KIND_SANDBOX_HELPER => "sandbox-helper",
                _ => "other",
            };
            let (resident, private) = of_process(pid);
            all.push(ProcessMemory { pid, kind, resident, private });
        }
    }
    all
}

#[cfg(not(windows))]
pub fn processes<'a>(_webviews: impl Iterator<Item = &'a WebView>) -> Vec<ProcessMemory> {
    vec![ProcessMemory { pid: std::process::id(), kind: "host", resident: 0, private: 0 }]
}

/// Asks the engine to use less memory for `webview` (`low`), or as much as it likes (`normal`).
#[cfg(windows)]
pub fn set_level(webview: &WebView, low: bool) -> Result<(), String> {
    use webview2_com::Microsoft::Web::WebView2::Win32::{
        COREWEBVIEW2_MEMORY_USAGE_TARGET_LEVEL_LOW, COREWEBVIEW2_MEMORY_USAGE_TARGET_LEVEL_NORMAL, ICoreWebView2_19,
    };
    use windows::core::Interface;
    use wry::WebViewExtWindows;
    let core = webview.webview().cast::<ICoreWebView2_19>().map_err(|_| "this WebView2 Runtime is too old for memory targets".to_string())?;
    let level = if low { COREWEBVIEW2_MEMORY_USAGE_TARGET_LEVEL_LOW } else { COREWEBVIEW2_MEMORY_USAGE_TARGET_LEVEL_NORMAL };
    unsafe { core.SetMemoryUsageTargetLevel(level) }.map_err(|e| e.message().to_string())
}

/// Whether `webview` is asked to use less memory.
#[cfg(windows)]
pub fn level_low(webview: &WebView) -> Result<bool, String> {
    use webview2_com::Microsoft::Web::WebView2::Win32::{
        COREWEBVIEW2_MEMORY_USAGE_TARGET_LEVEL, COREWEBVIEW2_MEMORY_USAGE_TARGET_LEVEL_LOW, ICoreWebView2_19,
    };
    use windows::core::Interface;
    use wry::WebViewExtWindows;
    let core = webview.webview().cast::<ICoreWebView2_19>().map_err(|_| "this WebView2 Runtime is too old for memory targets".to_string())?;
    let mut level = COREWEBVIEW2_MEMORY_USAGE_TARGET_LEVEL::default();
    unsafe { core.MemoryUsageTargetLevel(&mut level) }.map_err(|e| e.message().to_string())?;
    Ok(level == COREWEBVIEW2_MEMORY_USAGE_TARGET_LEVEL_LOW)
}

#[cfg(not(windows))]
pub fn set_level(_webview: &WebView, _low: bool) -> Result<(), String> {
    Err("memory targets are set on Windows for now".into())
}

#[cfg(not(windows))]
pub fn level_low(_webview: &WebView) -> Result<bool, String> {
    Err("memory targets are read on Windows for now".into())
}

/// Reports each failure of an engine process behind `webview` to `on` (Windows): its kind (`browser`, `renderer`,
/// `frame-renderer`, `gpu`, `utility`, `unresponsive`, `other`), why (`crashed`, `out-of-memory`, `terminated`,
/// `launch-failed`, `profile-deleted`, `unexpected`, `unknown`) and its exit code.
#[cfg(windows)]
pub fn on_process_failed(webview: &WebView, on: impl Fn(&'static str, &'static str, i32) + 'static) -> Result<(), String> {
    use webview2_com::Microsoft::Web::WebView2::Win32::*;
    use webview2_com::ProcessFailedEventHandler;
    use windows::core::Interface;
    use wry::WebViewExtWindows;

    let core = webview.webview();
    let handler = ProcessFailedEventHandler::create(Box::new(move |_, args| {
        let Some(args) = args else { return Ok(()) };
        let mut kind = COREWEBVIEW2_PROCESS_FAILED_KIND::default();
        unsafe { args.ProcessFailedKind(&mut kind)? };
        let kind = match kind {
            COREWEBVIEW2_PROCESS_FAILED_KIND_BROWSER_PROCESS_EXITED => "browser",
            COREWEBVIEW2_PROCESS_FAILED_KIND_RENDER_PROCESS_EXITED => "renderer",
            COREWEBVIEW2_PROCESS_FAILED_KIND_FRAME_RENDER_PROCESS_EXITED => "frame-renderer",
            COREWEBVIEW2_PROCESS_FAILED_KIND_RENDER_PROCESS_UNRESPONSIVE => "unresponsive",
            COREWEBVIEW2_PROCESS_FAILED_KIND_GPU_PROCESS_EXITED => "gpu",
            COREWEBVIEW2_PROCESS_FAILED_KIND_UTILITY_PROCESS_EXITED => "utility",
            _ => "other",
        };
        let (mut reason, mut exit_code) = ("unknown", 0);
        if let Ok(more) = args.cast::<ICoreWebView2ProcessFailedEventArgs2>() {
            let mut why = COREWEBVIEW2_PROCESS_FAILED_REASON::default();
            if unsafe { more.Reason(&mut why) }.is_ok() {
                reason = match why {
                    COREWEBVIEW2_PROCESS_FAILED_REASON_CRASHED => "crashed",
                    COREWEBVIEW2_PROCESS_FAILED_REASON_OUT_OF_MEMORY => "out-of-memory",
                    COREWEBVIEW2_PROCESS_FAILED_REASON_TERMINATED => "terminated",
                    COREWEBVIEW2_PROCESS_FAILED_REASON_LAUNCH_FAILED => "launch-failed",
                    COREWEBVIEW2_PROCESS_FAILED_REASON_PROFILE_DELETED => "profile-deleted",
                    COREWEBVIEW2_PROCESS_FAILED_REASON_UNEXPECTED => "unexpected",
                    _ => "unknown",
                };
            }
            let _ = unsafe { more.ExitCode(&mut exit_code) };
        }
        on(kind, reason, exit_code);
        Ok(())
    }));
    let mut token: i64 = 0;
    // SAFETY: a live view's webview, on the event loop's thread; the handler lives as long as it.
    unsafe { core.add_ProcessFailed(&handler, &mut token) }.map_err(|e| e.message().to_string())
}

#[cfg(not(windows))]
pub fn on_process_failed(_webview: &WebView, _on: impl Fn(&'static str, &'static str, i32) + 'static) -> Result<(), String> {
    Ok(())
}
