//! The event loop and everything done on it. Tao and Wry objects may only be touched on the
//! thread running the loop, so [`send`] (any thread) hands each message to it through an
//! [`EventLoopProxy`]; [`run`] blocks its thread until `app.quit`.
//!
//! Messages in are routed by subject, messages out go to the callback [`run`] was given, all as
//! frames (PROTOCOL.md). What each subject means is in PROTOCOL.md §2.

use std::collections::HashMap;
use std::path::Path;
use std::rc::Rc;
use std::sync::{Arc, Mutex};

use serde_json::{Value, json};
use tao::dpi::{LogicalPosition, LogicalSize, PhysicalPosition, PhysicalSize};
use tao::event::{Event, StartCause, WindowEvent};
use tao::event_loop::{ControlFlow, EventLoopBuilder, EventLoopProxy, EventLoopWindowTarget};
use tao::platform::run_return::EventLoopExtRunReturn;
use tao::window::{Fullscreen, ResizeDirection, Window, WindowBuilder, WindowId};
use wry::{WebContext, WebView, WebViewBuilder};

use crate::bridge;
use crate::frame::Message;
use crate::inspector::{self, Capture};
use crate::payload::{
    self, Backdrop, BoundsBody, CaptureBody, CookieBody, CookieRecord, FileDialogBody, Geometry, MessageDialogBody, NewWindows, OpenRequest,
    Outcome, PlaceBody, PositionBody, ProgressBody, SizeBody, StateBody, ThemeChoice, WebViewInfo, WindowBody,
};

/// Receives each message as a frame (`frame`, `length` bytes), valid only during the call. Mostly
/// called on the event loop thread (log records may come from any thread): it must return quickly.
pub type MessageCallback = extern "C" fn(*const u8, usize);

/// The protocol version, sent on `app.ready`.
pub const PROTOCOL: &str = "1";

/// What reaches the event loop: a protocol message, or (internal, never from Java) a script that
/// refuses a page's call.
pub enum ToLoop {
    Message(Message),
    Refuse { view: String, script: String },
    /// The page asked its own window to move, minimize, maximize or close (bridge.js `window`).
    Window { view: String, action: String },
    /// The page drew a canvas's picture `seq` (or could not: why).
    Drawn { view: String, canvas: String, seq: u64, error: Option<String> },
}

/// Set while the loop runs: how other threads reach it.
static PROXY: Mutex<Option<EventLoopProxy<ToLoop>>> = Mutex::new(None);

/// Set while the loop runs: where messages go.
static ON_MESSAGE: Mutex<Option<MessageCallback>> = Mutex::new(None);

/// Sends a message to the callback [`run`] was given; dropped when no loop runs.
pub(crate) fn emit(message: &Message) {
    let callback = *ON_MESSAGE.lock().unwrap();
    if let Some(callback) = callback {
        let frame = message.encode();
        callback(frame.as_ptr(), frame.len());
    }
}

/// Why [`send`] did not take a message.
#[derive(Debug, PartialEq, Eq)]
pub enum NotSent {
    NotRunning,
}

/// Hands a message to the event loop and returns at once. Callable from any thread.
pub fn send(message: Message) -> Result<(), NotSent> {
    let proxy = PROXY.lock().unwrap().clone().ok_or(NotSent::NotRunning)?;
    proxy.send_event(ToLoop::Message(message)).map_err(|_| NotSent::NotRunning)
}

struct Instance {
    webview: WebView,
    /// Its own window, or for an embedded view the window it is embedded in (its parent's).
    window: Rc<Window>,
    /// Set for an embedded view: the view whose window it sits in.
    parent: Option<String>,
    /// Set for an owned window: the view whose window owns it (it closes first).
    owner: Option<String>,
    title: String,
    capture: Arc<Capture>,
    link: Arc<Mutex<bridge::Link>>,
    /// The number of the last picture Java sent this view's canvases.
    next_frame: u64,
    /// A window's view moved into another window (adopted): the window of ours it fills there, which is what moves.
    dock: Option<isize>,
    /// An embedded view torn off into a window of its own: sized with that window.
    fills: bool,
    /// Made as an embedded view (Wry's child webview): sized by its bounds, never by what it is in, wherever it goes.
    child_webview: bool,
    /// An embedded view a person moves and sizes inside its window (an MDI child), raised when clicked.
    floating: bool,
    /// For each canvas, the picture Java waits to see drawn, and where its reply goes.
    frame_replies: HashMap<String, (u64, ReplyTo)>,
    /// Small pushes waiting to go out as one script: at the end of this round of the event loop,
    /// before the next call answer, or once they add up to PUSH_BATCH.
    pushes: Vec<String>,
    pushes_size: usize,
    /// The close button asks Java (`close-requested`) instead of closing.
    confirm_close: bool,
    /// Opened with devtools: its developer tools can be opened (F12, or view.<view>.devtools).
    devtools: bool,
    /// The last size and position the window reported this round, sent once at its end: a drag
    /// reports dozens a second.
    resized: Option<(f64, f64)>,
    moved: Option<(f64, f64)>,
    /// Minimized, maximized, fullscreen, as last reported: a change goes out as `view.<view>.window-state`.
    shown: (bool, bool, bool),
    /// Escape, unused by the page, takes the window out of fullscreen.
    escape_exits_fullscreen: bool,
    /// Whether it is the active window, as last reported (`view.<view>.focused` goes out on a change).
    focused: bool,
    // Keep the context alive until after its webview has been dropped.
    context: Option<Box<WebContext>>,
}

/// Small pushes that have built up go out at this size, whatever else is going on.
const PUSH_BATCH: usize = 1024 * 1024;

impl Instance {
    /// Runs the waiting pushes in the page, in the order they came, as one script.
    fn flush_pushes(&mut self) {
        if self.pushes.is_empty() {
            return;
        }
        let script = self.pushes.join("\n");
        self.pushes.clear();
        self.pushes_size = 0;
        settle(self, &script);
    }

    /// The size and position this round left the window at, if they changed.
    /// Whether it is the active window, once a round's messages are handled: a change goes out.
    fn flush_focus(&mut self, id: &str) {
        if self.parent.is_none() {
            let now = active(&self.window);
            self.set_focused(id, now);
        }
    }

    fn set_focused(&mut self, id: &str, focused: bool) {
        if self.focused != focused {
            self.focused = focused;
            emit(&Message::new(format!("view.{id}.focused")).payload(json!({"focused": focused}).to_string()));
        }
    }

    fn flush_geometry(&mut self, id: &str) {
        if let Some((width, height)) = self.resized.take() {
            emit(&Message::new(format!("view.{id}.resized")).payload(json!({"width": width, "height": height}).to_string()));
        }
        if let Some((x, y)) = self.moved.take() {
            emit(&Message::new(format!("view.{id}.moved")).payload(json!({"x": x, "y": y}).to_string()));
        }
    }
}

impl Drop for Instance {
    /// However a window goes (close, the user, app.quit), the page's waiting requests are answered
    /// while the webview still exists.
    fn drop(&mut self) {
        self.link.lock().unwrap().close();
        // Java's pictures still on their way: never drawn now; their futures fail instead of waiting forever.
        for (canvas, (_, reply)) in self.frame_replies.drain() {
            reply.send(Outcome::error(410, format!("the view closed before '{canvas}' was drawn"))
                .hint("The window closed while a picture was on its way: stop drawing on it (an Animation stops by itself)."));
        }
    }
}

#[derive(Default)]
struct State {
    views: HashMap<String, Instance>,
    ids_by_window: HashMap<WindowId, String>,
    next_id: u64,
    /// `app.warm`: a hidden webview that keeps the browser engine started (on Windows, WebView2's browser
    /// process), so the first real window opens in ~100 ms instead of ~450. Dropped once a window is open.
    warm: Option<Instance>,
    /// When to start it: a moment after `app.warm`, unless a window opened by then.
    warm_at: Option<std::time::Instant>,
    /// The browser profile it starts on: the windows' own (`app.warm`'s `dataDirectory`), or it would start an
    /// engine no window uses.
    warm_profile: Option<std::path::PathBuf>,
}

/// Starts the browser engine in a hidden 1×1 view that is then taken out of the views: not listed, nothing sent
/// about its window. It is made the way every window is (open_webview), so the engine starts with the same options
/// (WebView2 refuses a second environment on one profile with other options, such as other custom protocols).
/// Once a real window is open the engine stays started with it, and this one goes.
fn warm(target: &EventLoopWindowTarget<ToLoop>, state: &mut State) -> Outcome {
    if state.warm.is_some() || state.views.values().any(|v| v.parent.is_none()) {
        return Outcome::ok(); // started already
    }
    let mut open = match payload::json::<OpenRequest>(br#"{"title":"webview-4j","headless":true,"width":1,"height":1}"#) {
        Ok(open) => open,
        Err(error) => return error,
    };
    open.data_directory = state.warm_profile.clone();
    let opened = open_webview(open, target, state);
    let id = format!("wv-{}", state.next_id);
    match state.views.remove(&id) {
        Some(instance) => {
            state.ids_by_window.remove(&instance.window.id());
            state.warm = Some(instance);
            Outcome::ok()
        }
        None => opened, // it failed: why
    }
}

/// Runs the event loop on this thread until `app.quit`; returns its exit code. On macOS this must
/// be the process's first thread. Sends `app.ready` once messages can be sent.
pub fn run(on_message: Option<MessageCallback>) -> Result<i32, String> {
    let mut proxy = PROXY.lock().unwrap();
    if proxy.is_some() {
        return Err("the event loop is already running".into());
    }
    let mut builder = EventLoopBuilder::<ToLoop>::with_user_event();
    any_thread(&mut builder);
    let mut event_loop = builder.build();
    *proxy = Some(event_loop.create_proxy());
    *ON_MESSAGE.lock().unwrap() = on_message;
    drop(proxy);
    crate::logging::install();

    let _running = Running;
    let mut state = State::default();
    Ok(event_loop.run_return(move |event, target, control_flow| {
        *control_flow = ControlFlow::Wait;
        match event {
            Event::NewEvents(StartCause::Init) => emit(&Message::new("app.ready").header("Protocol", PROTOCOL)),
            Event::UserEvent(ToLoop::Message(message)) => handle(message, target, &mut state, control_flow),
            Event::UserEvent(ToLoop::Refuse { view, script }) => {
                // A refusal says nothing but "refused": it goes to whatever page asked.
                if let Some(instance) = state.views.get(&view) {
                    if let Err(error) = instance.webview.evaluate_script(&script) {
                        log::warn!("could not answer the page: {error}");
                    }
                }
            }
            Event::UserEvent(ToLoop::Window { view, action }) => page_window_action(&view, &action, &mut state),
            Event::UserEvent(ToLoop::Drawn { view, canvas, seq, error }) => {
                // Java's drawFrame completes once its picture, or a later one, is on the canvas.
                if let Some(instance) = state.views.get_mut(&view) {
                    if instance.frame_replies.get(&canvas).is_some_and(|(waiting, _)| *waiting <= seq) {
                        let (_, reply) = instance.frame_replies.remove(&canvas).expect("there");
                        reply.send(match error {
                            None => Outcome::json(&json!({"drawn": true, "seq": seq})),
                            Some(why) if why.contains("no <canvas") => Outcome::error(404, why).kind("page").hint(format!(
                                "Put <canvas id=\"{canvas}\"></canvas> in the page before drawing on it (a 2d canvas), or draw on a canvas it has."
                            )),
                            Some(why) => Outcome::error(422, why).kind("page").hint(
                                "The page could not draw the picture: check a PNG or JPEG is whole and the canvas is a 2d one (not WebGL).",
                            ),
                        });
                    }
                }
            }
            Event::WindowEvent { window_id, event, .. } => on_window(window_id, event, &mut state),
            // This round's messages are handled: the pushes they made go out, one script per page,
            // and each window's last size and position.
            Event::MainEventsCleared => {
                for (id, instance) in state.views.iter_mut() {
                    instance.flush_pushes();
                    instance.flush_geometry(id);
                    instance.flush_focus(id);
                }
            }
            _ => {}
        }
        // app.warm: once the app has asked for no window for a moment, the engine starts (an app that opens one at
        // once starts it with that window, not twice).
        if let Some(at) = state.warm_at {
            if std::time::Instant::now() >= at {
                state.warm_at = None;
                let outcome = warm(target, &mut state);
                if outcome.status >= 400 {
                    log::debug!("the browser engine did not start early ({})", outcome.status);
                }
            } else if *control_flow == ControlFlow::Wait {
                *control_flow = ControlFlow::WaitUntil(at);
            }
        }
    }))
}

