//! The event loop and everything done on it. Tao and Wry objects may only be touched on the
//! thread running the loop, so [`submit`] and [`call`] (any other thread) send the action through
//! an [`EventLoopProxy`]; [`run`] blocks its thread until [`quit`].
//!
//! Everything the loop reports goes through one callback, as JSON:
//!
//! ```text
//! {"type":"ready"}                                   the loop runs; actions can be submitted
//! {"type":"reply","request":42,"ok":true,...}        the answer to a submitted action
//! {"type":"closed","view":"wv-1"}                    a window closed, by the user or an action
//! {"type":"log","source":"native",...}               a log record (see logging.rs)
//! {"type":"api-call" | "click" | "dom-event" | "websocket" | "log",...}   page reports (inspector.rs)
//! ```

use std::cell::Cell;
use std::collections::HashMap;
use std::ffi::{CString, c_char};
use std::sync::mpsc::{self, Sender};
use std::sync::{Arc, Mutex};
use std::time::Duration;

use serde_json::{Value, json};
use tao::dpi::{LogicalPosition, LogicalSize, PhysicalPosition, PhysicalSize};
use tao::event::{Event, StartCause, WindowEvent};
use tao::event_loop::{ControlFlow, EventLoopBuilder, EventLoopProxy, EventLoopWindowTarget};
use tao::platform::run_return::EventLoopExtRunReturn;
use tao::window::{Window, WindowBuilder, WindowId};
use wry::{WebContext, WebView, WebViewBuilder};

use crate::action::{Action, InspectorAction, WebViewAction, WindowAction};
use crate::inspector::{self, Capture};
use crate::payload::{
    self, BoundsBody, CaptureBody, CookieRecord, HtmlBody, OpenRequest, PositionBody, Reply, ScriptBody, SizeBody,
    SocketBody, TitleBody, UrlBody, WebViewInfo,
};

/// Receives each event as a NUL-terminated JSON string, valid only during the call. Mostly called
/// on the event loop thread (log records may come from any thread): it must return quickly and
/// must not call [`call`], which would wait on itself.
pub type EventCallback = extern "C" fn(*const c_char);

/// How long [`call`] waits for the event loop to answer.
const REPLY_TIMEOUT: Duration = Duration::from_secs(10);

/// Where an action's reply goes: back to a waiting [`call`], or out as a `reply` event.
#[derive(Clone)]
pub enum ReplyTo {
    Caller(Sender<Reply>),
    Event(u64),
}

impl ReplyTo {
    fn send(&self, reply: Reply) {
        match self {
            ReplyTo::Caller(sender) => {
                let _ = sender.send(reply);
            }
            ReplyTo::Event(request) => emit(reply.to_event(*request)),
        }
    }
}

/// What other threads send to the event loop.
pub enum Request {
    Call { action: Action, view: Option<String>, payload: String, reply: ReplyTo },
    Quit,
}

/// Set while the loop runs: how other threads reach it.
static PROXY: Mutex<Option<EventLoopProxy<Request>>> = Mutex::new(None);

/// Set while the loop runs: where events go.
static ON_EVENT: Mutex<Option<EventCallback>> = Mutex::new(None);

thread_local! {
    static ON_EVENT_LOOP: Cell<bool> = const { Cell::new(false) };
}

/// Sends an event to the callback [`run`] was given; dropped when no loop runs.
pub(crate) fn emit(event: Value) {
    let callback = *ON_EVENT.lock().unwrap();
    if let Some(callback) = callback {
        let json = CString::new(event.to_string()).expect("JSON has no NUL byte");
        callback(json.as_ptr());
    }
}

struct Instance {
    webview: WebView,
    window: Window,
    title: String,
    capture: Arc<Capture>,
    // Keep the context alive until after its webview has been dropped.
    context: Option<Box<WebContext>>,
}

#[derive(Default)]
struct State {
    views: HashMap<String, Instance>,
    ids_by_window: HashMap<WindowId, String>,
    next_id: u64,
}

