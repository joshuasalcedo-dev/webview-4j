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
mod egui_windows;
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

// ---- immediate mode (egui): Java's frame function, and the widgets it draws with while it runs ----

/// Java's functions: `frame(window number)` for each frame, `nested(token)` inside a nested layout. Once.
#[unsafe(no_mangle)]
pub extern "C" fn wv_egui_callbacks(frame: egui_windows::FrameCallback, nested: egui_windows::FrameCallback) {
    egui_windows::set_callbacks(frame, nested);
}

/// The text Java passed: UTF-8 bytes, valid for the call.
fn ui_text<'a>(text: *const u8, length: usize) -> &'a str {
    if text.is_null() || length == 0 {
        return "";
    }
    // SAFETY: Java passes `length` bytes it holds for the call.
    std::str::from_utf8(unsafe { std::slice::from_raw_parts(text, length) }).unwrap_or("")
}

/// Runs a widget call, never letting a panic out to Java (it would end the process).
fn widget<R: Default>(f: impl FnOnce(&mut egui::Ui) -> R) -> R {
    panic::catch_unwind(AssertUnwindSafe(|| immediate::with_ui(f).unwrap_or_default())).unwrap_or_default()
}

#[unsafe(no_mangle)]
pub extern "C" fn wv_ui_heading(text: *const u8, length: usize) {
    let text = ui_text(text, length);
    widget(|ui| immediate::drew("heading", text, ui.heading(text).rect));
}

#[unsafe(no_mangle)]
pub extern "C" fn wv_ui_label(text: *const u8, length: usize) {
    let text = ui_text(text, length);
    widget(|ui| immediate::drew("label", text, ui.label(text).rect));
}

/// 1 on the frame it was clicked.
#[unsafe(no_mangle)]
pub extern "C" fn wv_ui_button(text: *const u8, length: usize) -> i32 {
    let text = ui_text(text, length);
    widget(|ui| {
        let r = ui.button(text);
        immediate::drew("button", text, r.rect);
        i32::from(r.clicked())
    })
}

/// Its state after this frame (1 checked).
#[unsafe(no_mangle)]
pub extern "C" fn wv_ui_checkbox(text: *const u8, length: usize, checked: i32) -> i32 {
    let text = ui_text(text, length);
    widget(|ui| {
        let mut on = checked != 0;
        let r = ui.checkbox(&mut on, text);
        immediate::drew("checkbox", text, r.rect);
        i32::from(on)
    })
}

/// Its value after this frame.
#[unsafe(no_mangle)]
pub extern "C" fn wv_ui_slider(text: *const u8, length: usize, value: f64, min: f64, max: f64) -> f64 {
    let text = ui_text(text, length);
    panic::catch_unwind(AssertUnwindSafe(|| {
        immediate::with_ui(|ui| {
            let mut v = value;
            let r = ui.add(egui::Slider::new(&mut v, min..=max).text(text));
            immediate::drew("slider", text, r.rect);
            v
        })
        .unwrap_or(value)
    }))
    .unwrap_or(value)
}

thread_local! {
    /// A text field's text after its frame, for Java to take (wv_ui_take_text).
    static EDITED: std::cell::RefCell<String> = const { std::cell::RefCell::new(String::new()) };
}

/// A one-line text field named `id`, holding `text`: its text's length in bytes if this frame changed it (take it
/// with wv_ui_take_text), -1 if not.
#[unsafe(no_mangle)]
pub extern "C" fn wv_ui_text_field(id: *const u8, id_length: usize, text: *const u8, length: usize) -> i64 {
    let name = ui_text(id, id_length);
    let mut edited = ui_text(text, length).to_string();
    panic::catch_unwind(AssertUnwindSafe(|| {
        immediate::with_ui(|ui| {
            let r = ui.add(egui::TextEdit::singleline(&mut edited).id_salt(name));
            immediate::drew("text-field", name, r.rect);
            if r.changed() {
                let length = edited.len() as i64;
                EDITED.with(|e| *e.borrow_mut() = std::mem::take(&mut edited));
                length
            } else {
                -1
            }
        })
        .unwrap_or(-1)
    }))
    .unwrap_or(-1)
}