/// Closes a view, every view embedded in it and every window it owns (theirs first, while its window still
/// exists), each with a `view.<id>.closed`. False if there is no such view.
fn close_view(state: &mut State, id: &str) -> bool {
    // A recording of it is finished as it is, not lost.
    crate::capture::closed(id);
    let children: Vec<String> = state
        .views
        .iter()
        .filter(|(_, v)| v.parent.as_deref() == Some(id) || v.owner.as_deref() == Some(id))
        .map(|(child, _)| child.clone())
        .collect();
    for child in children {
        close_view(state, &child);
    }
    let Some(instance) = state.views.remove(id) else {
        return false;
    };
    if instance.parent.is_none() {
        state.ids_by_window.remove(&instance.window.id());
    }
    let dock = instance.dock;
    drop(instance); // the webview, then (its last reference) the window: dropping the window closes it
    #[cfg(windows)]
    if let Some(dock) = dock {
        crate::dock::destroy(dock);
    }
    #[cfg(not(windows))]
    let _ = dock;
    crate::cdp::forget(id);
    emit(&Message::new(format!("view.{id}.closed")));
    true
}

/// What a window reports: closing, its size and position, focus.
fn on_window(window_id: WindowId, event: WindowEvent, state: &mut State) {
    let Some(id) = state.ids_by_window.get(&window_id).cloned() else {
        return;
    };
    let Some(instance) = state.views.get_mut(&id) else {
        return;
    };
    match event {
        WindowEvent::CloseRequested if instance.confirm_close => {
            emit(&Message::new(format!("view.{id}.close-requested")));
        }
        WindowEvent::CloseRequested => {
            close_view(state, &id);
        }
        WindowEvent::Resized(size) => {
            let size = size.to_logical::<f64>(instance.window.scale_factor());
            if instance.fills {
                let _ = instance.webview.set_bounds(wry::Rect {
                    position: tao::dpi::LogicalPosition::new(0.0, 0.0).into(),
                    size: tao::dpi::LogicalSize::new(size.width, size.height).into(),
                });
            }
            instance.resized = Some((size.width, size.height));
            // minimized, maximized and fullscreen change with a resize: reported when they do
            let window = &instance.window;
            let shown = (window.is_minimized(), window.is_maximized(), window.fullscreen().is_some());
            if shown != instance.shown {
                instance.shown = shown;
                let (minimized, maximized, fullscreen) = shown;
                emit(&Message::new(format!("view.{id}.window-state"))
                    .payload(json!({"minimized": minimized, "maximized": maximized, "fullscreen": fullscreen}).to_string()));
            }
        }
        WindowEvent::ScaleFactorChanged { scale_factor, .. } => {
            emit(&Message::new(format!("view.{id}.scale")).payload(json!({"scale": scale_factor}).to_string()));
        }
        WindowEvent::ThemeChanged(theme) => {
            let theme = if theme == tao::window::Theme::Dark { "dark" } else { "light" };
            emit(&Message::new(format!("view.{id}.system-theme")).payload(json!({"theme": theme}).to_string()));
        }
        WindowEvent::Moved(position) => {
            let position = position.to_logical::<f64>(instance.window.scale_factor());
            instance.moved = Some((position.x, position.y));
        }
        WindowEvent::Focused(true) => instance.set_focused(&id, true),
        // Windows says "unfocused" both when the window stops being the active one and when its own webview takes
        // the keyboard: which it was is known once the switch is over, at the end of the round (flush_focus).
        WindowEvent::Focused(false) => {}
        _ => {}
    }
}

/// What a page asked of its own window. Closing goes the way the close button does: with
/// `confirmClose`, it asks Java first.
fn page_window_action(view: &str, action: &str, state: &mut State) {
    let Some(instance) = state.views.get(view) else {
        return;
    };
    if instance.parent.is_some() {
        // a floating one (an MDI child) moves, sizes and raises itself inside its window; the others do nothing
        #[cfg(windows)]
        if instance.floating {
            let frame = frame_window(instance);
            match action {
                "drag" => crate::dock::start_move(frame),
                "front" => {
                    raise(state, view);
                }
                resize if resize.starts_with("resize-") => crate::dock::start_size(frame, &resize["resize-".len()..]),
                _ => {}
            }
        }
        return;
    }
    let window = &instance.window;
    match action {
        "drag" => {
            if let Err(error) = window.drag_window() {
                log::debug!("the window could not be dragged: {error}");
            }
        }
        "minimize" => window.set_minimized(true),
        // Escape the page did not use: out of fullscreen, as a browser does (a kiosk keeps it)
        "escape" => {
            log::debug!("escape: fullscreen {}, may leave it {}", window.fullscreen().is_some(), instance.escape_exits_fullscreen);
            if instance.escape_exits_fullscreen && window.fullscreen().is_some() {
                window.set_fullscreen(None);
            }
        }
        "toggle-maximize" => window.set_maximized(!window.is_maximized()),
        resize if resize.starts_with("resize-") => {
            // A window without the system's frame, resized from its edge: not while maximized.
            if let Some(direction) = resize_direction(&resize["resize-".len()..]) {
                if window.is_resizable() && !window.is_maximized() {
                    start_resize(window, direction);
                }
            }
        }
        "close" => {
            let id = window.id();
            on_window(id, WindowEvent::CloseRequested, state);
        }
        _ => log::debug!("a page asked its window for '{action}', which there is not"),
    }
}

/// Windows: hands the mouse to the system's own sizing from that edge, as if the window had a frame there. tao's
/// drag_resize_window posts the cursor's position as a pointer to a value on its stack instead of the position itself,
/// so the sizing starts from a wrong point and the left and top edges do not move; this posts the position.
#[cfg(target_os = "windows")]
fn start_resize(window: &Window, direction: ResizeDirection) {
    use tao::platform::windows::WindowExtWindows;
    use windows_sys::Win32::Foundation::POINT;
    use windows_sys::Win32::UI::Input::KeyboardAndMouse::ReleaseCapture;
    use windows_sys::Win32::UI::WindowsAndMessaging::{
        GetCursorPos, HTBOTTOM, HTBOTTOMLEFT, HTBOTTOMRIGHT, HTLEFT, HTRIGHT, HTTOP, HTTOPLEFT, HTTOPRIGHT, PostMessageW,
        WM_NCLBUTTONDOWN,
    };
    let hit = match direction {
        ResizeDirection::North => HTTOP,
        ResizeDirection::South => HTBOTTOM,
        ResizeDirection::East => HTRIGHT,
        ResizeDirection::West => HTLEFT,
        ResizeDirection::NorthEast => HTTOPRIGHT,
        ResizeDirection::NorthWest => HTTOPLEFT,
        ResizeDirection::SouthEast => HTBOTTOMRIGHT,
        ResizeDirection::SouthWest => HTBOTTOMLEFT,
    };
    let mut cursor = POINT { x: 0, y: 0 };
    unsafe {
        if GetCursorPos(&mut cursor) == 0 {
            return;
        }
        // MAKELPARAM(x, y): screen coordinates, each 16 bits (negative on a monitor left of or above the main one).
        let position = ((cursor.y as u16 as u32) << 16 | (cursor.x as u16 as u32)) as isize;
        ReleaseCapture();
        PostMessageW(window.hwnd() as _, WM_NCLBUTTONDOWN, hit as usize, position);
    }
}

#[cfg(not(target_os = "windows"))]
fn start_resize(window: &Window, direction: ResizeDirection) {
    if let Err(error) = window.drag_resize_window(direction) {
        log::debug!("the window could not be resized: {error}");
    }
}

fn resize_direction(edge: &str) -> Option<ResizeDirection> {
    Some(match edge {
        "n" => ResizeDirection::North,
        "s" => ResizeDirection::South,
        "e" => ResizeDirection::East,
        "w" => ResizeDirection::West,
        "ne" => ResizeDirection::NorthEast,
        "nw" => ResizeDirection::NorthWest,
        "se" => ResizeDirection::SouthEast,
        "sw" => ResizeDirection::SouthWest,
        _ => return None,
    })
}

fn window_theme(theme: ThemeChoice) -> Option<tao::window::Theme> {
    match theme {
        ThemeChoice::System => None,
        ThemeChoice::Light => Some(tao::window::Theme::Light),
        ThemeChoice::Dark => Some(tao::window::Theme::Dark),
    }
}

fn webview_theme(theme: ThemeChoice) -> wry::Theme {
    match theme {
        ThemeChoice::System => wry::Theme::Auto,
        ThemeChoice::Light => wry::Theme::Light,
        ThemeChoice::Dark => wry::Theme::Dark,
    }
}

/// The page's `prefers-color-scheme`. WebView2 is told; WebKit follows the window's appearance by itself.
#[cfg(target_os = "windows")]
fn page_theme(webview: &WebView, theme: ThemeChoice) -> Outcome {
    use wry::WebViewExtWindows;
    result(webview.set_theme(webview_theme(theme)))
}

#[cfg(not(target_os = "windows"))]
fn page_theme(_webview: &WebView, theme: ThemeChoice) -> Outcome {
    let _ = webview_theme(theme);
    Outcome::ok()
}

#[cfg(target_os = "windows")]
fn with_page_theme(builder: WebViewBuilder, theme: ThemeChoice) -> WebViewBuilder {
    use wry::WebViewBuilderExtWindows;
    builder.with_theme(webview_theme(theme))
}

#[cfg(not(target_os = "windows"))]
fn with_page_theme(builder: WebViewBuilder, _theme: ThemeChoice) -> WebViewBuilder {
    builder
}

/// Clears the proxy and the callback once the loop has returned, even if it unwinds.
struct Running;

impl Drop for Running {
    fn drop(&mut self) {
        *PROXY.lock().unwrap() = None;
        *ON_MESSAGE.lock().unwrap() = None;
    }
}

/// Where a message's reply goes: its `Reply-To`, if it has one.
#[derive(Clone)]
struct ReplyTo(Option<String>);

impl ReplyTo {
    fn send(&self, outcome: Outcome) {
        if let Some(subject) = &self.0 {
            emit(&outcome.to(subject));
        }
    }
}