/// Runs the event loop on this thread until [`quit`]; returns its exit code. On macOS this must
/// be the process's first thread. Emits `{"type":"ready"}` once actions can be submitted.
pub fn run(on_event: Option<EventCallback>) -> Result<i32, String> {
    let mut proxy = PROXY.lock().unwrap();
    if proxy.is_some() {
        return Err("the event loop is already running".into());
    }
    let mut builder = EventLoopBuilder::<Request>::with_user_event();
    any_thread(&mut builder);
    let mut event_loop = builder.build();
    *proxy = Some(event_loop.create_proxy());
    *ON_EVENT.lock().unwrap() = on_event;
    drop(proxy);
    crate::logging::install();

    let _running = Running::enter();
    let mut state = State::default();
    Ok(event_loop.run_return(move |event, target, control_flow| {
        *control_flow = ControlFlow::Wait;
        match event {
            Event::NewEvents(StartCause::Init) => emit(json!({"type": "ready"})),
            Event::UserEvent(Request::Call { action, view, payload, reply }) => {
                handle(action, view, &payload, reply, target, &mut state)
            }
            Event::UserEvent(Request::Quit) => {
                // Webviews before their windows and contexts, while the loop is still alive.
                state.ids_by_window.clear();
                state.views.clear();
                *control_flow = ControlFlow::Exit;
            }
            Event::WindowEvent { window_id, event: WindowEvent::CloseRequested, .. } => {
                if let Some(id) = state.ids_by_window.remove(&window_id) {
                    state.views.remove(&id);
                    emit(json!({"type": "closed", "view": id}));
                }
            }
            _ => {}
        }
    }))
}

/// Marks this thread as the event loop's while the loop runs, and afterwards clears the proxy
/// and the callback, even if the loop unwinds.
struct Running;

impl Running {
    fn enter() -> Self {
        ON_EVENT_LOOP.set(true);
        Running
    }
}

impl Drop for Running {
    fn drop(&mut self) {
        ON_EVENT_LOOP.set(false);
        *PROXY.lock().unwrap() = None;
        *ON_EVENT.lock().unwrap() = None;
    }
}

/// Hands an action to the event loop and returns at once; the reply goes to `reply`.
/// Callable from any thread, the loop's own included. Err when no loop takes it.
pub fn submit(action: Action, view: Option<String>, payload: String, reply: ReplyTo) -> Result<(), Reply> {
    let Some(proxy) = PROXY.lock().unwrap().clone() else {
        return Err(Reply::Error("the event loop is not running".into()));
    };
    proxy
        .send_event(Request::Call { action, view, payload, reply })
        .map_err(|_| Reply::Error("the event loop is gone".into()))
}

/// Performs an action and waits for its reply. Callable from any thread but the event loop's.
pub fn call(action: Action, view: Option<String>, payload: String) -> Reply {
    if ON_EVENT_LOOP.get() {
        return Reply::Error("actions cannot be called from the event loop thread (inside an event callback)".into());
    }
    let (sender, receiver) = mpsc::channel();
    if let Err(error) = submit(action, view, payload, ReplyTo::Caller(sender)) {
        return error;
    }
    receiver
        .recv_timeout(REPLY_TIMEOUT)
        .unwrap_or_else(|_| Reply::Error("timed out waiting for the event loop".into()))
}

/// Asks the event loop to close every window and return from [`run`]. False if it is not running.
pub fn quit() -> bool {
    match PROXY.lock().unwrap().as_ref() {
        Some(proxy) => proxy.send_event(Request::Quit).is_ok(),
        None => false,
    }
}

fn handle(
    action: Action,
    view: Option<String>,
    payload: &str,
    reply: ReplyTo,
    target: &EventLoopWindowTarget<Request>,
    state: &mut State,
) {
    let response = match action {
        Action::Window(WindowAction::Open) => match payload::parse::<OpenRequest>(payload) {
            Ok(open) => open_webview(open, target, state),
            Err(error) => error,
        },
        Action::Window(WindowAction::List) => Reply::WebViews(list(state)),
        _ => {
            let Some(id) = view else {
                return reply.send(Reply::Error("this action needs a view id".into()));
            };
            match on_view(action, &id, payload, &reply, state) {
                Some(response) => response,
                None => return, // answered later, by a script callback
            }
        }
    };
    reply.send(response);
}

