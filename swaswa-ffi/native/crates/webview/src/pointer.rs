//! Where the pointer is and what is under it, and when a person starts and stops moving (or sizing) a window: what a
//! drag between the app's windows (a tab torn off, a panel docked) needs to know.

/// The window of the app's own under the screen point (`x`, `y`, physical pixels), as the system has them stacked:
/// its native handle, to match with a view's. 0 when none of them is there (another app's, the desktop).
#[cfg(windows)]
pub fn window_at(x: f64, y: f64) -> isize {
    use windows_sys::Win32::Foundation::POINT;
    use windows_sys::Win32::UI::WindowsAndMessaging::{GA_ROOT, GetAncestor, WindowFromPoint};
    let point = POINT { x: x.round() as i32, y: y.round() as i32 };
    let hwnd = unsafe { WindowFromPoint(point) };
    if hwnd.is_null() {
        return 0;
    }
    unsafe { GetAncestor(hwnd, GA_ROOT) as isize }
}

#[cfg(not(windows))]
pub fn window_at(_x: f64, _y: f64) -> isize {
    0
}

/// The top window other than `except` (in the system's stacking) that is shown and has the point (physical pixels):
/// what a dragged window was let go over, `except` being the dragged one. 0 if none.
#[cfg(windows)]
pub fn window_below(except: isize, x: f64, y: f64) -> isize {
    use windows_sys::Win32::Foundation::{HWND, RECT};
    use windows_sys::Win32::UI::WindowsAndMessaging::{GW_HWNDNEXT, GetWindow, GetWindowRect, IsIconic, IsWindowVisible};
    use windows_sys::Win32::UI::WindowsAndMessaging::GW_HWNDFIRST;
    let (x, y) = (x.round() as i32, y.round() as i32);
    // from the top of the stacking, past `except` wherever it is (a window being dragged is usually on top, but showing
    // where it would go may bring its target above it)
    let mut at = unsafe { GetWindow(except as HWND, GW_HWNDFIRST) };
    while !at.is_null() && at as isize == except {
        at = unsafe { GetWindow(at, GW_HWNDNEXT) };
    }
    while !at.is_null() {
        let mut r = RECT { left: 0, top: 0, right: 0, bottom: 0 };
        let shown = unsafe { IsWindowVisible(at) } != 0 && unsafe { IsIconic(at) } == 0 && !see_through(at);
        if at as isize != except && shown && unsafe { GetWindowRect(at, &mut r) } != 0 && x >= r.left && x < r.right && y >= r.top && y < r.bottom {
            return at as isize;
        }
        at = unsafe { GetWindow(at, GW_HWNDNEXT) };
    }
    0
}

/// A window that is there but covers nothing: one the mouse goes through (an overlay of a GPU's or a mouse maker's
/// software), or one the system keeps but does not draw (cloaked: on another virtual desktop, a suspended app's).
#[cfg(windows)]
fn see_through(hwnd: windows_sys::Win32::Foundation::HWND) -> bool {
    use windows_sys::Win32::Graphics::Dwm::DwmGetWindowAttribute;
    use windows_sys::Win32::UI::WindowsAndMessaging::{GWL_EXSTYLE, GetWindowLongPtrW, WS_EX_TRANSPARENT};
    let ex = unsafe { GetWindowLongPtrW(hwnd, GWL_EXSTYLE) } as u32;
    if ex & WS_EX_TRANSPARENT != 0 {
        return true;
    }
    let mut cloaked: u32 = 0;
    const DWMWA_CLOAKED: u32 = 14;
    let ok = unsafe { DwmGetWindowAttribute(hwnd, DWMWA_CLOAKED, &mut cloaked as *mut u32 as _, 4) } == 0;
    ok && cloaked != 0
}

#[cfg(not(windows))]
pub fn window_below(_except: isize, _x: f64, _y: f64) -> isize {
    0
}

/// A move (or sizing) of a window by a person: its phase, its outer bounds then (physical pixels), the pointer's place,
/// and which of Ctrl, Shift and Alt are held (a key that says "do not dock it", as in Visual Studio).
pub struct Move {
    pub phase: &'static str,
    pub bounds: (i32, i32, i32, i32),
    pub cursor: (i32, i32),
    pub keys: (bool, bool, bool),
}

pub type OnMove = Box<dyn Fn(Move)>;

/// Starts reporting when a person starts moving or sizing the window `hwnd`, while they do (about 30 times a second),
/// and when they let go.
#[cfg(windows)]
pub fn watch_moves(hwnd: isize, on: OnMove) {
    watch(hwnd, on, None);
}