fn handle(message: Message, target: &EventLoopWindowTarget<ToLoop>, state: &mut State, control_flow: &mut ControlFlow) {
    let reply = ReplyTo(message.get("Reply-To").map(str::to_owned));
    let subject = message.subject.clone();
    let tokens: Vec<&str> = subject.split('.').collect();
    let outcome = match tokens.as_slice() {
        ["app", "open"] => match payload::json::<OpenRequest>(&message.payload) {
            Ok(open) => open_webview(open, target, state),
            Err(error) => error,
        },
        ["app", "list"] => Outcome::json(&list(state)),
        // where the pointer is: physical pixels, and the scale of the monitor it is on (its logical place too)
        ["app", "cursor"] => match target.cursor_position() {
            Ok(at) => {
                let scale = target
                    .available_monitors()
                    .find(|m| {
                        let (p, s) = (m.position(), m.size());
                        at.x >= p.x as f64 && at.y >= p.y as f64 && at.x < (p.x + s.width as i32) as f64 && at.y < (p.y + s.height as i32) as f64
                    })
                    .map(|m| m.scale_factor())
                    .unwrap_or(1.0);
                Outcome::json(&json!({"x": at.x / scale, "y": at.y / scale, "physicalX": at.x, "physicalY": at.y, "scale": scale}))
            }
            Err(e) => Outcome::error(501, format!("the pointer's place is not known here: {e}")),
        },
        // which of the app's windows is at a screen point (physical pixels), as the system has them stacked
        ["app", "window-at"] => {
            #[derive(serde::Deserialize)]
            struct At {
                x: f64,
                y: f64,
                /// A view to look past (the window being dragged): the first of the app's windows below it there.
                except: Option<String>,
            }
            match payload::json::<At>(&message.payload) {
                Ok(at) => {
                    let below = at.except.as_ref().and_then(|e| state.views.get(e)).filter(|v| v.parent.is_none()).map(|v| window_handle(&v.window));
                    let ours: Vec<isize> = state.views.values().filter(|v| v.parent.is_none()).map(|v| window_handle(&v.window)).collect();
                    let hwnd = match below {
                        Some(except) => {
                            // the first window below it that is the app's (others' windows above ours count: they hide it)
                            // the top window there other than it; if that is another app's, it hides ours: none
                            let top = crate::pointer::window_below(except, at.x, at.y);
                            if ours.contains(&top) { top } else { 0 }
                        }
                        None => crate::pointer::window_at(at.x, at.y),
                    };
                    let view = state
                        .views
                        .iter()
                        .find(|(_, v)| v.parent.is_none() && hwnd != 0 && window_handle(&v.window) == hwnd)
                        .map(|(id, _)| id.clone());
                    Outcome::json(&json!({"view": view}))
                }
                Err(error) => error,
            }
        }
        // this process and the browser engine's, each once (a warm view's engine counts while it is the only one)
        ["app", "memory"] => Outcome::json(&crate::memory::processes(state.views.values().map(|v| &v.webview).chain(state.warm.iter().map(|w| &w.webview)))),
        ["app", "warm"] => {
            #[derive(serde::Deserialize)]
            #[serde(rename_all = "camelCase")]
            struct WarmBody {
                data_directory: Option<std::path::PathBuf>,
            }
            let profile = if message.payload.is_empty() {
                Ok(None)
            } else {
                payload::json::<WarmBody>(&message.payload).map(|body| body.data_directory)
            };
            match profile {
                Err(error) => error,
                Ok(profile) => {
                    state.warm_profile = profile;
                    if state.warm.is_none() && !state.views.values().any(|v| v.parent.is_none()) {
                        state.warm_at = Some(std::time::Instant::now() + std::time::Duration::from_millis(50));
                    }
                    Outcome::ok()
                }
            }
        }
        ["app", "play"] => crate::sound::play(&message.payload),
        ["app", "dialog", "file"] => match payload::json::<FileDialogBody>(&message.payload) {
            Ok(body) => match parent(body.view.as_deref(), state) {
                Ok(window) => return answer_later(reply, crate::dialog::file(body, window)),
                Err(error) => error,
            },
            Err(error) => error,
        },
        ["app", "dialog", "message"] => match payload::json::<MessageDialogBody>(&message.payload) {
            Ok(body) => match parent(body.view.as_deref(), state) {
                Ok(window) => return answer_later(reply, crate::dialog::message(body, window)),
                Err(error) => error,
            },
            Err(error) => error,
        },
        ["app", "quit"] => {
            // Webviews before their windows and contexts, embedded ones first, while the loop is still alive.
            state.warm = None;
            state.views.retain(|_, v| v.parent.is_none());
            // Then owned windows before their owners: those that own none first, again until none is owned.
            loop {
                let owners: std::collections::HashSet<String> = state.views.values().filter_map(|v| v.owner.clone()).collect();
                if owners.is_empty() {
                    break;
                }
                state.views.retain(|id, v| v.owner.is_none() || owners.contains(id));
            }
            state.ids_by_window.clear();
            state.views.clear();
            *control_flow = ControlFlow::Exit;
            Outcome::ok()
        }
        // views moving between windows: they need the whole state (two views), and a detach the loop's windows
        #[cfg(windows)]
        ["view", host, "adopt"] => match payload::json::<AdoptBody>(&message.payload) {
            Ok(body) => adopt(state, host, body),
            Err(error) => error,
        },
        #[cfg(windows)]
        ["view", view, "front"] => raise(state, view),
        #[cfg(windows)]
        ["view", view, "detach"] => match payload::json::<DetachBody>(&message.payload) {
            Ok(body) => detach(state, target, view, body),
            Err(error) => error,
        },
        #[cfg(not(windows))]
        ["view", _, "adopt" | "detach" | "front" | "floating"] => Outcome::error(501, "views move between windows on Windows for now"),
        ["view", view, rest @ ..] => match on_view(view, rest, &message, &reply, state) {
            Some(outcome) => outcome,
            None => return, // answered later, or not at all (call answers)
        },
        _ => Outcome::error(404, format!("no subject '{subject}'")),
    };
    reply.send(outcome);
}

/// The window a dialog belongs to: the view's, or none.
fn parent<'a>(view: Option<&str>, state: &'a State) -> Result<Option<&'a Window>, Outcome> {
    match view {
        None => Ok(None),
        Some(view) => state.views.get(view).map(|instance| Some(&*instance.window)).ok_or_else(|| not_found(view)),
    }
}

/// Waits for a dialog on a thread of its own and replies with its answer: the loop goes on.
fn answer_later(reply: ReplyTo, answer: crate::dialog::Answer) {
    std::thread::Builder::new()
        .name("webview4j-dialog".into())
        .spawn(move || reply.send(pollster::block_on(answer)))
        .expect("a thread for the dialog");
}

/// A message about one view. None when a script callback replies later (eval, websocket send),
/// or nobody waits for a reply (the answer to a page's call).
/// How a person's move of a view's window (or of an MDI child in its window) goes to Java: `view.<view>.move`.
fn move_reporter(view: String) -> crate::pointer::OnMove {
    Box::new(move |m: crate::pointer::Move| {
        let (x, y, width, height) = m.bounds;
        let (ctrl, shift, alt) = m.keys;
        emit(&Message::new(format!("view.{view}.move")).payload(
            json!({"phase": m.phase, "x": x, "y": y, "width": width, "height": height, "cursorX": m.cursor.0, "cursorY": m.cursor.1,
                   "ctrl": ctrl, "shift": shift, "alt": alt})
            .to_string(),
        ));
    })
}

/// Raises an embedded view above the others in its window, and says so to their pages: it is the active one
/// (`data-swaswa-active` on its page's <html>), its siblings are not.
#[cfg(windows)]
fn raise(state: &mut State, view: &str) -> Outcome {
    let Some(instance) = state.views.get(view) else { return not_found(view) };
    let Some(parent) = instance.parent.clone() else {
        return Outcome::error(400, format!("'{view}' is a window: focus() brings a window forward")).kind("unsupported")
            .hint("toFront raises an embedded view above the others in its window; for a window, call focus().");
    };
    crate::dock::to_front(frame_window(instance));
    for (id, sibling) in state.views.iter() {
        if sibling.parent.as_deref() == Some(parent.as_str()) && sibling.floating {
            let active = id == view;
            let _ = sibling.webview.evaluate_script(&format!("window.__webview4j && window.__webview4j.state('active', {active});"));
        }
    }
    Outcome::ok()
}

/// The native window a view moves and stacks by: its container if docked, else its webview's own.
#[cfg(windows)]
fn frame_window(instance: &Instance) -> isize {
    instance.dock.unwrap_or_else(|| crate::dock::webview_window(&instance.webview))
}

#[derive(serde::Deserialize)]
struct AdoptBody {
    view: String,
    x: f64,
    y: f64,
    width: f64,
    height: f64,
}

/// `view.<host>.adopt`: the window's view `body.view` into `host`'s window at the bounds given (logical pixels), its
/// page running on; its own window goes.
#[cfg(windows)]
fn adopt(state: &mut State, host: &str, body: AdoptBody) -> Outcome {
    let Some(host_instance) = state.views.get(host) else { return not_found(host) };
    if host_instance.parent.is_some() {
        return Outcome::error(400, format!("'{host}' is embedded: a view moves into a window")).kind("unsupported")
            .hint("Adopt into a window's own view (the one app.open gave).");
    }
    if body.view == host {
        return Outcome::error(400, "a window cannot take its own view in").hint("Adopt another window's view.");
    }
    let host_window = Rc::clone(&host_instance.window);
    let scale = host_window.scale_factor();
    let p = |v: f64| (v * scale).round() as i32;
    let Some(child) = state.views.get(&body.view) else { return not_found(&body.view) };
    if child.parent.is_some() {
        return Outcome::error(400, format!("'{}' is embedded already", body.view)).kind("unsupported")
            .hint("Move an embedded view with place(...), or detach(...) it into a window of its own first.");
    }
    let dock = crate::dock::container(window_handle(&host_window), p(body.x), p(body.y), p(body.width), p(body.height));
    if dock == 0 {
        return Outcome::error(500, "a place for the view could not be made in the window");
    }
    if let Err(e) = wry::WebViewExtWindows::reparent(&child.webview, dock) {
        crate::dock::destroy(dock);
        return Outcome::error(500, format!("the view could not move: {e}"));
    }
    let State { views, ids_by_window, .. } = state;
    let child = views.get_mut(&body.view).expect("there");
    ids_by_window.remove(&child.window.id());
    child.window = host_window; // its own window's last reference goes: the window closes, without a word to Java
    child.parent = Some(host.to_string());
    child.dock = Some(dock);
    child.fills = false;
    if child.child_webview {
        // made embedded: it does not fill what it is in by itself
        let _ = child.webview.set_bounds(wry::Rect {
            position: tao::dpi::LogicalPosition::new(0.0, 0.0).into(),
            size: tao::dpi::LogicalSize::new(body.width, body.height).into(),
        });
    }
    let moved = body.view.clone();
    crate::pointer::watch_moves(
        dock,
        move_reporter(moved),
    );
    emit(&Message::new(format!("view.{}.reparented", body.view)).payload(json!({"host": host}).to_string()));
    Outcome::ok()
}

#[derive(serde::Deserialize)]
#[serde(rename_all = "camelCase")]
struct DetachBody {
    title: Option<String>,
    x: Option<f64>,
    y: Option<f64>,
    width: f64,
    height: f64,
    #[serde(default)]
    follow_cursor: bool,
}

/// `view.<id>.detach`: an embedded (or adopted) view out into a window of its own, its page running on; with
/// `followCursor`, the window follows the mouse as a dragged title bar does, until it is let go.
#[cfg(windows)]
fn detach(state: &mut State, target: &EventLoopWindowTarget<ToLoop>, id: &str, body: DetachBody) -> Outcome {
    let Some(instance) = state.views.get(id) else { return not_found(id) };
    if instance.parent.is_none() {
        return Outcome::error(400, format!("'{id}' has a window of its own already")).kind("unsupported")
            .hint("Detach an embedded view (or one adopted into another window).");
    }
    let mut builder = tao::window::WindowBuilder::new()
        .with_title(body.title.clone().unwrap_or_else(|| instance.webview.url().unwrap_or_default()))
        .with_inner_size(tao::dpi::LogicalSize::new(body.width, body.height));
    if let (Some(x), Some(y)) = (body.x, body.y) {
        builder = builder.with_position(tao::dpi::LogicalPosition::new(x, y));
    }
    let window = match builder.build(target) {
        Ok(window) => window,
        Err(e) => return Outcome::error(500, format!("a window could not be made: {e}")),
    };
    if body.follow_cursor && body.x.is_none() {
        // under the pointer, held by its title bar near the middle, as if it had been dragged out by it
        if let Ok(at) = target.cursor_position() {
            let scale = window.scale_factor();
            let half = body.width * scale / 2.0;
            window.set_outer_position(tao::dpi::PhysicalPosition::new(at.x - half, at.y - 12.0));
        }
    }
    let hwnd = window_handle(&window);
    if let Err(e) = wry::WebViewExtWindows::reparent(&instance.webview, hwnd) {
        return Outcome::error(500, format!("the view could not move: {e}"));
    }
    let State { views, ids_by_window, .. } = state;
    let instance = views.get_mut(id).expect("there");
    match instance.dock.take() {
        // an adopted window's view fills what it is in, by itself
        Some(dock) => crate::dock::destroy(dock),
        None => {
            instance.fills = true;
            let size = window.inner_size().to_logical::<f64>(window.scale_factor());
            let _ = instance.webview.set_bounds(wry::Rect {
                position: tao::dpi::LogicalPosition::new(0.0, 0.0).into(),
                size: tao::dpi::LogicalSize::new(size.width, size.height).into(),
            });
        }
    }
    instance.floating = false;
    instance.parent = None;
    instance.window = Rc::new(window);
    ids_by_window.insert(instance.window.id(), id.to_string());
    let moved = id.to_string();
    crate::pointer::watch_moves(
        hwnd,
        move_reporter(moved),
    );
    if body.follow_cursor {
        if let Err(e) = instance.window.drag_window() {
            log::debug!("the torn-off window could not follow the mouse: {e}");
        }
    }
    emit(&Message::new(format!("view.{id}.reparented")).payload(json!({"host": null}).to_string()));
    Outcome::ok()
}