/// An action on one open view. None when a script callback replies later (eval, websocket-send).
fn on_view(action: Action, id: &str, payload: &str, reply: &ReplyTo, state: &mut State) -> Option<Reply> {
    if action == Action::Window(WindowAction::Close) {
        return Some(match state.views.remove(id) {
            Some(instance) => {
                state.ids_by_window.remove(&instance.window.id());
                drop(instance); // dropping the window closes it
                emit(json!({"type": "closed", "view": id}));
                Reply::Ok
            }
            None => not_found(id),
        });
    }
    let Some(instance) = state.views.get_mut(id) else {
        return Some(not_found(id));
    };
    Some(match action {
        Action::Window(WindowAction::SetTitle) => match payload::parse::<TitleBody>(payload) {
            Ok(body) => {
                instance.window.set_title(&body.title);
                instance.title = body.title;
                Reply::Ok
            }
            Err(error) => error,
        },
        Action::Window(WindowAction::SetSize) => match payload::parse::<SizeBody>(payload) {
            Ok(body) => {
                instance.window.set_inner_size(LogicalSize::new(body.width, body.height));
                Reply::Ok
            }
            Err(error) => error,
        },
        Action::Window(WindowAction::SetPosition) => match payload::parse::<PositionBody>(payload) {
            Ok(body) => {
                instance.window.set_outer_position(LogicalPosition::new(body.x, body.y));
                Reply::Ok
            }
            Err(error) => error,
        },
        Action::Window(WindowAction::SetBounds) => match payload::parse::<BoundsBody>(payload) {
            Ok(body) => {
                set_bounds(&instance.window, body.x, body.y, body.width.max(1), body.height.max(1));
                Reply::Ok
            }
            Err(error) => error,
        },
        Action::Window(WindowAction::Show) => {
            instance.window.set_visible(true);
            Reply::Ok
        }
        Action::Window(WindowAction::Hide) => {
            instance.window.set_visible(false);
            Reply::Ok
        }
        Action::Window(WindowAction::Focus) => {
            instance.window.set_focus();
            Reply::Ok
        }
        Action::WebView(WebViewAction::Navigate) => match payload::parse::<UrlBody>(payload) {
            Ok(body) => result_reply(instance.webview.load_url(&body.url)),
            Err(error) => error,
        },
        Action::WebView(WebViewAction::LoadHtml) => match payload::parse::<HtmlBody>(payload) {
            Ok(body) => result_reply(instance.webview.load_html(&body.html)),
            Err(error) => error,
        },
        Action::WebView(WebViewAction::Eval) => match payload::parse::<ScriptBody>(payload) {
            Ok(body) => {
                let later = reply.clone();
                match instance
                    .webview
                    .evaluate_script_with_callback(&body.script, move |value| later.send(Reply::EvalResult(value)))
                {
                    Ok(()) => return None,
                    Err(e) => Reply::Error(e.to_string()),
                }
            }
            Err(error) => error,
        },
        Action::WebView(WebViewAction::Cookies) => match payload::parse::<UrlBody>(payload) {
            Ok(body) => match instance.webview.cookies_for_url(&body.url) {
                Ok(cookies) => Reply::Cookies(
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
                        .collect(),
                ),
                Err(error) => Reply::Error(error.to_string()),
            },
            Err(error) => error,
        },
        Action::WebView(WebViewAction::WebSocketSend) => match payload::parse::<SocketBody>(payload) {
            Ok(body) => {
                let later = reply.clone();
                match instance.webview.evaluate_script_with_callback(&send_script(&body), move |result| {
                    // The script answers null when sent, or why it could not send.
                    later.send(match serde_json::from_str::<Value>(&result) {
                        Ok(Value::String(error)) => Reply::Error(error),
                        _ => Reply::Ok,
                    })
                }) {
                    Ok(()) => return None,
                    Err(e) => Reply::Error(e.to_string()),
                }
            }
            Err(error) => error,
        },
        Action::Inspector(InspectorAction::Capture) => match payload::parse::<CaptureBody>(payload) {
            Ok(body) => {
                instance.capture.apply(body);
                Reply::Ok
            }
            Err(error) => error,
        },
        Action::Window(WindowAction::Open | WindowAction::List | WindowAction::Close) => {
            unreachable!("handled before a view is looked up")
        }
    })
}

