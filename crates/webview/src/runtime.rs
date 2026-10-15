//! The event loop and everything done on it. Tao and Wry objects may only be touched on the
//! thread running the loop, so [`call`] (any other thread) sends the action through an
//! [`EventLoopProxy`] and waits for the reply; [`run`] blocks its thread until [`quit`].

use std::cell::Cell;
use std::collections::HashMap;
use std::ffi::{CString, c_char};
use std::sync::Mutex;
use std::sync::mpsc::{self, Sender};
use std::time::Duration;

use tao::dpi::{LogicalPosition, LogicalSize, PhysicalPosition, PhysicalSize};
use tao::event::{Event, StartCause, WindowEvent};
use tao::event_loop::{ControlFlow, EventLoopBuilder, EventLoopProxy, EventLoopWindowTarget};
use tao::platform::run_return::EventLoopExtRunReturn;
use tao::window::{Window, WindowBuilder, WindowId};
use wry::{WebContext, WebView, WebViewBuilder};

use crate::action::{Action, InspectorAction, WebViewAction, WindowAction};
use crate::inspector;
use crate::payload::{
    self, ApiCallRecord, BoundsBody, CookieRecord, HtmlBody, OpenRequest, PositionBody, Reply, ScriptBody, SizeBody,
    TitleBody, UrlBody, WebViewInfo,
};

/// Receives each event as a NUL-terminated JSON string, on the event loop thread. The string is
/// only valid during the call. Must not block, and must not call [`call`] (it would wait on itself).
pub type EventCallback = extern "C" fn(*const c_char);

/// How long [`call`] waits for the event loop to answer.
const REPLY_TIMEOUT: Duration = Duration::from_secs(10);

/// What other threads send to the event loop.
pub enum Request {
    Call { action: Action, view: Option<String>, payload: String, reply: Sender<Reply> },
    Quit,
}

/// Set while the loop runs: how other threads reach it.
static PROXY: Mutex<Option<EventLoopProxy<Request>>> = Mutex::new(None);

/// What the inspector captured, drained by the inspector actions.
pub(crate) static API_CALLS: Mutex<Vec<ApiCallRecord>> = Mutex::new(Vec::new());
pub(crate) static DOM_EVENTS: Mutex<Vec<ApiCallRecord>> = Mutex::new(Vec::new());

thread_local! {
    static ON_EVENT_LOOP: Cell<bool> = const { Cell::new(false) };
}

struct Instance {
    webview: WebView,
    window: Window,
    title: String,
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
/// be the process's first thread. Emits `{"type":"ready"}` once [`call`] can be used.
pub fn run(on_event: Option<EventCallback>) -> Result<i32, String> {
    let mut proxy = PROXY.lock().unwrap();
    if proxy.is_some() {
        return Err("the event loop is already running".into());
    }
    let mut builder = EventLoopBuilder::<Request>::with_user_event();
    any_thread(&mut builder);
    let mut event_loop = builder.build();
    *proxy = Some(event_loop.create_proxy());
    drop(proxy);

    let _running = Running::enter();
    let emit = move |event: serde_json::Value| {
        if let Some(callback) = on_event {
            let json = CString::new(event.to_string()).expect("JSON has no NUL byte");
            callback(json.as_ptr());
        }
    };
    let mut state = State::default();
    Ok(event_loop.run_return(move |event, target, control_flow| {
        *control_flow = ControlFlow::Wait;
        match event {
            Event::NewEvents(StartCause::Init) => emit(serde_json::json!({"type": "ready"})),
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
                    emit(serde_json::json!({"type": "closed", "id": id}));
                }
            }
            _ => {}
        }
    }))
}

/// Marks this thread as the event loop's while the loop runs, and clears the proxy after it,
/// even if the loop unwinds.
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
    }
}

/// Performs an action and waits for its reply. Callable from any thread but the event loop's.
pub fn call(action: Action, view: Option<String>, payload: String) -> Reply {
    if ON_EVENT_LOOP.get() {
        return Reply::Error("actions cannot be called from the event loop thread (inside an event callback)".into());
    }
    match action {
        // The inspector's queues need no window: drained right here.
        Action::Inspector(InspectorAction::ApiCalls) => {
            return Reply::ApiCalls(std::mem::take(&mut *API_CALLS.lock().unwrap()));
        }
        Action::Inspector(InspectorAction::DomEvents) => {
            return Reply::DomEvents(std::mem::take(&mut *DOM_EVENTS.lock().unwrap()));
        }
        _ => {}
    }
    let Some(proxy) = PROXY.lock().unwrap().clone() else {
        return Reply::Error("the event loop is not running".into());
    };
    let (reply, receiver) = mpsc::channel();
    if proxy.send_event(Request::Call { action, view, payload, reply }).is_err() {
        return Reply::Error("the event loop is gone".into());
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
    reply: Sender<Reply>,
    target: &EventLoopWindowTarget<Request>,
    state: &mut State,
) {
    let response = match action {
        Action::Window(WindowAction::Open) => match payload::parse::<OpenRequest>(payload) {
            Ok(open) => open_webview(open, target, state),
            Err(error) => error,
        },
        Action::Window(WindowAction::List) => Reply::WebViews(list(state)),
        Action::Inspector(_) => Reply::Error("inspector actions are answered off the event loop".into()),
        _ => {
            let Some(id) = view else {
                let _ = reply.send(Reply::Error("this action needs a view id".into()));
                return;
            };
            match on_view(action, &id, payload, &reply, state) {
                Some(response) => response,
                None => return, // eval: its callback delivers the reply
            }
        }
    };
    let _ = reply.send(response);
}

/// An action on one open view. None when the reply comes later (eval).
fn on_view(action: Action, id: &str, payload: &str, reply: &Sender<Reply>, state: &mut State) -> Option<Reply> {
    if action == Action::Window(WindowAction::Close) {
        return Some(match state.views.remove(id) {
            Some(instance) => {
                state.ids_by_window.remove(&instance.window.id());
                drop(instance); // dropping the window closes it
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
                match instance.webview.evaluate_script_with_callback(&body.script, move |value| {
                    let _ = later.send(Reply::EvalResult(value));
                }) {
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
        Action::Window(WindowAction::Open | WindowAction::List | WindowAction::Close) | Action::Inspector(_) => {
            unreachable!("handled before a view is looked up")
        }
    })
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
    let view_id = id.clone();
    builder = builder
        .with_initialization_script(&inspector::initialization_script())
        .with_ipc_handler(move |request| {
            inspector::capture(request.body(), &view_id, &API_CALLS);
            inspector::capture_dom(request.body(), &view_id, &DOM_EVENTS);
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
    state.views.insert(id.clone(), Instance { window, webview, title: open.title, context });
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