/// Copies the changed text into `into` (`capacity` bytes); returns how many.
#[unsafe(no_mangle)]
pub extern "C" fn wv_ui_take_text(into: *mut u8, capacity: usize) -> usize {
    EDITED.with(|e| {
        let text = e.borrow();
        let n = text.len().min(capacity);
        // SAFETY: Java gives `capacity` bytes of its own to write.
        unsafe { std::ptr::copy_nonoverlapping(text.as_ptr(), into, n) };
        n
    })
}

#[unsafe(no_mangle)]
pub extern "C" fn wv_ui_separator() {
    widget(|ui| {
        ui.separator();
    });
}

#[unsafe(no_mangle)]
pub extern "C" fn wv_ui_space(points: f32) {
    widget(|ui| ui.add_space(points));
}

/// A text in a fixed-width font (code, logs).
#[unsafe(no_mangle)]
pub extern "C" fn wv_ui_monospace(text: *const u8, length: usize) {
    let text = ui_text(text, length);
    widget(|ui| immediate::drew("monospace", text, ui.monospace(text).rect));
}

fn scroll_area(id: &str, max_height: f32, stick: i32) -> egui::ScrollArea {
    egui::ScrollArea::vertical()
        .id_salt(id)
        .max_height(if max_height > 0.0 { max_height } else { f32::INFINITY })
        .stick_to_bottom(stick != 0)
        .auto_shrink([false, false])
}

/// A scroll area named `id`, at most `max_height` tall (0: what is left), sticking to its bottom as it grows when
/// `stick` (until the person scrolls away; back at the bottom, it sticks again): Java's closure `token` draws in it.
#[unsafe(no_mangle)]
pub extern "C" fn wv_ui_scroll(id: *const u8, id_length: usize, token: i64, max_height: f32, stick: i32) {
    let id = ui_text(id, id_length);
    widget(|ui| {
        let r = scroll_area(id, max_height, stick).show(ui, |inner| immediate::enter(inner, || egui_windows::nested(token)));
        follow_growth(ui, id, stick, r.content_size.y);
        immediate::drew("scroll", id, r.inner_rect);
    });
}

/// egui moves a sticking area to its new bottom on the frame after its content grew: when it changed size, one more
/// frame is asked for, or the end would show only at the next input.
fn follow_growth(ui: &egui::Ui, id: &str, stick: i32, height: f32) {
    if stick == 0 {
        return;
    }
    let key = ui.scope_id().with(("swaswa-scroll-height", id));
    let before = ui.ctx().data_mut(|d| {
        let b = d.get_temp::<f32>(key);
        d.insert_temp(key, height);
        b
    });
    if before.is_some_and(|b: f32| (b - height).abs() > 0.5) {
        ui.ctx().request_repaint();
    }
}

thread_local! {
    /// The rows a rows-area shows this frame: from, to (exclusive).
    static VISIBLE: std::cell::Cell<(usize, usize)> = const { std::cell::Cell::new((0, 0)) };
}

/// A scroll area of `count` rows `row_height` tall (0: a monospace line's), only those in view drawn: Java's closure
/// `token` draws rows wv_ui_visible_rows() says. Otherwise as wv_ui_scroll.
#[unsafe(no_mangle)]
pub extern "C" fn wv_ui_scroll_rows(id: *const u8, id_length: usize, token: i64, row_height: f32, count: i64, max_height: f32, stick: i32) {
    let id = ui_text(id, id_length);
    widget(|ui| {
        let height = if row_height > 0.0 { row_height } else { ui.text_style_height(&egui::TextStyle::Monospace) };
        let r = scroll_area(id, max_height, stick).show_rows(ui, height, count.max(0) as usize, |inner, rows| {
            VISIBLE.with(|v| v.set((rows.start, rows.end)));
            immediate::enter(inner, || egui_windows::nested(token));
        });
        follow_growth(ui, id, stick, r.content_size.y);
        immediate::drew("scroll", id, r.inner_rect);
    });
}

/// The rows in view in the rows-area being drawn: from in the high 32 bits, to (exclusive) in the low.
#[unsafe(no_mangle)]
pub extern "C" fn wv_ui_visible_rows() -> i64 {
    let (from, to) = VISIBLE.with(|v| v.get());
    ((from as i64) << 32) | (to as i64 & 0xFFFF_FFFF)
}

