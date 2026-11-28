//! Immediate-mode windows (egui, the `immediate` crate) on swaswa's event loop: opened, closed and given input by
//! messages (`app.egui.open`, `egui.<id>.close|input|drawn|repaint`), each frame run by Java (an upcall), whose
//! widget calls come back as the `wv_ui_*` functions while it runs.

use std::collections::HashMap;
use std::sync::Mutex;
use std::time::Instant;

use serde::Deserialize;
use serde_json::json;
use tao::event::WindowEvent;
use tao::event_loop::EventLoopWindowTarget;
use tao::window::{Window, WindowBuilder, WindowId};

use crate::frame::Message;
use crate::payload::{self, Outcome};

/// Java's frame function (the window's number) and its nested layouts' (a token for the closure it gave).
pub type FrameCallback = extern "C" fn(i64);

static FRAME: Mutex<Option<FrameCallback>> = Mutex::new(None);
static NESTED: Mutex<Option<FrameCallback>> = Mutex::new(None);

pub fn set_callbacks(frame: FrameCallback, nested: FrameCallback) {
    *FRAME.lock().unwrap() = Some(frame);
    *NESTED.lock().unwrap() = Some(nested);
}

/// Runs Java's closure `token`, inside a nested layout (its Ui the innermost).
pub fn nested(token: i64) {
    if let Some(callback) = *NESTED.lock().unwrap() {
        callback(token);
    }
}

pub struct EguiWindow {
    pub window: Window,
    /// The edge the pointer is on, for a frameless window's sizing ("" when none).
    pub edge: &'static str,
    pub surface: immediate::Surface,
    pub number: i64,
    pub frames: u64,
    pub drawn: Vec<immediate::Drawn>,
}

#[derive(Default)]
pub struct EguiWindows {
    pub windows: HashMap<String, EguiWindow>,
    pub by_window: HashMap<WindowId, String>,
    next: i64,
    /// The window just drawn (in its paint): a redraw asked now would be lost as the paint ends.
    just_drawn: Option<WindowId>,
}

#[derive(Deserialize)]
struct OpenBody {
    title: Option<String>,
    width: f64,
    height: f64,
    /// The system's frame and title bar (true unless false: the UI draws its own, `wv_ui_title_bar`).
    decorations: Option<bool>,
    x: Option<f64>,
    y: Option<f64>,
    /// A view whose window it is drawn in (an egui panel there, at x, y in its page): not a window of its own.
    parent: Option<String>,
}

/// One synthetic input: the pointer to a point, a button there, text typed, a key.
#[derive(Deserialize)]
struct InputBody {
    events: Vec<InputEvent>,
}

#[derive(Deserialize)]
#[serde(tag = "kind", rename_all = "lowercase")]
enum InputEvent {
    Move { x: f32, y: f32 },
    Down { x: f32, y: f32 },
    Up { x: f32, y: f32 },
    Click { x: f32, y: f32 },
    Text { text: String },
    Key { key: String, pressed: Option<bool> },
    Wheel { x: f32, y: f32 },
}

fn events(input: Vec<InputEvent>) -> Vec<egui::Event> {
    let button = |x: f32, y: f32, pressed: bool| egui::Event::PointerButton {
        pos: egui::pos2(x, y),
        button: egui::PointerButton::Primary,
        pressed,
        modifiers: Default::default(),
    };
    let mut out = Vec::new();
    for e in input {
        match e {
            InputEvent::Move { x, y } => out.push(egui::Event::PointerMoved(egui::pos2(x, y))),
            InputEvent::Down { x, y } => out.extend([egui::Event::PointerMoved(egui::pos2(x, y)), button(x, y, true)]),
            InputEvent::Up { x, y } => out.push(button(x, y, false)),
            InputEvent::Click { x, y } => out.extend([egui::Event::PointerMoved(egui::pos2(x, y)), button(x, y, true), button(x, y, false)]),
            InputEvent::Text { text } => out.push(egui::Event::Text(text)),
            InputEvent::Key { key, pressed } => {
                if let Some(k) = egui::Key::from_name(&key) {
                    let p = pressed.unwrap_or(true);
                    out.push(egui::Event::Key { key: k, physical_key: None, pressed: p, repeat: false, modifiers: Default::default() });
                    if pressed.is_none() {
                        out.push(egui::Event::Key { key: k, physical_key: None, pressed: false, repeat: false, modifiers: Default::default() });
                    }
                }
            }
            InputEvent::Wheel { x, y } => out.push(egui::Event::MouseWheel {
                unit: egui::MouseWheelUnit::Point,
                delta: egui::vec2(x, y),
                modifiers: Default::default(),
                phase: egui::TouchPhase::Move,
                source: egui::MouseWheelSource::Unknown,
            }),
        }
    }
    out
}