fn on_view(view: &str, rest: &[&str], message: &Message, reply: &ReplyTo, state: &mut State) -> Option<Outcome> {
    let payload = message.payload.as_slice();
    if rest == ["close"] {
        return Some(if close_view(state, view) { Outcome::ok() } else { not_found(view) });
    }
    let Some(instance) = state.views.get_mut(view) else {
        return Some(not_found(view));
    };
    if instance.parent.is_some() {
        // An embedded view has no window of its own: what is about a window is refused; showing, hiding and
        // focusing are about the webview, and `place` moves it.
        match rest {
            ["title"] | ["size"] | ["position"] | ["bounds"] | ["state"] | ["min-size"] | ["geometry"] | ["monitors"] | ["theme"] | ["handle"]
            | ["screenshot"] | ["record"] | ["window"] | ["progress"] => {
                return Some(Outcome::error(400, format!("'{view}' is embedded: it has no window of its own"))
                    .kind("unsupported")
                    .hint("Call this on the window it is embedded in (the view you called embed(...) on); move or size an embedded view with place(x, y, width, height)."));
            }
            ["show"] => return Some(result(instance.webview.set_visible(true))),
            ["hide"] => return Some(result(instance.webview.set_visible(false))),
            ["focus"] => return Some(result(instance.webview.focus())),
            _ => {}
        }
    }
    #[cfg(windows)]
    if let ["floating"] = rest {
        if instance.parent.is_none() {
            return Some(Outcome::error(400, format!("'{view}' is a window: it floats already")).kind("unsupported")
                .hint("setFloating is for an embedded view: one a person moves and sizes inside its window, as an MDI child."));
        }
        let on = std::str::from_utf8(&message.payload).unwrap_or("").trim() == "true";
        if on && !instance.floating {
            // its moves reported with its own id, and its webview kept the size of its frame as a person sizes it
            let moved = view.to_string();
            let frame = frame_window(instance);
            let sized: Option<Box<dyn Fn(i32, i32)>> = if instance.dock.is_none() {
                use wry::WebViewExtWindows;
                let controller = instance.webview.controller();
                Some(Box::new(move |width, height| {
                    let bounds = windows::Win32::Foundation::RECT { left: 0, top: 0, right: width, bottom: height };
                    // SAFETY: the view's own controller, on the event loop's thread (its frame's messages come there)
                    let _ = unsafe { controller.SetBounds(bounds) };
                }))
            } else {
                None // a docked view fills its frame by itself
            };
            crate::pointer::watch(
                frame,
                move_reporter(moved),
                sized,
            );
        }
        instance.floating = on;
        let _ = instance.webview.evaluate_script(&format!("window.__webview4j_floating = {on};"));
        return Some(Outcome::ok());
    }
    if let ["client"] = rest {
        // where the window's page area is on the screen: physical pixels, and the window's scale
        if instance.parent.is_some() {
            return Some(Outcome::error(400, format!("'{view}' is embedded: its window's is its host's")).kind("unsupported")
                .hint("Ask the window it is in (its host)."));
        }
        let window = &instance.window;
        return Some(match window.inner_position() {
            Ok(at) => {
                let size = window.inner_size();
                Outcome::json(&json!({"x": at.x, "y": at.y, "width": size.width, "height": size.height, "scale": window.scale_factor()}))
            }
            Err(e) => Outcome::error(501, format!("its place is not known here: {e}")),
        });
    }
    if let ["drag-files"] = rest {
        // files dragged out of this view's window, while the person holds the mouse button: until they let go
        #[derive(serde::Deserialize)]
        struct Files {
            paths: Vec<std::path::PathBuf>,
        }
        return Some(match payload::json::<Files>(&message.payload) {
            Ok(files) => {
                if let Some(missing) = files.paths.iter().find(|p| !p.exists()) {
                    Outcome::error(404, format!("{} is not there", missing.display())).kind("system").hint("Drag files that exist: write the file first.")
                } else {
                    match crate::dragout::drag_files(&files.paths) {
                        Ok(effect) => Outcome::json(&json!({"effect": effect})),
                        Err(e) => Outcome::error(500, e).hint("Start the drag while the mouse button is held (on the page's mousedown or dragstart)."),
                    }
                }
            }
            Err(error) => error,
        });
    }
    if let ["memory-level"] = rest {
        // set ("low" / "normal"), or read (no payload): the engine's memory target for this view
        return Some(match std::str::from_utf8(&message.payload).unwrap_or("").trim() {
            "" => match crate::memory::level_low(&instance.webview) {
                Ok(low) => Outcome::json(&json!({"low": low})),
                Err(e) => Outcome::error(501, e).hint("Memory targets need Windows and a WebView2 Runtime from 2023 or later (Edge 114+)."),
            },
            level @ ("low" | "normal") => match crate::memory::set_level(&instance.webview, level == "low") {
                Ok(()) => Outcome::ok(),
                Err(e) => Outcome::error(501, e).hint("Memory targets need Windows and a WebView2 Runtime from 2023 or later (Edge 114+)."),
            },
            other => Outcome::error(400, format!("'{other}' is not a memory level")).hint("A memory level is low or normal."),
        });
    }
    if let ["frame", canvas] = rest {
        return frame(instance, canvas, message, reply);
    }
    Some(match rest {
        ["call", call] => {
            // Java's answer to the page's call: to the waiting fetch, status and bytes unread, or
            // settled by script for a call that came over IPC.
            if let Ok(call) = call.parse::<u64>() {
                let script = instance.link.lock().unwrap().answer(call, message.clone());
                if let Some(script) = script {
                    instance.flush_pushes(); // pushes sent before this answer arrive before it
                    settle(instance, &script);
                }
            }
            return None;
        }
        ["navigate"] => match payload::text(payload) {
            Ok(url) => result(instance.webview.load_url(url)),
            Err(error) => error,
        },
        ["load-html"] => match payload::text(payload) {
            Ok(html) => result(instance.webview.load_html(html)),
            Err(error) => error,
        },
        // The page's DevTools Protocol: a method's result, and its events as view.<view>.cdp.
        ["cdp", "call"] => match payload::json::<serde_json::Value>(payload) {
            Ok(body) => {
                let method = body.get("method").and_then(Value::as_str).unwrap_or_default().to_string();
                let params = body.get("params").cloned().unwrap_or_else(|| json!({})).to_string();
                if method.is_empty() {
                    return Some(Outcome::error(400, "a DevTools call names its method"));
                }
                let later = reply.clone();
                let called = crate::cdp::call(&instance.webview, &method, &params, move |result| {
                    later.send(match result {
                        Ok(json) => Outcome { status: 200, content_type: "application/json", body: json.into_bytes(), kind: None, hint: None, headers: Vec::new() },
                        Err(error) => Outcome::error(502, error).hint("Check the DevTools Protocol method's name and parameters (chromedevtools.github.io/devtools-protocol); the window must be open with devtools(true)."),
                    });
                });
                match called {
                    Ok(()) => return None,
                    Err(error) => Outcome::error(500, error),
                }
            }
            Err(error) => error,
        },
        ["cdp", "listen"] => match payload::text(payload) {
            Ok(event) => {
                let event = event.trim().to_string();
                let topic = format!("view.{view}.cdp");
                let name = event.clone();
                match crate::cdp::listen(view, &instance.webview, &event, move |params| {
                    let body = format!("{{\"event\":{},\"params\":{}}}", Value::String(name.clone()), params);
                    emit(&Message::new(topic.clone()).payload(body));
                }) {
                    Ok(()) => Outcome::ok(),
                    Err(error) => Outcome::error(500, error),
                }
            }
            Err(error) => error,
        },
        ["eval"] => match payload::text(payload) {
            Ok(script) => {
                let later = reply.clone();
                let evaluated = instance.webview.evaluate_script_with_callback(script, move |value| {
                    // The webview hands back the value already as JSON; empty when undefined.
                    let body = if value.trim().is_empty() { b"null".to_vec() } else { value.into_bytes() };
                    later.send(Outcome { status: 200, content_type: "application/json", body, kind: None, hint: None, headers: Vec::new() });
                });
                match evaluated {
                    Ok(()) => return None,
                    Err(e) => Outcome::error(500, e.to_string()),
                }
            }
            Err(error) => error,
        },
        ["cookies"] => match payload::text(payload) {
            Ok(url) => match instance.webview.cookies_for_url(url) {
                Ok(cookies) => Outcome::json(&cookie_records(cookies)),
                Err(error) => Outcome::error(500, error.to_string()),
            },
            Err(error) => error,
        },
        // every cookie of the view's browser profile, whatever site
        ["cookies", "all"] => match instance.webview.cookies() {
            Ok(cookies) => Outcome::json(&cookie_records(cookies)),
            Err(error) => Outcome::error(500, error.to_string()),
        },
        ["cookies", "set"] => match payload::json::<CookieBody>(payload).and_then(|body| on_the_pages_site(instance, body)) {
            Ok(body) => match body.cookie() {
                Ok(cookie) => result(instance.webview.set_cookie(&cookie)),
                Err(error) => error,
            },
            Err(error) => error,
        },
        ["cookies", "delete"] => match payload::json::<CookieBody>(payload).and_then(|body| on_the_pages_site(instance, body)) {
            Ok(body) => match body.cookie() {
                Ok(cookie) => result(instance.webview.delete_cookie(&cookie)),
                Err(error) => error,
            },
            Err(error) => error,
        },
        ["title"] => match payload::text(payload) {
            Ok(title) => {
                instance.window.set_title(title);
                instance.title = title.to_string();
                Outcome::ok()
            }
            Err(error) => error,
        },
        ["size"] => match payload::json::<SizeBody>(payload) {
            Ok(body) => {
                instance.window.set_inner_size(LogicalSize::new(body.width, body.height));
                Outcome::ok()
            }
            Err(error) => error,
        },
        ["position"] => match payload::json::<PositionBody>(payload) {
            Ok(body) => {
                instance.window.set_outer_position(LogicalPosition::new(body.x, body.y));
                Outcome::ok()
            }
            Err(error) => error,
        },
        ["bounds"] => match payload::json::<BoundsBody>(payload) {
            Ok(body) => {
                set_bounds(&instance.window, body.x, body.y, body.width.max(1), body.height.max(1));
                Outcome::ok()
            }
            Err(error) => error,
        },
        ["show"] => {
            instance.window.set_visible(true);
            Outcome::ok()
        }
        ["hide"] => {
            instance.window.set_visible(false);
            Outcome::ok()
        }
        ["focus"] => {
            // whether it came to the front: Windows refuses a background app that while the person works in another
            // (the taskbar button flashes instead)
            instance.window.set_focus();
            Outcome::json(&json!({"focused": active(&instance.window)}))
        }
        ["state"] => match payload::json::<StateBody>(payload) {
            Ok(body) => {
                let window = &instance.window;
                if let Some(on) = body.minimized {
                    window.set_minimized(on);
                }
                if let Some(on) = body.maximized {
                    window.set_maximized(on);
                }
                if let Some(on) = body.fullscreen {
                    window.set_fullscreen(on.then_some(Fullscreen::Borderless(None)));
                }
                if let Some(on) = body.always_on_top {
                    window.set_always_on_top(on);
                }
                if let Some(on) = body.resizable {
                    window.set_resizable(on);
                }
                Outcome::ok()
            }
            Err(error) => error,
        },
        ["theme"] => match payload::text(payload).map(|t| serde_json::from_value::<ThemeChoice>(Value::String(t.trim().to_string()))) {
            Ok(Ok(theme)) => {
                instance.window.set_theme(window_theme(theme));
                page_theme(&instance.webview, theme)
            }
            Ok(Err(_)) => Outcome::error(400, "a theme is system, light or dark"),
            Err(error) => error,
        },
        ["min-size"] => match payload::json::<SizeBody>(payload) {
            Ok(body) => {
                let size = (body.width > 0.0 && body.height > 0.0).then(|| LogicalSize::new(body.width, body.height));
                instance.window.set_min_inner_size(size);
                Outcome::ok()
            }
            Err(error) => error,
        },
        ["window"] => match payload::json::<WindowBody>(payload) {
            Ok(body) => {
                if let Some(on) = body.decorations {
                    instance.window.set_decorations(on);
                    // without the frame, the bridge resizes the window from the page's edges (bridge.js)
                    let frameless = !on && instance.window.is_resizable();
                    if let Err(error) = instance.webview.evaluate_script(&format!("window.__webview4j_frameless = {frameless};")) {
                        log::debug!("the page could not be told the window's frame changed: {error}");
                    }
                }
                window_settings(&instance.window, body)
            }
            Err(error) => error,
        },
        ["progress"] => match payload::json::<ProgressBody>(payload) {
            Ok(body) => progress(&instance.window, &body),
            Err(error) => error,
        },
        // the page: zoom, history, print, what it keeps
        ["zoom"] => match payload::text(payload).map(|t| t.trim().parse::<f64>()) {
            Ok(Ok(zoom)) if zoom > 0.0 && zoom.is_finite() => result(instance.webview.zoom(zoom)),
            Ok(_) => Outcome::error(400, "a zoom is a number above 0 (1 is 100%)"),
            Err(error) => error,
        },
        ["reload"] => result(instance.webview.reload()),
        ["back"] => result(instance.webview.evaluate_script("history.back()")),
        ["forward"] => result(instance.webview.evaluate_script("history.forward()")),
        ["print"] => result(instance.webview.print()),
        ["clear-browsing-data"] => result(instance.webview.clear_all_browsing_data()),
        ["shortcuts"] => match payload::json::<Vec<String>>(payload) {
            Ok(list) => result(instance.webview.evaluate_script(&format!(
                "window.__webview4j_shortcuts = new Set({});",
                serde_json::to_string(&list).unwrap_or_else(|_| "[]".to_string())
            ))),
            Err(error) => error,
        },
        ["geometry"] => Outcome::json(&geometry(&instance.window)),
        ["handle"] => native_handle(&instance.window),
        ["devtools"] => devtools(instance, payload),
        ["monitors"] => Outcome::json(&monitors(&instance.window)),
        ["screenshot"] => match payload::json::<crate::capture::ShotBody>(payload) {
            Ok(body) => match crate::capture::grab(window_handle(&instance.window)).and_then(|f| f.scaled(body.scale.unwrap_or(1.0)).png()) {
                Ok(png) => Outcome { status: 200, content_type: "image/png", body: png, kind: None, hint: None, headers: Vec::new() },
                Err(e) => Outcome::error(if instance.window.is_visible() { 501 } else { 409 }, capture_problem(&instance.window, e)),
            },
            Err(error) => error,
        },
        ["record"] => match payload::json::<crate::capture::RecordBody>(payload) {
            Ok(body) if body.action == "stop" => {
                let later = ReplyTo(reply.0.clone());
                crate::capture::stop_later(view, move |done| {
                    later.send(match done {
                        Ok(done) => Outcome::json(&done),
                        Err(e) => Outcome::error(409, e).hint("Start a recording with view.record(file) before stopping it; each Recording is stopped once."),
                    })
                });
                return None;
            }
            Ok(body) if body.path.as_deref().is_some_and(|p| writable(Path::new(p)).is_err()) => {
                // the file first: what the system says about it, not a recording that fails later
                let path = body.path.clone().unwrap_or_default();
                let error = writable(Path::new(&path)).expect_err("checked");
                crate::oserror::outcome("the recording's file could not be written", &error, Some(Path::new(&path)))
            }
            Ok(body) => match crate::capture::start(view, window_handle(&instance.window), body) {
                Ok(()) => Outcome::ok(),
                Err(e) => {
                    let problem = capture_problem(&instance.window, e);
                    let hint = if problem.contains("already being recorded") {
                        "One recording at a time per window: stop() the one under way first (it hands back its Result)."
                    } else if problem.contains("ffmpeg") {
                        "WebM, and MP4 outside Windows, are encoded by ffmpeg: install it and put it on the PATH, or record a GIF (Recording.Options.gif())."
                    } else {
                        "Only a window that is open and not minimized can be recorded; check the file's folder can be written."
                    };
                    Outcome::error(409, problem).hint(hint)
                }
            },
            Err(error) => error,
        },
        ["place"] => match payload::json::<PlaceBody>(payload) {
            #[cfg(windows)]
            Ok(body) if instance.dock.is_some() => {
                let scale = instance.window.scale_factor();
                let p = |v: f64| (v * scale).round() as i32;
                crate::dock::place(instance.dock.expect("docked"), p(body.x), p(body.y), p(body.width), p(body.height));
                if instance.child_webview {
                    let _ = instance.webview.set_bounds(wry::Rect {
                        position: tao::dpi::LogicalPosition::new(0.0, 0.0).into(),
                        size: tao::dpi::LogicalSize::new(body.width, body.height).into(),
                    });
                }
                Outcome::ok()
            }
            Ok(body) if instance.parent.is_some() => result(instance.webview.set_bounds(logical_rect(&body))),
            Ok(_) => Outcome::error(400, format!("'{view}' is not embedded: place moves an embedded view")),
            Err(error) => error,
        },
        ["capture"] => match payload::json::<CaptureBody>(payload) {
            Ok(body) => {
                instance.capture.apply(body);
                Outcome::ok()
            }
            Err(error) => error,
        },
        ["websocket", socket, "send"] => {
            let Ok(socket) = socket.parse::<u64>() else {
                return Some(Outcome::error(400, format!("'{socket}' is not a socket number")));
            };
            match payload::text(payload) {
                Ok(data) => {
                    let later = reply.clone();
                    let sent = instance.webview.evaluate_script_with_callback(&send_script(socket, data), move |result| {
                        // The script answers null when sent, or why it could not send.
                        later.send(match serde_json::from_str::<Value>(&result) {
                            Ok(Value::String(error)) => Outcome::error(404, error),
                            _ => Outcome::ok(),
                        })
                    });
                    match sent {
                        Ok(()) => return None,
                        Err(e) => Outcome::error(500, e.to_string()),
                    }
                }
                Err(error) => error,
            }
        }
        ["emit", topic @ ..] if !topic.is_empty() => {
            // Small: a script, batched with the others of this round (fast); large: a frame for
            // the page's poll, payload unread.
            let content_type = message.get("Content-Type").unwrap_or("application/json");
            let route = instance.link.lock().unwrap().emit(&topic.join("."), content_type, payload);
            match route {
                bridge::Emit::Script(script) => {
                    instance.pushes_size += script.len();
                    instance.pushes.push(script);
                    if instance.pushes_size >= PUSH_BATCH {
                        instance.flush_pushes();
                    }
                    Outcome::ok()
                }
                bridge::Emit::Done(outcome) => outcome,
            }
        }
        _ => Outcome::error(404, format!("no subject '{}'", message.subject)),
    })
}

