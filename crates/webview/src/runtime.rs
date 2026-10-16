//! The event loop and everything done on it. Tao and Wry objects may only be touched on the
//! thread running the loop, so [`send`] (any thread) hands each message to it through an
//! [`EventLoopProxy`]; [`run`] blocks its thread until `app.quit`.
//!
//! Messages in are routed by subject, messages out go to the callback [`run`] was given, all as
//! frames (PROTOCOL.md). What each subject means is in PROTOCOL.md §2.

use std::collections::HashMap;
use std::sync::{Arc, Mutex};

use serde_json::{Value, json};
use tao::dpi::{LogicalPosition, LogicalSize, PhysicalPosition, PhysicalSize};
use tao::event::{Event, StartCause, WindowEvent};
use tao::event_loop::{ControlFlow, EventLoopBuilder, EventLoopProxy, EventLoopWindowTarget};
use tao::platform::run_return::EventLoopExtRunReturn;
use tao::window::{Window, WindowBuilder, WindowId};
use wry::{WebContext, WebView, WebViewBuilder};

use crate::bridge;
use crate::frame::Message;
use crate::inspector::{self, Capture};
use crate::payload::{self, BoundsBody, CaptureBody, CookieRecord, OpenRequest, Outcome, PositionBody, SizeBody, WebViewInfo};

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
    window: Window,
    title: String,
    capture: Arc<Capture>,
    link: Arc<Mutex<bridge::Link>>,
    /// Small pushes waiting to go out as one script: at the end of this round of the event loop,
    /// before the next call answer, or once they add up to PUSH_BATCH.
    pushes: Vec<String>,
    pushes_size: usize,
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
}

impl Drop for Instance {
    /// However a window goes (close, the user, app.quit), the page's waiting requests are answered
    /// while the webview still exists.
    fn drop(&mut self) {
        self.link.lock().unwrap().close();
    }
}

#[derive(Default)]
struct State {
    views: HashMap<String, Instance>,
    ids_by_window: HashMap<WindowId, String>,
    next_id: u64,
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
            Event::WindowEvent { window_id, event: WindowEvent::CloseRequested, .. } => {
                if let Some(id) = state.ids_by_window.remove(&window_id) {
                    state.views.remove(&id);
                    emit(&Message::new(format!("view.{id}.closed")));
                }
            }
            // This round's messages are handled: the pushes they made go out, one script per page.
            Event::MainEventsCleared => state.views.values_mut().for_each(Instance::flush_pushes),
            _ => {}
        }
    }))
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
        ["app", "play"] => crate::sound::play(&message.payload),
        ["app", "quit"] => {
            // Webviews before their windows and contexts, while the loop is still alive.
            state.ids_by_window.clear();
            state.views.clear();
            *control_flow = ControlFlow::Exit;
            Outcome::ok()
        }
        ["view", view, rest @ ..] => match on_view(view, rest, &message, &reply, state) {
            Some(outcome) => outcome,
            None => return, // answered later, or not at all (call answers)
        },
        _ => Outcome::error(404, format!("no subject '{subject}'")),
    };
    reply.send(outcome);
}

/// A message about one view. None when a script callback replies later (eval, websocket send),
/// or nobody waits for a reply (the answer to a page's call).
fn on_view(view: &str, rest: &[&str], message: &Message, reply: &ReplyTo, state: &mut State) -> Option<Outcome> {
    let payload = message.payload.as_slice();
    if rest == ["close"] {
        return Some(match state.views.remove(view) {
            Some(instance) => {
                state.ids_by_window.remove(&instance.window.id());
                drop(instance); // dropping the window closes it
                emit(&Message::new(format!("view.{view}.closed")));
                Outcome::ok()
            }
            None => not_found(view),
        });
    }
    let Some(instance) = state.views.get_mut(view) else {
        return Some(not_found(view));
    };
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
        ["eval"] => match payload::text(payload) {
            Ok(script) => {
                let later = reply.clone();
                let evaluated = instance.webview.evaluate_script_with_callback(script, move |value| {
                    // The webview hands back the value already as JSON; empty when undefined.
                    let body = if value.trim().is_empty() { b"null".to_vec() } else { value.into_bytes() };
                    later.send(Outcome { status: 200, content_type: "application/json", body });
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
                Ok(cookies) => Outcome::json(
                    &cookies
                        .into_iter()
                        .map(|cookie| CookieRecord {
                            name: cookie.name().to_string(),
                            value: cookie.value().to_string(),
                            domain: cookie.domain().unwrap_or_default().to_string(),
                            path: cookie.path().unwrap_or_default().to_string(),
                            http_only: cookie.http_only().unwrap_or(false),
                            secure: cookie.secure().unwrap_or(false),
                        })
                        .collect::<Vec<_>>(),
                ),
                Err(error) => Outcome::error(500, error.to_string()),
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
            instance.window.set_focus();
            Outcome::ok()
        }
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

/// What a page sends over IPC: a small call (a frame, bridge.rs), or an inspector report.
fn from_page(request: &wry::http::Request<String>, view: &str, capture: &Capture, link: &Mutex<bridge::Link>) {
    let raw = request.body();
    if raw.starts_with("MSG ") {
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
            return Outcome::error(500, format!("cannot create browser profile: {e}"));
        }
    }
    let window = match window_builder(&open).build(target) {
        Ok(window) => window,
        Err(e) => return Outcome::error(500, format!("failed to create the window: {e}")),
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
    let origins = open.origins.clone().unwrap_or_else(|| match &open.url {
        Some(url) => bridge::origin_of(url).into_iter().collect(),
        None => vec!["null".to_string()],
    });
    let link = bridge::Link::with_assets(&id, origins, open.assets.clone());
    let page_link = Arc::clone(&link);
    let ipc_link = Arc::clone(&link);
    builder = https_scheme(builder)
        .with_initialization_script(&bridge::script())
        .with_initialization_script(&inspector::initialization_script())
        .with_ipc_handler(move |request| from_page(&request, &view_id, &view_capture, &ipc_link))
        .with_asynchronous_custom_protocol(bridge::SCHEME.into(), move |_, request, responder| {
            let call = page_link.lock().unwrap().request(request, responder);
            if let Some(call) = call {
                emit(&call);
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
        Err(e) => return Outcome::error(500, format!("failed to create the webview: {e}")),
    };

    state.ids_by_window.insert(window.id(), id.clone());
    let instance = Instance { window, webview, title: open.title, capture, link, pushes: Vec::new(), pushes_size: 0, context };
    state.views.insert(id.clone(), instance);
    Outcome::json(&json!({"view": id}))
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

fn not_found(view: &str) -> Outcome {
    Outcome::error(404, format!("no view '{view}'"))
}

fn window_builder(open: &OpenRequest) -> WindowBuilder {
    let mut builder = WindowBuilder::new()
        .with_title(&open.title)
        .with_inner_size(LogicalSize::new(open.width, open.height))
        .with_resizable(open.resizable)
        .with_decorations(open.decorations)
        .with_visible(!open.headless);
    if let Some(png) = &open.icon {
        // A picture that is not one leaves the program's icon: not worth refusing the window for.
        match crate::icon::from_png_base64(png) {
            Ok(icon) => builder = with_icon(builder, icon),
            Err(reason) => log::warn!("the window's icon is not used: {reason}"),
        }
    }
    owned(builder, open.owner_window)
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