impl EguiWindows {
    /// `app.egui.open`: a window with egui in it; its first frame drawn at once.
    /// A window of egui's own, or with `parent` (a view's window's handle) a panel inside that window.
    pub fn open<T>(&mut self, target: &EventLoopWindowTarget<T>, payload: &[u8], parent: Option<isize>) -> Outcome {
        let body = match payload::json::<OpenBody>(payload) {
            Ok(body) => body,
            Err(error) => return error,
        };
        let embedded = parent.is_some() && body.parent.is_some();
        let mut builder = WindowBuilder::new()
            .with_decorations(!embedded && body.decorations.unwrap_or(true))
            .with_title(body.title.unwrap_or_else(|| "swaswa".into()))
            .with_inner_size(tao::dpi::LogicalSize::new(body.width, body.height));
        if let (Some(x), Some(y)) = (body.x, body.y) {
            builder = builder.with_position(tao::dpi::LogicalPosition::new(x, y));
        }
        #[cfg(windows)]
        if let Some(hwnd) = parent {
            use tao::platform::windows::WindowBuilderExtWindows;
            builder = builder.with_parent_window(hwnd as _);
        }
        #[cfg(not(windows))]
        if parent.is_some() {
            return Outcome::error(501, "an egui panel inside a window is on Windows for now")
                .hint("Open egui in a window of its own (Egui.open) on this system.");
        }
        let window = match builder.build(target) {
            Ok(window) => window,
            Err(e) => return Outcome::error(500, format!("a window could not be made: {e}")),
        };
        if embedded {
            // in its window's client area, where Java put it (logical pixels of the window), above the page
            let scale = window.scale_factor();
            let p = |v: f64| (v * scale).round() as i32;
            #[cfg(windows)]
            {
                use tao::platform::windows::WindowExtWindows;
                let hwnd = window.hwnd() as isize;
                crate::dock::place(hwnd, p(body.x.unwrap_or(0.0)), p(body.y.unwrap_or(0.0)), p(body.width), p(body.height));
                crate::dock::clip_siblings(hwnd);
                crate::dock::to_front(hwnd);
            }
        }
        let surface = match immediate::Surface::new(&window) {
            Ok(surface) => surface,
            Err(e) => {
                return Outcome::error(501, format!("egui could not draw in it: {e}"))
                    .hint("egui draws with OpenGL: a graphics driver with OpenGL 2 or later (any GPU from the last ten years) is needed.");
            }
        };
        self.next += 1;
        let number = self.next;
        let id = format!("eg-{number}");
        window.request_redraw();
        self.by_window.insert(window.id(), id.clone());
        self.windows.insert(id.clone(), EguiWindow { window, edge: "", surface, number, frames: 0, drawn: Vec::new() });
        Outcome::json(&json!({"window": id}))
    }