/// The page script that sends a text frame on the inspector's socket number `socket`.
fn send_script(socket: u64, data: &str) -> String {
    let data = Value::String(data.to_string());
    format!(
        "(() => {{ const ws = window.__WEB_INSPECTOR_SOCKETS__ && window.__WEB_INSPECTOR_SOCKETS__.get({socket}); \
         if (!ws) return 'no open socket {socket}'; \
         if (ws.readyState !== 1) return 'socket {socket} is not open'; \
         ws.send({data}); return null; }})()"
    )
}

/// A picture for one of the page's canvases: kept for the page to fetch, the page told; the reply goes once it is
/// drawn (a later picture drawn first: this one was skipped, `drawn: false`).
fn frame(instance: &mut Instance, canvas: &str, message: &Message, reply: &ReplyTo) -> Option<Outcome> {
    if !bridge::canvas_name(canvas) {
        return Some(Outcome::error(400, format!("'{canvas}' is not a canvas name"))
            .hint("A canvas name is its id in the page: letters, digits, - and _, up to 64."));
    }
    let header = |name: &str| message.headers.iter().find(|(k, _)| k.eq_ignore_ascii_case(name)).map(|(_, v)| v.as_str());
    let number = |name: &str| header(name).and_then(|v| v.parse::<u32>().ok()).unwrap_or(0);
    let (width, height) = (number("Width"), number("Height"));
    let format = match header("Format").unwrap_or("rgba") {
        "rgba" => 0,
        "png" => 1,
        "jpeg" => 2,
        other => return Some(Outcome::error(400, format!("'{other}' is not a picture format (rgba, png, jpeg)"))),
    };
    if format == 0 && (width == 0 || height == 0 || message.payload.len() as u64 != width as u64 * height as u64 * 4) {
        return Some(Outcome::error(400, format!("RGBA pixels are width × height × 4 bytes: {width} × {height} × 4 is not {}", message.payload.len())));
    }
    instance.next_frame += 1;
    let seq = instance.next_frame;
    instance.link.lock().unwrap().put_frame(canvas, bridge::Frame { seq, width, height, format, bytes: Arc::new(message.payload.clone()) });
    if let Some((_, skipped)) = instance.frame_replies.insert(canvas.to_string(), (seq, reply.clone())) {
        skipped.send(Outcome::json(&json!({"drawn": false})));
    }
    let name = serde_json::to_string(canvas).expect("a string");
    if let Err(error) = instance.webview.evaluate_script(&format!("window.__webview4j && window.__webview4j.frameReady({name}, {seq});")) {
        instance.frame_replies.remove(canvas);
        return Some(Outcome::error(500, format!("the page could not be told: {error}")));
    }
    None
}

/// What a page sends over IPC: a small call (a frame, bridge.rs), or an inspector report.
fn from_page(request: &wry::http::Request<String>, view: &str, capture: &Capture, link: &Mutex<bridge::Link>) {
    let raw = request.body();
    if let Some(action) = raw.strip_prefix("WIN ") {
        // Only a page the bridge allows moves or closes its window.
        let page = bridge::page_origin(&request.uri().to_string());
        if link.lock().unwrap().allows(&page) {
            if let Some(proxy) = PROXY.lock().unwrap().clone() {
                let _ = proxy.send_event(ToLoop::Window { view: view.to_string(), action: action.to_string() });
            }
        }
    } else if let Some(shortcut) = raw.strip_prefix("KEY ") {
        // A key the window answers itself, pressed in a page the bridge allows.
        let page = bridge::page_origin(&request.uri().to_string());
        if link.lock().unwrap().allows(&page) {
            emit(&Message::new(format!("view.{view}.shortcut")).payload(json!({"shortcut": shortcut}).to_string()));
        }
    } else if let Some(drawn) = raw.strip_prefix("FRM ") {
        // "FRM <seq> <canvas>", or "FRM <seq> <canvas> <why not>": a canvas's picture, drawn or not.
        let page = bridge::page_origin(&request.uri().to_string());
        let mut parts = drawn.splitn(3, ' ');
        let (seq, canvas, error) = (parts.next(), parts.next(), parts.next());
        if let (Some(Ok(seq)), Some(canvas)) = (seq.map(str::parse::<u64>), canvas) {
            if bridge::canvas_name(canvas) && link.lock().unwrap().allows(&page) {
                if let Some(proxy) = PROXY.lock().unwrap().clone() {
                    let _ = proxy.send_event(ToLoop::Drawn {
                        view: view.to_string(),
                        canvas: canvas.to_string(),
                        seq,
                        error: error.map(str::to_string),
                    });
                }
            }
        }
    } else if raw.starts_with("MSG ") {
        let page_url = request.uri().to_string();
        let outcome = link.lock().unwrap().from_ipc(raw, &page_url);
        match outcome {
            bridge::FromPage::Java(call) => emit(&call),
            // Run by the loop, which owns the webview; this handler only borrows the page.
            bridge::FromPage::Page(script) => refuse_later(view, script),
            bridge::FromPage::Nothing => {}
        }
    } else if let Some((kind, report)) = inspector::event(raw, capture) {
        emit(&Message::new(format!("view.{view}.{kind}")).payload(report.to_string()));
    }
}

fn refuse_later(view: &str, script: String) {
    if let Some(proxy) = PROXY.lock().unwrap().clone() {
        let _ = proxy.send_event(ToLoop::Refuse { view: view.to_string(), script });
    }
}

/// Runs a settle script (bridge::settle_script) in the page: only while the page showing is one
/// the bridge allows, so an answer never reaches a page the window navigated to since.
fn settle(instance: &Instance, script: &str) {
    let page = instance.webview.url().unwrap_or_default();
    if instance.link.lock().unwrap().allows(&bridge::page_origin(&page)) {
        if let Err(error) = instance.webview.evaluate_script(script) {
            log::warn!("could not answer the page: {error}");
        }
    }
}

/// Whether the window is the active one, the one the keyboard goes to: on Windows the foreground window, whether
/// the keyboard is in the window itself or in its webview (Tao's `is_focused` says false once the webview has it).
fn active(window: &Window) -> bool {
    #[cfg(windows)]
    {
        use windows_sys::Win32::UI::WindowsAndMessaging::GetForegroundWindow;
        let foreground = unsafe { GetForegroundWindow() };
        !foreground.is_null() && foreground as isize == window_handle(window)
    }
    #[cfg(not(windows))]
    {
        window.is_focused()
    }
}

/// Whether `file` can be written: its folder made, the file created (a recording then writes over it).
fn writable(file: &Path) -> std::io::Result<()> {
    if let Some(folder) = file.parent().filter(|f| !f.as_os_str().is_empty()) {
        std::fs::create_dir_all(folder)?;
    }
    std::fs::OpenOptions::new().write(true).create(true).truncate(false).open(file).map(|_| ())
}