/// What the frame's UI does to its window: 0 start a drag (the system's move), 1 minimize, 2 maximize or restore,
/// 3 close (after the frame). 1 if done, 0 outside a frame.
#[unsafe(no_mangle)]
pub extern "C" fn wv_ui_window(action: i32) -> i32 {
    panic::catch_unwind(|| i32::from(egui_windows::window_action(action))).unwrap_or(0)
}

/// A band `height` tall across the width: dragging its empty parts moves the window (double-click: maximize or
/// restore); what Java's closure `token` draws in it (left to right) works as anywhere.
#[unsafe(no_mangle)]
pub extern "C" fn wv_ui_drag_region(token: i64, height: f32) {
    widget(|ui| {
        let rect = egui::Rect::from_min_size(ui.cursor().min, egui::vec2(ui.available_width(), height.max(1.0)));
        // the band first, below what is drawn in it: a press on a button is the button's, on the rest the band's
        let band = ui.interact(rect, ui.scope_id().with(("swaswa-drag-region", token)), egui::Sense::click_and_drag());
        ui.scope_builder(egui::UiBuilder::new().max_rect(rect).layout(egui::Layout::left_to_right(egui::Align::Center)), |row| {
            immediate::enter(row, || egui_windows::nested(token));
        });
        ui.advance_cursor_after_rect(rect);
        immediate::drew("drag-region", "", rect);
        if band.double_clicked() {
            egui_windows::window_action(2);
        } else if band.drag_started() {
            egui_windows::window_action(0);
        }
    });
}

/// A title bar of the window's own, drawn by egui: its title; drag it to move, double-click to maximize; minimize,
/// maximize (restore) and close buttons at its right.
#[unsafe(no_mangle)]
pub extern "C" fn wv_ui_title_bar(text: *const u8, length: usize) {
    let title = ui_text(text, length).to_string();
    widget(|ui| {
        let height = 32.0;
        let rect = egui::Rect::from_min_size(ui.cursor().min, egui::vec2(ui.available_width(), height));
        let band = ui.interact(rect, ui.scope_id().with("swaswa-title-bar"), egui::Sense::click_and_drag());
        ui.painter().rect_filled(rect, 0.0, ui.visuals().faint_bg_color);
        ui.scope_builder(egui::UiBuilder::new().max_rect(rect.shrink2(egui::vec2(8.0, 0.0))).layout(egui::Layout::left_to_right(egui::Align::Center)), |bar| {
            bar.label(egui::RichText::new(&title).strong());
            bar.with_layout(egui::Layout::right_to_left(egui::Align::Center), |buttons| {
                let close = buttons.add(egui::Button::new("✕").frame(false).min_size(egui::vec2(28.0, 24.0)));
                immediate::drew("button", "✕", close.rect);
                if close.clicked() {
                    egui_windows::window_action(3);
                }
                let max_label = if egui_windows::maximized() { "❐" } else { "☐" };
                let max = buttons.add(egui::Button::new(max_label).frame(false).min_size(egui::vec2(28.0, 24.0)));
                immediate::drew("button", "☐", max.rect);
                if max.clicked() {
                    egui_windows::window_action(2);
                }
                let min = buttons.add(egui::Button::new("—").frame(false).min_size(egui::vec2(28.0, 24.0)));
                immediate::drew("button", "—", min.rect);
                if min.clicked() {
                    egui_windows::window_action(1);
                }
            });
        });
        ui.advance_cursor_after_rect(rect);
        immediate::drew("title-bar", &title, rect);
        if band.double_clicked() {
            egui_windows::window_action(2);
        } else if band.drag_started() {
            egui_windows::window_action(0);
        }
    });
}

/// A row: Java's closure `token` draws in it, left to right.
#[unsafe(no_mangle)]
pub extern "C" fn wv_ui_horizontal(token: i64) {
    widget(|ui| {
        ui.horizontal(|row| immediate::enter(row, || egui_windows::nested(token)));
    });
}

/// A column: Java's closure `token` draws in it, top to bottom.
#[unsafe(no_mangle)]
pub extern "C" fn wv_ui_vertical(token: i64) {
    widget(|ui| {
        ui.vertical(|column| immediate::enter(column, || egui_windows::nested(token)));
    });
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
