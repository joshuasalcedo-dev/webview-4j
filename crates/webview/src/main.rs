//! swaswa-webview webview sidecar.
//!
//! Binds the HTTP API from [`api`] on 127.0.0.1 (`--port N`, default 0 =
//! ephemeral), announces the bound port on stdout as
//! `SWASWA_WEBVIEW_PORT=<port>`, then runs the tao event loop on the main
//! thread. The Java side reads the handshake line and talks to the API; the
//! process exits on `POST /shutdown`.

mod api;
mod inspector;

use std::collections::HashMap;
use std::io::Write;
use std::sync::{Arc, Mutex};

use tao::dpi::{LogicalPosition, LogicalSize, PhysicalPosition, PhysicalSize};
use tao::event::{Event, WindowEvent};
use tao::event_loop::{ControlFlow, EventLoopBuilder, EventLoopWindowTarget};
use tao::window::{Window, WindowBuilder, WindowId};
use wry::{WebContext, WebView, WebViewBuilder};

use api::{Command, EventRecord, OpenRequest, Reply, Request, WebViewInfo};

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
    api_calls: Arc<Mutex<Vec<api::ApiCallRecord>>>,
    dom_events: Arc<Mutex<Vec<api::ApiCallRecord>>>,
}

fn main() {
    let port = port_from_args();
    let event_loop = EventLoopBuilder::<Request>::with_user_event().build();
    let proxy = event_loop.create_proxy();
    let events: Arc<Mutex<Vec<EventRecord>>> = Arc::new(Mutex::new(Vec::new()));

    let server = tiny_http::Server::http(("127.0.0.1", port)).expect("failed to bind the API port");
    let bound = server
        .server_addr()
        .to_ip()
        .expect("expected an IP listen address")
        .port();
    println!("SWASWA_WEBVIEW_PORT={bound}");
    let _ = std::io::stdout().flush();

    let http_events = Arc::clone(&events);
    let mut state = State::default();
    let http_api_calls = Arc::clone(&state.api_calls);
    let http_dom_events = Arc::clone(&state.dom_events);
    std::thread::spawn(move || {
        api::serve(server, proxy, http_events, http_api_calls, http_dom_events)
    });
    event_loop.run(move |event, target, control_flow| {
        *control_flow = ControlFlow::Wait;
        match event {
            Event::UserEvent(request) => handle(request, target, &mut state, control_flow),
            Event::WindowEvent {
                window_id,
                event: WindowEvent::CloseRequested,
                ..
            } => {
                if let Some(id) = state.ids_by_window.remove(&window_id) {
                    state.views.remove(&id);
                    events.lock().unwrap().push(EventRecord {
                        kind: "closed".to_string(),
                        id,
                    });
                }
            }
            _ => {}
        }
    });
}