/// The window's native handle, for capture (its HWND on Windows; 0 elsewhere, where capture says it cannot yet).
fn window_handle(window: &Window) -> isize {
    #[cfg(windows)]
    {
        use tao::platform::windows::WindowExtWindows;
        window.hwnd() as isize
    }
    #[cfg(not(windows))]
    {
        let _ = window;
        0
    }
}

/// What to say when a window cannot be captured: a hidden one has nothing to show.
fn capture_problem(window: &Window, error: String) -> String {
    if window.is_visible() { error } else { format!("a hidden window has nothing to capture (open it with headless false): {error}") }
}

/// The monitors, in the window's own logical pixels (as `geometry`'s are), so the two can be compared: each one's
/// bounds and work area are where a window may go.
fn monitors(window: &Window) -> Vec<crate::payload::Monitor> {
    let scale = window.scale_factor();
    let primary = window.primary_monitor().map(|m| (m.position(), m.size()));
    let current = window.current_monitor().map(|m| (m.position(), m.size()));
    window
        .available_monitors()
        .map(|m| {
            let position = m.position().to_logical::<f64>(scale);
            let size = m.size().to_logical::<f64>(scale);
            let key = (m.position(), m.size());
            crate::payload::Monitor {
                name: m.name().unwrap_or_default(),
                x: position.x,
                y: position.y,
                width: size.width,
                height: size.height,
                scale_factor: m.scale_factor(),
                primary: primary == Some(key),
                current: current == Some(key),
            }
        })
        .collect()
}

fn geometry(window: &Window) -> Geometry {
    let scale = window.scale_factor();
    let position = window.outer_position().unwrap_or_default().to_logical::<f64>(scale);
    let size = window.inner_size().to_logical::<f64>(scale);
    Geometry {
        x: position.x,
        y: position.y,
        width: size.width,
        height: size.height,
        scale_factor: scale,
        minimized: window.is_minimized(),
        maximized: window.is_maximized(),
        fullscreen: window.fullscreen().is_some(),
        focused: active(window),
        visible: window.is_visible(),
    }
}

fn list(state: &State) -> Vec<WebViewInfo> {
    state
        .views
        .iter()
        .map(|(id, instance)| WebViewInfo {
            view: id.clone(),
            title: instance.title.clone(),
            url: instance.webview.url().unwrap_or_default(),
            data_directory: instance.context.as_ref().and_then(|c| c.data_directory().map(|p| p.to_path_buf())),
        })
        .collect()
}

fn open_webview(open: OpenRequest, target: &EventLoopWindowTarget<ToLoop>, state: &mut State) -> Outcome {
    if let Some(path) = &open.data_directory {
        if !path.is_absolute() {
            return Outcome::error(400, "dataDirectory must be absolute");
        }
        if let Err(e) = std::fs::create_dir_all(path) {
            return crate::oserror::outcome("the browser profile's folder could not be made", &e, Some(path));
        }
    }
    let parent = match &open.parent {
        None => None,
        Some(parent) => match state.views.get(parent) {
            Some(instance) if instance.parent.is_none() => Some((parent.clone(), Rc::clone(&instance.window))),
            Some(_) => return Outcome::error(400, format!("'{parent}' is embedded itself: embed in a window's own view")),
            None => return not_found(parent),
        },
    };
    if parent.is_some() && !EMBEDDING {
        return Outcome::error(501, "embedded views are not supported on this system yet")
            .hint("Embedded views work on Windows for now; elsewhere open the page in a window of its own (app.open(...)).");
    }
    // The window that owns this one: by its view (an embedded view's is the window it sits in).
    let owner = match &open.owner {
        None => None,
        Some(_) if parent.is_some() => return Outcome::error(400, "an embedded view has no window of its own to be owned: owner or parent"),
        Some(_) if open.owner_window.is_some() => return Outcome::error(400, "owner or ownerWindow, not both"),
        Some(owner) => match state.views.get(owner) {
            Some(instance) => Some((instance.parent.clone().unwrap_or_else(|| owner.clone()), Rc::clone(&instance.window))),
            None => return not_found(owner),
        },
    };
    let window = match &parent {
        Some((_, window)) => Rc::clone(window),
        None => match window_builder(&open, owner.as_ref().map(|(_, window)| &**window)).build(target) {
            Ok(window) => Rc::new(window),
            Err(e) => return Outcome::error(500, format!("failed to create the window: {e}")),
        },
    };
    state.next_id += 1;
    let id = format!("wv-{}", state.next_id);

    let mut context = open.data_directory.as_ref().map(|path| Box::new(WebContext::new(Some(path.clone()))));
    let mut builder = match context.as_deref_mut() {
        Some(context) => WebViewBuilder::new_with_web_context(context),
        None => WebViewBuilder::new(),
    }
    .with_devtools(open.devtools);
    let capture = Arc::new(Capture::new(open.capture));
    let (view_id, view_capture) = (id.clone(), Arc::clone(&capture));
    let origins = open.origins.clone().unwrap_or_else(|| match (&parent, &open.url) {
        // An embedded page is someone else's site, usually: no bridge unless Java names its origins.
        (Some(_), _) => Vec::new(),
        (None, Some(url)) => bridge::origin_of(url).into_iter().collect(),
        (None, None) => vec!["null".to_string()],
    });
    // Where the window's own pages are: its bridge origins, its url's, the app's files', and any Java allows. A
    // link anywhere else opens in the system's browser, never in place of the app (an embedded site browses on).
    let mut home: Vec<String> = origins.clone();
    home.extend(open.url.as_deref().and_then(bridge::origin_of));
    home.extend(bridge::origin_of(bridge::base_url()));
    home.extend(open.navigation.clone().unwrap_or_default());
    let embedded = parent.is_some();
    builder = builder
        .with_navigation_handler(move |url| {
            if embedded {
                return true;
            }
            match bridge::navigation(&url, &home) {
                bridge::Navigation::Stay => true,
                bridge::Navigation::Browser => {
                    open_in_browser(&url);
                    false
                }
                bridge::Navigation::Refuse => {
                    log::warn!("a page tried to go to {url}: refused");
                    false
                }
            }
        })
        // target=_blank and window.open: never a bare browser window of WebView2's own. The system's browser (the
        // default), refused, or reported for Java to decide.
        .with_new_window_req_handler({
            let policy = open.new_windows;
            let new_window_view = id.clone();
            move |url, _| {
                match policy {
                    NewWindows::Browser if bridge::browsable(&url) => open_in_browser(&url),
                    NewWindows::Browser => log::warn!("a page tried to open {url} in a new window: refused"),
                    NewWindows::Deny => log::debug!("a page tried to open {url} in a new window: refused (newWindows deny)"),
                    NewWindows::Java => {
                        emit(&Message::new(format!("view.{new_window_view}.new-window")).payload(json!({"url": url}).to_string()))
                    }
                }
                wry::NewWindowResponse::Deny
            }
        });
    // downloads: allowed and reported, or refused
    {
        let allowed = open.downloads;
        let started_view = id.clone();
        let finished_view = id.clone();
        builder = builder
            .with_download_started_handler(move |url, path| {
                if allowed {
                    let report = json!({"state": "started", "url": url, "path": path.to_string_lossy()});
                    emit(&Message::new(format!("view.{started_view}.download")).payload(report.to_string()));
                }
                allowed
            })
            .with_download_completed_handler(move |url, path, ok| {
                let path = path.map(|p| p.to_string_lossy().into_owned());
                let report = json!({"state": if ok { "finished" } else { "failed" }, "url": url, "path": path});
                emit(&Message::new(format!("view.{finished_view}.download")).payload(report.to_string()));
            });
    }
    let see_through = open.transparent || matches!(open.backdrop, Some(b) if b != Backdrop::None);
    if see_through {
        builder = builder.with_transparent(true);
    }
    if let Some([r, g, b, a]) = open.background_color {
        builder = builder.with_background_color((r, g, b, a));
    }
    if let Some(agent) = &open.user_agent {
        builder = builder.with_user_agent(agent);
    }
    builder = builder.with_initialization_script(&shortcut_script(&open.shortcuts));
    let link = bridge::Link::with_assets(&id, origins, open.assets.clone());
    let page_link = Arc::clone(&link);
    let ipc_link = Arc::clone(&link);
    builder = with_page_theme(https_scheme(builder), open.theme)
        .with_initialization_script(&bridge::script())
        .with_initialization_script(&inspector::initialization_script())
        .with_ipc_handler(move |request| from_page(&request, &view_id, &view_capture, &ipc_link))
        .with_asynchronous_custom_protocol(bridge::SCHEME.into(), move |_, request, responder| {
            let call = page_link.lock().unwrap().request(request, responder);
            if let Some(call) = call {
                emit(&call);
            }
        });
    if !open.decorations && open.resizable {
        // No system frame to grab: the bridge resizes the window from the page's edges (bridge.js).
        builder = builder.with_initialization_script("window.__webview4j_frameless = true;");
    }
    if let Some(script) = &open.initialization_script {
        builder = builder.with_initialization_script(script);
    }
    // Where the page is and what it is called, for a host's address bar and tab title (a JavaFX browser pane).
    let load_view = id.clone();
    let title_view = id.clone();
    builder = builder
        .with_on_page_load_handler(move |event, url| {
            let state = match event {
                wry::PageLoadEvent::Started => "started",
                wry::PageLoadEvent::Finished => "finished",
            };
            emit(&Message::new(format!("view.{load_view}.page-load")).payload(json!({"state": state, "url": url}).to_string()));
        })
        .with_document_title_changed_handler(move |title| {
            emit(&Message::new(format!("view.{title_view}.title-changed")).payload(json!({"title": title}).to_string()));
        });
    if open.file_drop {
        let drop_view = id.clone();
        builder = builder.with_drag_drop_handler(move |event| {
            file_drop(&drop_view, event);
            true // the page does not open the file itself
        });
    }
    if let Some(url) = &open.url {
        builder = builder.with_url(url);
    } else if let Some(html) = &open.html {
        builder = builder.with_html(html);
    }

    let built = match &parent {
        Some(_) => builder
            .with_bounds(logical_rect(&PlaceBody { x: open.x, y: open.y, width: open.width, height: open.height }))
            .with_visible(!open.headless)
            .build_as_child(&*window),
        None => build_webview(builder, &window),
    };
    let webview = match built {
        Ok(webview) => webview,
        Err(e) => return Outcome::error(500, format!("failed to create the webview: {e}")),
    };

    if parent.is_none() && open.offscreen {
        crate::cdp::offscreen(&window);
    }
    if let (None, Some(material)) = (&parent, open.backdrop) {
        backdrop(&window, material);
    }
    if parent.is_none() {
        state.ids_by_window.insert(window.id(), id.clone());
        // A real window keeps the engine started now: the warm-up's work is done (or not needed).
        state.warm = None;
        state.warm_at = None;
    }
    // a person moving or sizing the window: when they start and when they let go (a drop target's moment)
    if parent.is_none() {
        let moved_view = id.clone();
        crate::pointer::watch_moves(
            window_handle(&window),
            move_reporter(moved_view),
        );
    }
    // an engine process that fails (a crash, out of memory, a renderer that stops answering): said, not a blank page
    let failed_view = id.clone();
    if let Err(error) = crate::memory::on_process_failed(&webview, move |kind, reason, exit_code| {
        emit(&Message::new(format!("view.{failed_view}.process-failed"))
            .payload(json!({"kind": kind, "reason": reason, "exitCode": exit_code}).to_string()));
    }) {
        log::debug!("process failures will not be reported: {error}");
    }
    let instance = Instance {
        child_webview: parent.is_some(),
        dock: None,
        fills: false,
        floating: false,
        next_frame: 0,
        frame_replies: HashMap::new(),
        window,
        parent: parent.map(|(parent, _)| parent),
        owner: owner.map(|(owner, _)| owner),
        webview,
        title: open.title,
        capture,
        link,
        pushes: Vec::new(),
        pushes_size: 0,
        confirm_close: open.confirm_close,
        devtools: open.devtools,
        resized: None,
        moved: None,
        shown: (false, open.maximized, open.fullscreen),
        escape_exits_fullscreen: open.escape_exits_fullscreen,
        focused: false,
        context,
    };
    state.views.insert(id.clone(), instance);
    Outcome::json(&json!({"view": id}))
}