/// [`watch_moves`], and `on_size(width, height)` (its client area, physical pixels) each time its size changes.
#[cfg(windows)]
pub fn watch(hwnd: isize, on: OnMove, on_size: Option<Box<dyn Fn(i32, i32)>>) {
    use std::cell::Cell;
    use std::time::{Duration, Instant};
    use windows_sys::Win32::Foundation::{HWND, LPARAM, LRESULT, POINT, RECT, WPARAM};
    use windows_sys::Win32::UI::Input::KeyboardAndMouse::{GetKeyState, VK_CONTROL, VK_MENU, VK_SHIFT};
    use windows_sys::Win32::UI::Shell::{DefSubclassProc, SetWindowSubclass};
    use windows_sys::Win32::UI::WindowsAndMessaging::{GetCursorPos, GetWindowRect};

    const WM_ENTERSIZEMOVE: u32 = 0x0231;
    const WM_EXITSIZEMOVE: u32 = 0x0232;
    const WM_MOVING: u32 = 0x0216;
    const WM_SIZING: u32 = 0x0214;
    const WM_NCDESTROY: u32 = 0x0082;
    const WM_SIZE: u32 = 0x0005;
    const WM_SETCURSOR: u32 = 0x0020;
    struct Watcher {
        on: OnMove,
        on_size: Option<Box<dyn Fn(i32, i32)>>,
        last: Cell<Instant>,
    }

    fn report(hwnd: HWND, watcher: &Watcher, phase: &'static str, moving: Option<&RECT>) {
        let mut rect = RECT { left: 0, top: 0, right: 0, bottom: 0 };
        match moving {
            Some(r) => rect = *r, // where it is going, as the system says
            None => unsafe {
                GetWindowRect(hwnd, &mut rect);
            },
        }
        let mut at = POINT { x: 0, y: 0 };
        unsafe { GetCursorPos(&mut at) };
        let held = |key: u16| unsafe { GetKeyState(key as i32) } < 0;
        (watcher.on)(Move {
            phase,
            bounds: (rect.left, rect.top, rect.right - rect.left, rect.bottom - rect.top),
            cursor: (at.x, at.y),
            keys: (held(VK_CONTROL), held(VK_SHIFT), held(VK_MENU)),
        });
    }

    unsafe extern "system" fn subclass(hwnd: HWND, message: u32, wparam: WPARAM, lparam: LPARAM, _id: usize, data: usize) -> LRESULT {
        let watcher = data as *mut Watcher;
        match message {
            WM_ENTERSIZEMOVE => report(hwnd, unsafe { &*watcher }, "started", None),
            WM_EXITSIZEMOVE => report(hwnd, unsafe { &*watcher }, "ended", None),
            WM_MOVING | WM_SIZING => {
                let w = unsafe { &*watcher };
                if w.last.get().elapsed() >= Duration::from_millis(30) {
                    w.last.set(Instant::now());
                    report(hwnd, w, "moving", Some(unsafe { &*(lparam as *const RECT) }));
                }
            }
            // Over a child (the webview), the child says which cursor: the page's (a hand, an I-beam). Tao would set
            // its own arrow on every such question that comes up to it, over the page's.
            WM_SETCURSOR if wparam as HWND != hwnd => return 0,
            WM_SIZE => {
                if let Some(on_size) = unsafe { &(*watcher).on_size } {
                    on_size((lparam & 0xFFFF) as i32, ((lparam >> 16) & 0xFFFF) as i32);
                }
            }
            WM_NCDESTROY => drop(unsafe { Box::from_raw(watcher) }), // the window goes: its watcher with it
            _ => {}
        }
        unsafe { DefSubclassProc(hwnd, message, wparam, lparam) }
    }

    let watcher: *mut Watcher = Box::into_raw(Box::new(Watcher { on, on_size, last: Cell::new(Instant::now()) }));
    if unsafe { SetWindowSubclass(hwnd as HWND, Some(subclass), 0x5357_4153, watcher as usize) } == 0 {
        drop(unsafe { Box::from_raw(watcher) });
        log::debug!("moves of this window will not be reported");
    }
}

#[cfg(not(windows))]
pub fn watch_moves(_hwnd: isize, _on: OnMove) {}

#[cfg(not(windows))]
pub fn watch(_hwnd: isize, _on: OnMove, _on_size: Option<Box<dyn Fn(i32, i32)>>) {}