    /// `egui.<id>.<action>`.
    pub fn on_message(&mut self, id: &str, rest: &[&str], payload: &[u8]) -> Outcome {
        if rest == ["close"] {
            return if self.close(id) { Outcome::ok() } else { gone(id) };
        }
        let Some(w) = self.windows.get_mut(id) else { return gone(id) };
        match rest {
            ["input"] => match payload::json::<InputBody>(payload) {
                Ok(body) => {
                    w.surface.inject(events(body.events));
                    w.window.request_redraw();
                    Outcome::ok()
                }
                Err(error) => error,
            },
            ["drawn"] => Outcome::json(&json!({
                "frames": w.frames,
                "widgets": w.drawn.iter().map(|d| json!({"kind": d.kind, "label": d.label, "x": d.rect[0], "y": d.rect[1], "width": d.rect[2], "height": d.rect[3]})).collect::<Vec<_>>(),
            })),
            ["repaint"] => {
                w.window.request_redraw();
                Outcome::ok()
            }
            ["place"] => {
                // a panel moved or sized in its window (logical pixels); none (0 x 0): hidden
                #[derive(Deserialize)]
                struct Place {
                    x: f64,
                    y: f64,
                    width: f64,
                    height: f64,
                }
                match payload::json::<Place>(payload) {
                    Ok(at) => {
                        if at.width < 1.0 || at.height < 1.0 {
                            w.window.set_visible(false);
                        } else {
                            let scale = w.window.scale_factor();
                            let p = |v: f64| (v * scale).round() as i32;
                            #[cfg(windows)]
                            {
                                use tao::platform::windows::WindowExtWindows;
                                let hwnd = w.window.hwnd() as isize;
                                crate::dock::place(hwnd, p(at.x), p(at.y), p(at.width), p(at.height));
                                crate::dock::clip_siblings(hwnd);
                crate::dock::to_front(hwnd);
                            }
                            #[cfg(not(windows))]
                            {
                                w.window.set_outer_position(tao::dpi::LogicalPosition::new(at.x, at.y));
                                w.window.set_inner_size(tao::dpi::LogicalSize::new(at.width, at.height));
                            }
                            w.window.set_visible(true);
                        }
                        w.window.request_redraw();
                        Outcome::ok()
                    }
                    Err(error) => error,
                }
            }
            ["window"] => {
                #[derive(Deserialize)]
                struct Body {
                    decorations: Option<bool>,
                }
                match payload::json::<Body>(payload) {
                    Ok(body) => {
                        if let Some(on) = body.decorations {
                            w.window.set_decorations(on);
                        }
                        w.window.request_redraw();
                        Outcome::ok()
                    }
                    Err(error) => error,
                }
            }
            ["state"] => Outcome::json(&json!({
                "minimized": w.window.is_minimized(),
                "maximized": w.window.is_maximized(),
                "decorated": w.window.is_decorated(),
            })),
            _ => Outcome::error(404, format!("an egui window has no '{}'", rest.join("."))),
        }
    }

    pub fn close(&mut self, id: &str) -> bool {
        let Some(mut w) = self.windows.remove(id) else { return false };
        self.by_window.remove(&w.window.id());
        w.surface.destroy();
        drop(w);
        crate::runtime::emit_message(&Message::new(format!("egui.{id}.closed")));
        true
    }

    /// The window's event: true if it was an egui window's.
    pub fn on_window(&mut self, window_id: WindowId, event: &WindowEvent) -> bool {
        let Some(id) = self.by_window.get(&window_id).cloned() else { return false };
        if matches!(event, WindowEvent::CloseRequested) {
            self.close(&id);
            return true;
        }
        if let Some(w) = self.windows.get_mut(&id) {
            if !w.window.is_decorated() && w.window.is_resizable() && !w.window.is_maximized() {
                // no frame to grab: its outer few pixels size it, as a frame's would
                if let WindowEvent::CursorMoved { position, .. } = event {
                    let edge = edge_at(&w.window, position.x, position.y);
                    w.edge = edge;
                    if let Some(cursor) = edge_cursor(edge) {
                        w.window.set_cursor_icon(cursor);
                        return true; // not egui's: the edge's
                    }
                }
                if let WindowEvent::MouseInput { state: tao::event::ElementState::Pressed, button: tao::event::MouseButton::Left, .. } = event {
                    if let Some(direction) = crate::runtime::resize_direction(w.edge) {
                        crate::runtime::start_resize(&w.window, direction);
                        return true;
                    }
                }
            }
            if w.surface.on_event(&w.window, event) {
                w.window.request_redraw();
            }
        }
        true
    }