/// The page script that sends a text frame on the inspector's socket number `socket`.
fn send_script(body: &SocketBody) -> String {
    let socket = body.socket;
    let data = Value::String(body.data.clone());
    format!(
        "(() => {{ const ws = window.__WEB_INSPECTOR_SOCKETS__ && window.__WEB_INSPECTOR_SOCKETS__.get({socket}); \
         if (!ws) return 'no open socket {socket}'; \
         if (ws.readyState !== 1) return 'socket {socket} is not open'; \
         ws.send({data}); return null; }})()"
    )
}

fn list(state: &State) -> Vec<WebViewInfo> {
    state
        .views
        .iter()
        .map(|(id, instance)| WebViewInfo {
            id: id.clone(),
            title: instance.title.clone(),
            url: instance.webview.url().unwrap_or_default(),
            data_directory: instance.context.as_ref().and_then(|c| c.data_directory().map(|p| p.to_path_buf())),
        })
        .collect()
}

fn open_webview(open: OpenRequest, target: &EventLoopWindowTarget<Request>, state: &mut State) -> Reply {
    if let Some(path) = &open.data_directory {
        if !path.is_absolute() {
            return Reply::Error("dataDirectory must be absolute".into());
        }
        if let Err(e) = std::fs::create_dir_all(path) {
            return Reply::Error(format!("cannot create browser profile: {e}"));
        }
    }
    let window = match window_builder(&open).build(target) {
        Ok(window) => window,
        Err(e) => return Reply::Error(format!("failed to create the window: {e}")),
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
    builder = builder
        .with_initialization_script(&inspector::initialization_script())
        .with_ipc_handler(move |request| {
            if let Some(event) = inspector::event(request.body(), &view_id, &view_capture) {
                emit(event);
            }
        });
    if let Some(script) = &open.initialization_script {
        builder = builder.with_initialization_script(script);
    }
    if let Some(url) = &open.url {
        builder = builder.with_url(url);
    } else if let Some(html) = &open.html {
        builder = builder.with_html(html);
    }

    let webview = match build_webview(builder, &window) {
        Ok(webview) => webview,
        Err(e) => return Reply::Error(format!("failed to create the webview: {e}")),
    };

    state.ids_by_window.insert(window.id(), id.clone());
    state.views.insert(id.clone(), Instance { window, webview, title: open.title, capture, context });
    Reply::Opened { id }
}

#[cfg(target_os = "windows")]
fn any_thread(builder: &mut EventLoopBuilder<Request>) {
    use tao::platform::windows::EventLoopBuilderExtWindows;
    builder.with_any_thread(true);
}

#[cfg(not(any(target_os = "windows", target_os = "macos", target_os = "ios", target_os = "android")))]
fn any_thread(builder: &mut EventLoopBuilder<Request>) {
    use tao::platform::unix::EventLoopBuilderExtUnix;
    builder.with_any_thread(true);
}

/// macOS: the loop must run on the process's first thread; there is no opting out.
#[cfg(any(target_os = "macos", target_os = "ios", target_os = "android"))]
fn any_thread(_builder: &mut EventLoopBuilder<Request>) {}

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

fn result_reply(result: Result<(), wry::Error>) -> Reply {
    match result {
        Ok(()) => Reply::Ok,
        Err(e) => Reply::Error(e.to_string()),
    }
}

fn not_found(id: &str) -> Reply {
    Reply::Error(format!("no webview with id '{id}'"))
}

fn window_builder(open: &OpenRequest) -> WindowBuilder {
    let builder = WindowBuilder::new()
        .with_title(&open.title)
        .with_inner_size(LogicalSize::new(open.width, open.height))
        .with_resizable(open.resizable)
        .with_decorations(open.decorations)
        .with_visible(!open.headless);
    owned(builder, open.owner_window)
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
            .with_undecorated_shadow(false),
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