/// Files dragged onto a window: `enter` and `drop` with their paths, `leave`. Positions are physical
/// pixels from the webview's top left.
fn file_drop(view: &str, event: wry::DragDropEvent) {
    let (kind, paths, position) = match event {
        wry::DragDropEvent::Enter { paths, position } => ("enter", paths, Some(position)),
        wry::DragDropEvent::Drop { paths, position } => ("drop", paths, Some(position)),
        wry::DragDropEvent::Leave => ("leave", Vec::new(), None),
        _ => return, // over: dozens a second, and nothing new
    };
    let paths: Vec<String> = paths.iter().map(|p| p.to_string_lossy().into_owned()).collect();
    let (x, y) = position.unwrap_or_default();
    let report = json!({"kind": kind, "paths": paths, "x": x, "y": y});
    emit(&Message::new(format!("view.{view}.file-drop")).payload(report.to_string()));
}

/// Where child webviews work: Windows and macOS. On Linux a child webview needs a GTK container the window does
/// not have yet (wry's build_as_child is X11 only), so embedding is refused there with `501`.
const EMBEDDING: bool = cfg!(any(target_os = "windows", target_os = "macos"));

/// An embedded view's place in its window, logical pixels from the window's top left.
fn logical_rect(place: &PlaceBody) -> wry::Rect {
    wry::Rect {
        position: wry::dpi::LogicalPosition::new(place.x, place.y).into(),
        size: wry::dpi::LogicalSize::new(place.width.max(1.0), place.height.max(1.0)).into(),
    }
}

/// Windows maps the custom protocol onto `http(s)://webview4j.localhost`: https, so a secure page
/// never makes an insecure request to it.
#[cfg(target_os = "windows")]
fn https_scheme(builder: WebViewBuilder) -> WebViewBuilder {
    use wry::WebViewBuilderExtWindows;
    builder.with_https_scheme(true)
}

#[cfg(not(target_os = "windows"))]
fn https_scheme(builder: WebViewBuilder) -> WebViewBuilder {
    builder
}

#[cfg(target_os = "windows")]
fn any_thread(builder: &mut EventLoopBuilder<ToLoop>) {
    use tao::platform::windows::EventLoopBuilderExtWindows;
    builder.with_any_thread(true);
}

#[cfg(not(any(target_os = "windows", target_os = "macos", target_os = "ios", target_os = "android")))]
fn any_thread(builder: &mut EventLoopBuilder<ToLoop>) {
    use tao::platform::unix::EventLoopBuilderExtUnix;
    builder.with_any_thread(true);
}

/// macOS: the loop must run on the process's first thread; there is no opting out.
#[cfg(any(target_os = "macos", target_os = "ios", target_os = "android"))]
fn any_thread(_builder: &mut EventLoopBuilder<ToLoop>) {}

#[cfg(any(target_os = "windows", target_os = "macos", target_os = "ios", target_os = "android"))]
fn build_webview(builder: WebViewBuilder, window: &Window) -> wry::Result<WebView> {
    builder.build(window)
}

#[cfg(not(any(target_os = "windows", target_os = "macos", target_os = "ios", target_os = "android")))]
fn build_webview(builder: WebViewBuilder, window: &Window) -> wry::Result<WebView> {
    use tao::platform::unix::WindowExtUnix;
    use wry::WebViewBuilderExtUnix;
    let vbox = window.default_vbox().expect("the window has no default vbox");
    builder.build_gtk(vbox)
}

fn result(result: Result<(), wry::Error>) -> Outcome {
    match result {
        Ok(()) => Outcome::ok(),
        Err(e) => Outcome::error(500, e.to_string()),
    }
}

/// Opens an http(s) or mailto address in the system's browser or mail app: the address as one argument, never
/// through a shell, on a thread of its own (the loop does not wait for the browser).
fn open_in_browser(url: &str) {
    if !bridge::browsable(url) {
        return;
    }
    let url = url.trim().to_string();
    log::info!("opening {url} in the system's browser");
    // Tests: the decision is made and logged, no browser starts.
    if std::env::var("WEBVIEW4J_BROWSER").is_ok_and(|v| v == "off") {
        return;
    }
    let _ = std::thread::Builder::new().name("webview4j-browser".into()).spawn(move || {
        let mut command = if cfg!(target_os = "windows") {
            let mut c = std::process::Command::new("rundll32");
            c.args(["url.dll,FileProtocolHandler", &url]);
            c
        } else if cfg!(target_os = "macos") {
            let mut c = std::process::Command::new("open");
            c.arg(&url);
            c
        } else {
            let mut c = std::process::Command::new("xdg-open");
            c.arg(&url);
            c
        };
        if let Err(error) = command.status() {
            log::warn!("no browser opened {url}: {error}");
        }
    });
}

fn not_found(view: &str) -> Outcome {
    Outcome::error(404, format!("no view '{view}': it was closed, or never opened in this app"))
        .kind("view-closed")
        .hint("The window was closed (by the person, or by close()). Subscribe to Closed (view.subscribe(Closed.class, ...)) to stop using it, or open a new one with app.open(...).")
}

fn window_builder(open: &OpenRequest, owner: Option<&Window>) -> WindowBuilder {
    let mut builder = WindowBuilder::new()
        .with_title(&open.title)
        .with_inner_size(LogicalSize::new(open.width, open.height))
        .with_resizable(open.resizable)
        .with_decorations(open.decorations)
        .with_visible(!open.headless)
        .with_maximized(open.maximized)
        .with_always_on_top(open.always_on_top)
        .with_theme(window_theme(open.theme));
    if open.positioned {
        builder = builder.with_position(LogicalPosition::new(open.x, open.y));
    }
    if open.fullscreen {
        builder = builder.with_fullscreen(Some(Fullscreen::Borderless(None)));
    }
    if let (Some(width), Some(height)) = (open.min_width, open.min_height) {
        builder = builder.with_min_inner_size(LogicalSize::new(width, height));
    }
    if let (Some(width), Some(height)) = (open.max_width, open.max_height) {
        builder = builder.with_max_inner_size(LogicalSize::new(width, height));
    }
    let see_through = open.transparent || matches!(open.backdrop, Some(b) if b != Backdrop::None);
    builder = builder
        .with_minimizable(open.minimizable)
        .with_maximizable(open.maximizable)
        .with_closable(open.closable)
        .with_focusable(open.focusable)
        .with_content_protection(open.content_protection)
        .with_visible_on_all_workspaces(open.all_workspaces)
        .with_transparent(see_through);
    if let Some([r, g, b, a]) = open.background_color {
        builder = builder.with_background_color((r, g, b, a));
    }
    builder = chrome(builder, open);
    if let Some(png) = &open.icon {
        // A picture that is not one leaves the program's icon: not worth refusing the window for.
        match crate::icon::from_png_base64(png) {
            Ok(icon) => builder = with_icon(builder, icon),
            Err(reason) => log::warn!("the window's icon is not used: {reason}"),
        }
    }
    match owner {
        Some(owner) => owned_by(builder, owner),
        None => owned(builder, open.owner_window),
    }
}

/// The page's listener for the keys the window answers itself: pressed, they do nothing in the page and go to the
/// window (`KEY <shortcut>`). `Mod` is Ctrl, or Cmd on macOS; letters and digits by their key, not the character
/// shift makes of them. Java changes the list (`view.<view>.shortcuts`).
fn shortcut_script(shortcuts: &[String]) -> String {
    let list = serde_json::to_string(shortcuts).unwrap_or_else(|_| "[]".to_string());
    format!(
        r#"(() => {{
  if (window.__webview4j_keys) return;
  window.__webview4j_keys = true;
  window.__webview4j_shortcuts = new Set({list});
  const mac = /Mac/.test(navigator.platform);
  addEventListener('keydown', (e) => {{
    const set = window.__webview4j_shortcuts;
    if (!set || set.size === 0 || e.repeat || e.isComposing) return;
    const parts = [];
    if (mac ? e.metaKey : e.ctrlKey) parts.push('Mod');
    if (mac && e.ctrlKey) parts.push('Ctrl');
    if (!mac && e.metaKey) parts.push('Meta');
    if (e.altKey) parts.push('Alt');
    if (e.shiftKey) parts.push('Shift');
    const code = e.code || '';
    const key = code.startsWith('Key') ? code.slice(3) : code.startsWith('Digit') ? code.slice(5)
      : (e.key.length === 1 ? e.key.toUpperCase() : e.key);
    parts.push(key);
    const name = parts.join('+');
    if (!set.has(name)) return;
    e.preventDefault();
    e.stopPropagation();
    if (window.ipc && typeof window.ipc.postMessage === 'function') window.ipc.postMessage('KEY ' + name);
  }}, true);
  // Escape the page did not use (not prevented, not leaving the page's own element fullscreen): the window may leave
  // fullscreen (the native side decides: only when it is fullscreen and allowed to).
  addEventListener('keydown', (e) => {{
    if (e.key !== 'Escape' || e.defaultPrevented || e.repeat || document.fullscreenElement) return;
    if (window.ipc && typeof window.ipc.postMessage === 'function') window.ipc.postMessage('WIN escape');
  }});
}})();"#
    )
}

/// An open window's settings, changed: what the body names.
/// The window's frame border in a colour (Windows 11: DWMWA_BORDER_COLOR), or the system's own again (None). Err on a
/// Windows that has no such colour.
#[cfg(windows)]
fn border_color(window: &Window, rgb: Option<[u8; 3]>) -> Result<(), String> {
    use tao::platform::windows::WindowExtWindows;
    use windows_sys::Win32::Graphics::Dwm::DwmSetWindowAttribute;
    const DWMWA_BORDER_COLOR: u32 = 34;
    const DWMWA_COLOR_DEFAULT: u32 = 0xFFFF_FFFF;
    // COLORREF: 0x00BBGGRR
    let value: u32 = rgb.map_or(DWMWA_COLOR_DEFAULT, |[r, g, b]| (b as u32) << 16 | (g as u32) << 8 | r as u32);
    let done = unsafe { DwmSetWindowAttribute(window.hwnd() as _, DWMWA_BORDER_COLOR, &value as *const u32 as _, 4) };
    if done == 0 { Ok(()) } else { Err(format!("this Windows has no border colour (0x{done:08x}): Windows 11 has")) }
}

#[cfg(not(windows))]
fn border_color(_window: &Window, _rgb: Option<[u8; 3]>) -> Result<(), String> {
    Err("a window's border colour is set on Windows 11 for now".into())
}

fn window_settings(window: &Window, body: WindowBody) -> Outcome {
    if let Some(color) = &body.border_color {
        let rgb = match color.as_slice() {
            [] => None,
            [r, g, b] => Some([*r, *g, *b]),
            _ => return Outcome::error(400, "a border colour is [r, g, b], or [] for the system's own"),
        };
        if let Err(e) = border_color(window, rgb) {
            return Outcome::error(501, e).hint("Border colours need Windows 11; elsewhere, Docking draws its highlight inside the window.");
        }
    }
    if let (Some(width), Some(height)) = (body.max_width, body.max_height) {
        window.set_max_inner_size((width > 0.0 && height > 0.0).then(|| LogicalSize::new(width, height)));
    }
    if let Some(on) = body.minimizable {
        window.set_minimizable(on);
    }
    if let Some(on) = body.maximizable {
        window.set_maximizable(on);
    }
    if let Some(on) = body.closable {
        window.set_closable(on);
    }
    if let Some(on) = body.all_workspaces {
        window.set_visible_on_all_workspaces(on);
    }
    if let Some(on) = body.content_protection {
        window.set_content_protection(on);
    }
    if let Some(on) = body.focusable {
        window.set_focusable(on);
    }
    if let Some([r, g, b, a]) = body.background_color {
        window.set_background_color(Some((r, g, b, a)));
    }
    if let Some(on) = body.skip_taskbar {
        skip_taskbar(window, on);
    }
    if let Some(on) = body.shadow {
        shadow(window, on);
    }
    if let Some(material) = body.backdrop {
        backdrop(window, material);
    }
    if let Some(count) = body.badge {
        badge(window, count);
    }
    if let Some(kind) = body.attention.as_deref() {
        window.request_user_attention(match kind {
            "critical" => Some(tao::window::UserAttentionType::Critical),
            "informational" => Some(tao::window::UserAttentionType::Informational),
            _ => None,
        });
    }
    Outcome::ok()
}

