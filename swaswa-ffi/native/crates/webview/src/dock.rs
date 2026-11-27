//! Views moving between windows (Windows): a window's view into another window, its page running on (docking, an MDI
//! child); an embedded view out into a window of its own (tearing it off); embedded views a person moves and sizes
//! inside their window, and raises above the others there.

#[cfg(windows)]
mod windows {
    use windows_sys::Win32::Foundation::HWND;
    use windows_sys::Win32::UI::Input::KeyboardAndMouse::ReleaseCapture;
    use windows_sys::Win32::UI::WindowsAndMessaging::{
        CreateWindowExW, DestroyWindow, HWND_TOP, PostMessageW, SWP_NOACTIVATE, SWP_NOMOVE, SWP_NOSIZE, SWP_NOZORDER, SetWindowPos,
        WM_SYSCOMMAND, WS_CHILD, WS_CLIPCHILDREN, WS_CLIPSIBLINGS, WS_VISIBLE,
    };
    use wry::WebView;

    /// The native window a view's webview sits in (Wry's container): what moves, sizes and stacks it.
    pub fn webview_window(webview: &WebView) -> isize {
        use wry::WebViewExtWindows;
        let controller = webview.controller();
        let mut parent = windows::Win32::Foundation::HWND::default();
        // SAFETY: a live view's controller, on the event loop's thread.
        match unsafe { controller.ParentWindow(&mut parent) } {
            Ok(()) => parent.0 as isize,
            Err(_) => 0,
        }
    }

    /// A window of ours inside `host` (physical pixels), for a view moved in: it fills it, and it is what moves.
    pub fn container(host: isize, x: i32, y: i32, width: i32, height: i32) -> isize {
        let class: Vec<u16> = "STATIC\0".encode_utf16().collect();
        let hwnd = unsafe {
            CreateWindowExW(
                0,
                class.as_ptr(),
                std::ptr::null(),
                WS_CHILD | WS_VISIBLE | WS_CLIPCHILDREN | WS_CLIPSIBLINGS,
                x,
                y,
                width.max(1),
                height.max(1),
                host as HWND,
                std::ptr::null_mut(),
                std::ptr::null_mut(),
                std::ptr::null(),
            )
        };
        hwnd as isize
    }

    pub fn destroy(hwnd: isize) {
        unsafe { DestroyWindow(hwnd as HWND) };
    }

    /// Moves and sizes a child window inside its parent (physical pixels).
    pub fn place(hwnd: isize, x: i32, y: i32, width: i32, height: i32) {
        unsafe { SetWindowPos(hwnd as HWND, std::ptr::null_mut(), x, y, width.max(1), height.max(1), SWP_NOZORDER | SWP_NOACTIVATE) };
    }

    /// A native panel over a page (egui): it and its siblings each paint only where they are not covered, so the
    /// page's webview does not paint over it (child windows that overlap need WS_CLIPSIBLINGS for that).
    pub fn clip_siblings(hwnd: isize) {
        use windows_sys::Win32::UI::WindowsAndMessaging::{GW_CHILD, GW_HWNDNEXT, GWL_STYLE, GetParent, GetWindow, GetWindowLongPtrW, SetWindowLongPtrW};
        unsafe {
            let parent = GetParent(hwnd as HWND);
            if parent.is_null() {
                return;
            }
            let mut child = GetWindow(parent, GW_CHILD);
            while !child.is_null() {
                let style = GetWindowLongPtrW(child, GWL_STYLE);
                if style & WS_CLIPSIBLINGS as isize == 0 {
                    SetWindowLongPtrW(child, GWL_STYLE, style | WS_CLIPSIBLINGS as isize);
                }
                child = GetWindow(child, GW_HWNDNEXT);
            }
            let style = GetWindowLongPtrW(parent, GWL_STYLE);
            if style & WS_CLIPCHILDREN as isize == 0 {
                SetWindowLongPtrW(parent, GWL_STYLE, style | WS_CLIPCHILDREN as isize);
            }
        }
    }

    /// Above its siblings in its window.
    pub fn to_front(hwnd: isize) {
        unsafe { SetWindowPos(hwnd as HWND, HWND_TOP, 0, 0, 0, 0, SWP_NOMOVE | SWP_NOSIZE | SWP_NOACTIVATE) };
    }

    /// Hands the mouse to the system's own moving of the child window, as a press on a title bar does, from where the
    /// pointer is now: the window then stays under it where it was grabbed.
    pub fn start_move(hwnd: isize) {
        use windows_sys::Win32::Foundation::POINT;
        use windows_sys::Win32::UI::WindowsAndMessaging::{GetCursorPos, HTCAPTION, WM_NCLBUTTONDOWN};
        let mut at = POINT { x: 0, y: 0 };
        unsafe {
            if GetCursorPos(&mut at) == 0 {
                return;
            }
            // MAKELPARAM(x, y): screen coordinates, 16 bits each (negative left of or above the main monitor)
            let position = ((at.y as u16 as u32) << 16 | (at.x as u16 as u32)) as isize;
            ReleaseCapture();
            PostMessageW(hwnd as HWND, WM_NCLBUTTONDOWN, HTCAPTION as usize, position);
        }
    }

    /// The system's own sizing of the child window from an edge (`n`, `se`, ...), kept inside its parent.
    pub fn start_size(hwnd: isize, edge: &str) {
        let code = match edge {
            "w" => 1,
            "e" => 2,
            "n" => 3,
            "nw" => 4,
            "ne" => 5,
            "s" => 6,
            "sw" => 7,
            "se" => 8,
            _ => return,
        };
        unsafe {
            ReleaseCapture();
            PostMessageW(hwnd as HWND, WM_SYSCOMMAND, 0xF000 + code, 0); // SC_SIZE + WMSZ_*
        }
    }
}

#[cfg(windows)]
pub use windows::*;