fn handle(
    request: Request,
    target: &EventLoopWindowTarget<Request>,
    state: &mut State,
    control_flow: &mut ControlFlow,
) {
    let Request { command, reply } = request;
    let response = match command {
        Command::Ping => Reply::Ok,
        Command::Open(open) => open_webview(open, target, state),
        Command::Navigate { id, url } => match state.views.get(&id) {
            Some(instance) => result_reply(instance.webview.load_url(&url)),
            None => not_found(&id),
        },
        Command::LoadHtml { id, html } => match state.views.get(&id) {
            Some(instance) => result_reply(instance.webview.load_html(&html)),
            None => not_found(&id),
        },
        Command::Eval { id, script } => match state.views.get(&id) {
            Some(instance) => {
                let callback_reply = reply.clone();
                match instance
                    .webview
                    .evaluate_script_with_callback(&script, move |value| {
                        let _ = callback_reply.send(Reply::EvalResult(value));
                    }) {
                    Ok(()) => return, // the callback delivers the reply
                    Err(e) => Reply::Error(e.to_string()),
                }
            }
            None => not_found(&id),
        },
        Command::Cookies { id, url } => match state.views.get(&id) {
            Some(instance) => match instance.webview.cookies_for_url(&url) {
                Ok(cookies) => Reply::Cookies(cookies.into_iter().map(|cookie| api::CookieRecord {
                    name: cookie.name().to_string(),
                    value: cookie.value().to_string(),
                    domain: cookie.domain().unwrap_or_default().to_string(),
                    path: cookie.path().unwrap_or_default().to_string(),
                    http_only: cookie.http_only().unwrap_or(false),
                    secure: cookie.secure().unwrap_or(false),
                }).collect()),
                Err(error) => Reply::Error(error.to_string()),
            },
            None => not_found(&id),
        },
        Command::SetTitle { id, title } => match state.views.get_mut(&id) {
            Some(instance) => {
                instance.window.set_title(&title);
                instance.title = title;
                Reply::Ok
            }
            None => not_found(&id),
        },
        Command::SetSize { id, width, height } => match state.views.get(&id) {
            Some(instance) => {
                instance
                    .window
                    .set_inner_size(LogicalSize::new(width, height));
                Reply::Ok
            }
            None => not_found(&id),
        },
        Command::SetPosition { id, x, y } => match state.views.get(&id) {
            Some(instance) => {
                instance
                    .window
                    .set_outer_position(LogicalPosition::new(x, y));
                Reply::Ok
            }
            None => not_found(&id),
        },
        Command::SetBounds { id, x, y, width, height } => match state.views.get(&id) {
            Some(instance) => {
                set_bounds(&instance.window, x, y, width.max(1), height.max(1));
                Reply::Ok
            }
            None => not_found(&id),
        },
        Command::Show { id } => match state.views.get(&id) {
            Some(instance) => {
                instance.window.set_visible(true);
                Reply::Ok
            }
            None => not_found(&id),
        },
        Command::Hide { id } => match state.views.get(&id) {
            Some(instance) => {
                instance.window.set_visible(false);
                Reply::Ok
            }
            None => not_found(&id),
        },
        Command::Focus { id } => match state.views.get(&id) {
            Some(instance) => {
                instance.window.set_focus();
                Reply::Ok
            }
            None => not_found(&id),
        },
        Command::Close { id } => match state.views.remove(&id) {
            Some(instance) => {
                state.ids_by_window.remove(&instance.window.id());
                drop(instance); // dropping the window closes it
                Reply::Ok
            }
            None => not_found(&id),
        },
        Command::List => Reply::WebViews(
            state
                .views
                .iter()
                .map(|(id, instance)| WebViewInfo {
                    id: id.clone(),
                    title: instance.title.clone(),
                    url: instance.webview.url().unwrap_or_default(),
                    data_directory: instance.context.as_ref().and_then(|c| c.data_directory().map(|p| p.to_path_buf())),
                })
                .collect(),
        ),
        Command::Shutdown => {
            *control_flow = ControlFlow::Exit;
            Reply::Ok
        }
    };
    let _ = reply.send(response);
}

fn open_webview(
    open: OpenRequest,
    target: &EventLoopWindowTarget<Request>,
    state: &mut State,
) -> Reply {
    state.next_id += 1;
    let id = format!("wv-{}", state.next_id);
    let window = match window_builder(&open)
        .build(target)
    {
        Ok(window) => window,
        Err(e) => return Reply::Error(format!("failed to create the window: {e}")),
    };

    if let Some(path) = &open.data_directory {
        if !path.is_absolute() { return Reply::Error("dataDirectory must be absolute".into()); }
        if let Err(e) = std::fs::create_dir_all(path) {
            return Reply::Error(format!("cannot create browser profile: {e}"));
        }
    }
    let mut context = open.data_directory.as_ref().map(|path| Box::new(WebContext::new(Some(path.clone()))));
    let mut builder = match context.as_deref_mut() {
        Some(context) => WebViewBuilder::new_with_web_context(context),
        None => WebViewBuilder::new(),
    }.with_devtools(open.devtools);
    let calls = Arc::clone(&state.api_calls);
    let dom_events = Arc::clone(&state.dom_events);
    let view_id = id.clone();
    builder = builder
        .with_initialization_script(&inspector::initialization_script())
        .with_ipc_handler(move |request| {
            inspector::capture(request.body(), &view_id, &calls);
            inspector::capture_dom(request.body(), &view_id, &dom_events);
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
    state.views.insert(
        id.clone(),
        Instance {
            window,
            webview,
            title: open.title,
            context,
        },
    );
    Reply::Opened { id }
}

#[cfg(any(
    target_os = "windows",
    target_os = "macos",
    target_os = "ios",
    target_os = "android"
))]
fn build_webview(builder: WebViewBuilder, window: &Window) -> wry::Result<WebView> {
    builder.build(window)
}

#[cfg(not(any(
    target_os = "windows",
    target_os = "macos",
    target_os = "ios",
    target_os = "android"
)))]
fn build_webview(builder: WebViewBuilder, window: &Window) -> wry::Result<WebView> {
    use tao::platform::unix::WindowExtUnix;
    use wry::WebViewBuilderExtUnix;
    let vbox = window
        .default_vbox()
        .expect("the window has no default vbox");
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

fn port_from_args() -> u16 {
    let mut args = std::env::args();
    while let Some(arg) = args.next() {
        if arg == "--port" {
            if let Some(value) = args.next() {
                if let Ok(port) = value.parse() {
                    return port;
                }
            }
        }
    }
    0
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