    /// The window wants drawing: a frame, Java's frame function drawing its UI.
    pub fn redraw(&mut self, window_id: WindowId) -> bool {
        let Some(id) = self.by_window.get(&window_id).cloned() else { return false };
        let Some(w) = self.windows.get_mut(&id) else { return true };
        let number = w.number;
        let callback = *FRAME.lock().unwrap();
        CURRENT.with(|c| c.set(&w.window as *const Window));
        let result = w.surface.frame(&w.window, |ui| {
            if let Some(callback) = callback {
                immediate::enter(ui, || callback(number));
            }
        });
        CURRENT.with(|c| c.set(std::ptr::null()));
        let close = CLOSE.with(|c| c.replace(false));
        w.drawn = immediate::take_drawn();
        w.frames += 1;
        self.just_drawn = Some(window_id);
        if close {
            // asked for by its own close button: after its frame, not in it
            self.close(&id);
        }
        if let Err(e) = result {
            log::warn!("an egui frame could not be drawn: {e}");
        }
        true
    }

    /// Those whose next frame is due: drawn; the soonest one still to come (to wait until).
    pub fn due(&mut self) -> Option<Instant> {
        let now = Instant::now();
        let mut soonest: Option<Instant> = None;
        let just = self.just_drawn.take();
        for w in self.windows.values_mut() {
            if let Some(at) = w.surface.next {
                if at <= now && just == Some(w.window.id()) {
                    // asked in its own paint: a moment later, when the paint is over (else Windows drops it)
                    let later = now + std::time::Duration::from_millis(1);
                    w.surface.next = Some(later);
                    soonest = Some(soonest.map_or(later, |s| s.min(later)));
                } else if at <= now {
                    w.surface.next = None;
                    w.window.request_redraw();
                } else {
                    soonest = Some(soonest.map_or(at, |s| s.min(at)));
                }
            }
        }
        soonest
    }

    pub fn clear(&mut self) {
        let ids: Vec<String> = self.windows.keys().cloned().collect();
        for id in ids {
            self.close(&id);
        }
    }
}

thread_local! {
    /// The window whose frame is being drawn: what its title bar's buttons and drag act on.
    static CURRENT: std::cell::Cell<*const Window> = const { std::cell::Cell::new(std::ptr::null()) };
    /// Its close button was pressed: closed once its frame is over.
    static CLOSE: std::cell::Cell<bool> = const { std::cell::Cell::new(false) };
}

/// What a frame's UI does to its window: 0 drag it (the system's move, as a title bar's), 1 minimize, 2 maximize or
/// restore, 3 close (after the frame). False outside a frame.
pub fn window_action(action: i32) -> bool {
    let window = CURRENT.with(|c| c.get());
    if window.is_null() {
        return false;
    }
    // SAFETY: set for the length of the window's frame, on this thread; the window lives through it.
    let window = unsafe { &*window };
    match action {
        0 => {
            let _ = window.drag_window();
        }
        1 => window.set_minimized(true),
        2 => window.set_maximized(!window.is_maximized()),
        3 => CLOSE.with(|c| c.set(true)),
        _ => return false,
    }
    true
}

/// Whether the window being drawn is maximized (the title bar's restore button).
pub fn maximized() -> bool {
    let window = CURRENT.with(|c| c.get());
    !window.is_null() && unsafe { &*window }.is_maximized()
}

const EDGE: f64 = 6.0;

fn edge_at(window: &Window, x: f64, y: f64) -> &'static str {
    let size = window.inner_size();
    let edge = EDGE * window.scale_factor();
    let (w, h) = (size.width as f64, size.height as f64);
    let north = y < edge;
    let south = y >= h - edge;
    let west = x < edge;
    let east = x >= w - edge;
    match (north, south, west, east) {
        (true, _, true, _) => "nw",
        (true, _, _, true) => "ne",
        (_, true, true, _) => "sw",
        (_, true, _, true) => "se",
        (true, ..) => "n",
        (_, true, ..) => "s",
        (_, _, true, _) => "w",
        (_, _, _, true) => "e",
        _ => "",
    }
}

fn edge_cursor(edge: &str) -> Option<tao::window::CursorIcon> {
    use tao::window::CursorIcon as C;
    Some(match edge {
        "n" | "s" => C::NsResize,
        "e" | "w" => C::EwResize,
        "ne" | "sw" => C::NeswResize,
        "nw" | "se" => C::NwseResize,
        _ => return None,
    })
}

fn gone(id: &str) -> Outcome {
    Outcome::error(404, format!("no egui window '{id}': it was closed"))
        .kind("view-closed")
        .hint("The window was closed (by the person, or by close()): wait for whenClosed() to stop using it.")
}