/// The taskbar button's progress (the dock's on macOS).
fn progress(window: &Window, body: &ProgressBody) -> Outcome {
    use tao::window::{ProgressBarState, ProgressState};
    let state = match body.state.as_str() {
        "none" => ProgressState::None,
        "normal" => ProgressState::Normal,
        "indeterminate" => ProgressState::Indeterminate,
        "paused" => ProgressState::Paused,
        "error" => ProgressState::Error,
        other => return Outcome::error(400, format!("a progress is none, normal, indeterminate, paused or error, not '{other}'")),
    };
    window.set_progress_bar(ProgressBarState { state: Some(state), progress: body.value.map(|v| v.min(100)), desktop_filename: None });
    Outcome::ok()
}

/// Each system's own window settings: Windows' frameless shadow and taskbar entry.
#[cfg(target_os = "windows")]
fn chrome(builder: WindowBuilder, open: &OpenRequest) -> WindowBuilder {
    use tao::platform::windows::WindowBuilderExtWindows;
    builder.with_skip_taskbar(open.skip_taskbar).with_undecorated_shadow(open.shadow)
}

/// macOS: a transparent title bar (the page under it, the title hidden, the traffic lights where asked).
#[cfg(target_os = "macos")]
fn chrome(builder: WindowBuilder, open: &OpenRequest) -> WindowBuilder {
    use tao::platform::macos::WindowBuilderExtMacOS;
    let mut builder = builder;
    if open.title_bar == payload::TitleBar::Transparent {
        builder = builder.with_titlebar_transparent(true).with_title_hidden(true).with_fullsize_content_view(true);
    }
    if let Some([x, y]) = open.traffic_lights {
        builder = builder.with_traffic_light_inset(LogicalPosition::new(x, y));
    }
    builder
}

/// Linux: the taskbar entry.
#[cfg(any(target_os = "linux", target_os = "dragonfly", target_os = "freebsd", target_os = "netbsd", target_os = "openbsd"))]
fn chrome(builder: WindowBuilder, open: &OpenRequest) -> WindowBuilder {
    use tao::platform::unix::WindowBuilderExtUnix;
    builder.with_skip_taskbar(open.skip_taskbar)
}

#[cfg(target_os = "windows")]
fn skip_taskbar(window: &Window, on: bool) {
    use tao::platform::windows::WindowExtWindows;
    if let Err(error) = window.set_skip_taskbar(on) {
        log::debug!("the taskbar entry could not change: {error}");
    }
}

#[cfg(any(target_os = "linux", target_os = "dragonfly", target_os = "freebsd", target_os = "netbsd", target_os = "openbsd"))]
fn skip_taskbar(window: &Window, on: bool) {
    use tao::platform::unix::WindowExtUnix;
    if let Err(error) = window.set_skip_taskbar(on) {
        log::debug!("the taskbar entry could not change: {error}");
    }
}

#[cfg(target_os = "macos")]
fn skip_taskbar(_window: &Window, _on: bool) {}

#[cfg(target_os = "windows")]
fn shadow(window: &Window, on: bool) {
    use tao::platform::windows::WindowExtWindows;
    window.set_undecorated_shadow(on);
}

#[cfg(not(target_os = "windows"))]
fn shadow(_window: &Window, _on: bool) {}

/// Windows 11 (22H2 and later): the system's material behind the window (DWMWA_SYSTEMBACKDROP_TYPE). Older Windows
/// ignores it; elsewhere there is none.
#[cfg(target_os = "windows")]
fn backdrop(window: &Window, material: Backdrop) {
    use tao::platform::windows::WindowExtWindows;
    use windows_sys::Win32::Graphics::Dwm::DwmSetWindowAttribute;
    // DWMWA_SYSTEMBACKDROP_TYPE, and its values: DWMSBT_NONE 1, MAINWINDOW (Mica) 2, TRANSIENTWINDOW (Acrylic) 3,
    // TABBEDWINDOW 4
    const DWMWA_SYSTEMBACKDROP_TYPE: u32 = 38;
    let value: i32 = match material {
        Backdrop::None => 1,
        Backdrop::Mica => 2,
        Backdrop::Acrylic => 3,
        Backdrop::Tabbed => 4,
    };
    let done = unsafe {
        DwmSetWindowAttribute(window.hwnd() as _, DWMWA_SYSTEMBACKDROP_TYPE, &value as *const i32 as _, std::mem::size_of::<i32>() as u32)
    };
    if done != 0 {
        log::debug!("the window's backdrop could not be set (Windows 11 22H2 or later): {done:#x}");
    }
}

#[cfg(not(target_os = "windows"))]
fn backdrop(_window: &Window, _material: Backdrop) {}

/// The badge: macOS's dock label. (Windows draws a badge as an overlay icon: to come.)
#[cfg(target_os = "macos")]
fn badge(window: &Window, count: i64) {
    use tao::platform::macos::WindowExtMacOS;
    window.set_badge_label((count > 0).then(|| count.to_string()));
}

#[cfg(not(target_os = "macos"))]
fn badge(_window: &Window, _count: i64) {}

/// Owned by another of the app's windows: above it, minimized with it, no taskbar entry of its own. Unlike a
/// docked `ownerWindow`, it keeps its frame's shadow: it is a window of its own (a tool window, a dialog).
#[cfg(target_os = "windows")]
fn owned_by(builder: WindowBuilder, owner: &Window) -> WindowBuilder {
    use tao::platform::windows::{WindowBuilderExtWindows, WindowExtWindows};
    builder.with_owner_window(owner.hwnd()).with_skip_taskbar(true)
}

/// A child window of the owner's (NSWindow addChildWindow): it moves with it and stays above it.
#[cfg(target_os = "macos")]
fn owned_by(builder: WindowBuilder, owner: &Window) -> WindowBuilder {
    use tao::platform::macos::{WindowBuilderExtMacOS, WindowExtMacOS};
    builder.with_parent_window(owner.ns_window())
}

/// Transient for the owner's window: the window manager keeps it above it.
#[cfg(any(target_os = "linux", target_os = "dragonfly", target_os = "freebsd", target_os = "netbsd", target_os = "openbsd"))]
fn owned_by(builder: WindowBuilder, owner: &Window) -> WindowBuilder {
    use tao::platform::unix::{WindowBuilderExtUnix, WindowExtUnix};
    builder.with_transient_for(owner.gtk_window()).with_skip_taskbar(true)
}

/// `view.<view>.devtools`: opens (`{"open":true}`) or closes (`{"open":false}`) the view's developer tools, or with
/// no payload only says. Answers `{"open": true | false | null}`: null where the system cannot tell (Windows,
/// whose WebView2 can open its tools but neither close them nor say whether they are open).
fn devtools(instance: &Instance, payload: &[u8]) -> Outcome {
    if !instance.devtools {
        return Outcome::error(400, "its developer tools are off")
            .kind("unsupported")
            .hint("Open the window with WindowOptions.devtools(true), or run the app with -Dswaswa.devtools=true.");
    }
    #[derive(serde::Deserialize)]
    struct Body {
        open: Option<bool>,
    }
    let body = if payload.is_empty() {
        Body { open: None }
    } else {
        match serde_json::from_slice::<Body>(payload) {
            Ok(body) => body,
            Err(e) => return Outcome::error(400, format!("devtools takes {{\"open\": true|false}}: {e}")),
        }
    };
    match body.open {
        Some(true) => instance.webview.open_devtools(),
        Some(false) => instance.webview.close_devtools(),
        None => {}
    }
    let open = if cfg!(target_os = "windows") { serde_json::Value::Null } else { serde_json::Value::Bool(instance.webview.is_devtools_open()) };
    Outcome::json(&json!({ "open": open }))
}

/// The window's native handle, for code that talks to the system itself (an HWND, an NSWindow pointer).
#[cfg(target_os = "windows")]
fn native_handle(window: &Window) -> Outcome {
    use tao::platform::windows::WindowExtWindows;
    Outcome::json(&json!({"kind": "hwnd", "handle": window.hwnd() as i64}))
}

#[cfg(target_os = "macos")]
fn native_handle(window: &Window) -> Outcome {
    use tao::platform::macos::WindowExtMacOS;
    Outcome::json(&json!({"kind": "nswindow", "handle": window.ns_window() as usize as i64}))
}

#[cfg(not(any(target_os = "windows", target_os = "macos")))]
fn native_handle(_window: &Window) -> Outcome {
    Outcome::error(501, "a window's native handle is not given on this system yet")
        .hint("A window's native handle is given on Windows for now.")
}

/// The icon in the title bar and the window switcher, and on Windows the taskbar's too.
#[cfg(target_os = "windows")]
fn with_icon(builder: WindowBuilder, icon: tao::window::Icon) -> WindowBuilder {
    use tao::platform::windows::WindowBuilderExtWindows;
    builder.with_taskbar_icon(Some(icon.clone())).with_window_icon(Some(icon))
}

#[cfg(not(target_os = "windows"))]
fn with_icon(builder: WindowBuilder, icon: tao::window::Icon) -> WindowBuilder {
    builder.with_window_icon(Some(icon))
}

#[cfg(target_os = "windows")]
fn owned(builder: WindowBuilder, owner: Option<isize>) -> WindowBuilder {
    use tao::platform::windows::WindowBuilderExtWindows;
    match owner {
        // An owned window stays above its owner and needs no taskbar entry of its own.
        Some(owner) => builder
            .with_owner_window(owner)
            .with_skip_taskbar(true)
            // A docked view must end exactly at its bounds: no drop shadow around it.
            .with_undecorated_shadow(false)
            // Shown without taking the focus from the window it is docked in (a JavaFX or Swing app's).
            .with_focused(false),
        None => builder,
    }
}

#[cfg(not(target_os = "windows"))]
fn owned(builder: WindowBuilder, _owner: Option<isize>) -> WindowBuilder {
    builder
}

/// Places the window so that its outer edge is exactly the given rectangle. An undecorated
/// window on Windows keeps frame metrics that `set_inner_size` adds on top of the size asked for.
fn set_bounds(window: &Window, x: i32, y: i32, width: u32, height: u32) {
    window.set_outer_position(PhysicalPosition::new(x, y));
    window.set_inner_size(PhysicalSize::new(width, height));
    let outer = window.outer_size();
    if outer.width != width || outer.height != height {
        let inner_width = (2 * width).saturating_sub(outer.width).max(1);
        let inner_height = (2 * height).saturating_sub(outer.height).max(1);
        window.set_inner_size(PhysicalSize::new(inner_width, inner_height));
    }
}

/// The view's cookies as Java reads them (Cookie.java), values included: they are credentials, never logged.
fn cookie_records(cookies: Vec<wry::cookie::Cookie<'static>>) -> Vec<CookieRecord> {
    cookies
        .into_iter()
        .map(|cookie| CookieRecord {
            name: cookie.name().to_string(),
            value: cookie.value().to_string(),
            domain: cookie.domain().unwrap_or_default().to_string(),
            path: cookie.path().unwrap_or_default().to_string(),
            http_only: cookie.http_only().unwrap_or(false),
            secure: cookie.secure().unwrap_or(false),
        })
        .collect()
}

/// A cookie without a domain is the page's own site's (WebView2 cannot set one without); 400 when the page has
/// no site (about:blank, a data: page).
fn on_the_pages_site(instance: &Instance, mut body: CookieBody) -> Result<CookieBody, Outcome> {
    if body.domain.as_deref().is_some_and(|d| !d.is_empty()) {
        return Ok(body);
    }
    let host = instance.webview.url().ok().and_then(|url| host_of(&url));
    match host {
        Some(host) => {
            body.domain = Some(host);
            Ok(body)
        }
        None => Err(Outcome::error(400, "this page has no site of its own (about:blank, data:): give the cookie a domain")),
    }
}

/// The host of an http(s) URL ("https://a.example.com:8080/x" → "a.example.com"); None for any other.
fn host_of(url: &str) -> Option<String> {
    let rest = url.strip_prefix("https://").or_else(|| url.strip_prefix("http://"))?;
    let authority = rest.split(['/', '?', '#']).next()?;
    let authority = authority.rsplit('@').next()?;
    let host = if authority.starts_with('[') { authority.split(']').next().map(|h| &h[1..])? } else { authority.split(':').next()? };
    (!host.is_empty()).then(|| host.to_ascii_lowercase())
}

#[cfg(test)]
mod host_tests {
    use super::host_of;

    #[test]
    fn a_pages_host_is_its_cookies_domain() {
        assert_eq!(host_of("https://webview4j.localhost/app/index.html").as_deref(), Some("webview4j.localhost"));
        assert_eq!(host_of("http://user@A.Example.com:8080/x?y#z").as_deref(), Some("a.example.com"));
        assert_eq!(host_of("about:blank"), None);
        assert_eq!(host_of("data:text/html,x"), None);
        assert_eq!(host_of("https:///x"), None);
    }
}
