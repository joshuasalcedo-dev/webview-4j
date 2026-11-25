//! The dev logger in a native window: `cargo run -p egui-4j --example dev_logger`.
//!
//! A pretend app runs: it calls its API (now and then slowly, now and then failing), calls between page and
//! Java, logs, and has long tasks. Everything reaches the logger as the hosts would send it: JSON props. The
//! example's own `log::` lines go in too. Click a row for its bodies (JSON tree), headers and time.
//!
//! The window floats like an overlay: no title bar, always on top, see-through (the opacity slider sets how much,
//! 0% to 100%).
//! Drag its top strip to move it, its bottom-right corner to resize it. Let it go near a screen edge and it docks
//! there like a sidebar (full height at the left or right, full width at the top or bottom); drag it away from
//! the edge and it floats again, at its old size.

use std::sync::Mutex;
use std::time::{SystemTime, UNIX_EPOCH};

use egui4j::{Component, Events, dev_logger::DevLogger};
use serde_json::{Value, json};

/// The example's own log lines, waiting to go into the logger.
static LOGGED: Mutex<Vec<Value>> = Mutex::new(Vec::new());

struct IntoLogger;

impl log::Log for IntoLogger {
    fn enabled(&self, metadata: &log::Metadata<'_>) -> bool {
        // the app's own lines (this example's), not winit's or wgpu's many
        metadata.level() <= log::Level::Debug && metadata.target().starts_with("dev_logger")
    }
    fn log(&self, record: &log::Record<'_>) {
        if self.enabled(record.metadata()) {
            LOGGED.lock().unwrap().push(json!({
                "kind": "log", "time": now(), "level": record.level().as_str().to_lowercase(),
                "target": record.target(), "message": record.args().to_string()
            }));
        }
    }
    fn flush(&self) {}
}

fn now() -> f64 {
    SystemTime::now().duration_since(UNIX_EPOCH).unwrap().as_secs_f64() * 1000.0
}

fn main() -> eframe::Result {
    log::set_logger(&IntoLogger).unwrap();
    log::set_max_level(log::LevelFilter::Debug);
    let options = eframe::NativeOptions {
        viewport: egui::ViewportBuilder::default()
            .with_inner_size([1100.0, 700.0])
            .with_min_inner_size([360.0, 200.0])
            .with_title("egui-4j: dev-logger")
            .with_decorations(false)
            .with_always_on_top()
            .with_transparent(true)
            .with_icon(icon()),
        // OpenGL: its window blends with what is behind it (wgpu's does not, on Windows)
        renderer: eframe::Renderer::Glow,
        ..Default::default()
    };
    eframe::run_native(
        "egui-4j dev-logger",
        options,
        Box::new(|_| Ok(Box::new(Demo::new()))),
    )
}

struct Demo {
    logger: DevLogger,
    events: Events,
    frame: u64,
    running: bool,
    dark: bool,
    /// How solid the window is: 1 opaque, lower shows what is behind it.
    opacity: f32,
    dock: EdgeDock,
}

impl Demo {
    fn new() -> Self {
        let mut logger = DevLogger::default();
        logger.update(json!({ "slowMs": 300 })).unwrap();
        log::info!("the app started");
        // DEV_LOGGER_OPACITY=0.6 starts it more see-through (0 to 1)
        // DEV_LOGGER_THEME=light starts it light
        let dark = std::env::var("DEV_LOGGER_THEME").map_or(true, |t| !t.eq_ignore_ascii_case("light"));
        let opacity = std::env::var("DEV_LOGGER_OPACITY").ok().and_then(|v| v.parse().ok()).unwrap_or(0.85f32).clamp(0.0, 1.0);
        Demo { logger, events: Events::default(), frame: 0, running: true, dark, opacity, dock: EdgeDock::default() }
    }

    /// What the pretend app does this frame.
    fn app_step(&mut self) -> Vec<Value> {
        self.frame += 1;
        let f = self.frame;
        let t = now();
        let mut out = Vec::new();
        if f.is_multiple_of(45) {
            let slow = f.is_multiple_of(225);
            let took = if slow { 640.0 + (f % 7) as f64 * 40.0 } else { 18.0 + (f % 13) as f64 * 3.5 };
            let todos: Vec<Value> = (1..=(f % 5 + 2)).map(|i| json!({ "id": i, "title": format!("task {i}"), "done": i % 2 == 0, "tags": ["home", "errand"] })).collect();
            out.push(json!({
                "kind": "http", "time": t, "source": "page", "method": "GET", "url": "/api/todos?page=1",
                "status": 200, "durationMs": took,
                "request": { "headers": { "accept": "application/json" } },
                "response": { "headers": { "content-type": "application/json", "x-total": "42" },
                              "body": serde_json::to_string(&json!({ "items": todos, "page": 1, "total": 42 })).unwrap() }
            }));
            if slow {
                log::warn!("GET /api/todos took {took:.0} ms");
            }
        }
        if f.is_multiple_of(160) {
            out.push(json!({
                "kind": "http", "time": t, "source": "page", "method": "POST", "url": "/api/todos",
                "status": 500, "durationMs": 120.0,
                "request": { "headers": { "content-type": "application/json" }, "body": { "title": "milk" } },
                "response": { "headers": { "content-type": "application/problem+json" },
                              "body": { "type": "about:blank", "title": "Internal Server Error", "detail": "database is locked" } }
            }));
            log::error!("saving a todo failed: database is locked");
        }
        if f.is_multiple_of(70) {
            out.push(json!({
                "kind": "bridge", "time": t, "direction": "toJava", "name": "TodoController.add",
                "durationMs": 2.0 + (f % 5) as f64, "request": { "body": { "title": "bread" } },
                "response": { "body": { "id": f, "title": "bread", "done": false } }
            }));
        }
        if f.is_multiple_of(110) {
            out.push(json!({
                "kind": "http", "time": t, "source": "java", "method": "GET", "url": "https://api.example.com/rates",
                "durationMs": 3000.0, "error": "timed out after 3 s"
            }));
        }
        if f.is_multiple_of(95) {
            out.push(json!({ "kind": "perf", "time": t, "name": "long task", "value": 60 + f % 90, "unit": "ms" }));
        }
        if f.is_multiple_of(300) {
            log::debug!("{} frames drawn", f);
        }
        out
    }

    fn send(&mut self, props: Value) {
        if let Err(e) = self.logger.update(props) {
            eprintln!("update refused: {e}");
        }
    }
}

impl eframe::App for Demo {
    fn ui(&mut self, ui: &mut egui::Ui, frame: &mut eframe::Frame) {
        let ctx = ui.ctx().clone();
        self.dock.update(&ctx, work_area(&ctx, frame));
        let mut visuals = if self.dark { egui::Visuals::dark() } else { egui::Visuals::light() };
        // see-through: the panels' fill lets the desktop show, the text stays solid
        let opacity = self.opacity;
        let alpha = (opacity * 255.0) as u8;
        let [r, g, b, _] = visuals.panel_fill.to_array();
        visuals.panel_fill = egui::Color32::from_rgba_unmultiplied(r, g, b, alpha);
        visuals.window_fill = visuals.panel_fill;
        // the rows' stripes: a shade of the panel (a touch darker in light, lighter in dark), as solid as it.
        // (egui's own stripe is a faint see-through black: given the panels' opacity it turns near black.)
        let shade = |c: u8| if self.dark { c.saturating_add(10) } else { c.saturating_sub(10) };
        visuals.faint_bg_color = egui::Color32::from_rgba_unmultiplied(shade(r), shade(g), shade(b), alpha);
        ctx.set_visuals(visuals);
        if self.running {
            let mut append = self.app_step();
            append.extend(LOGGED.lock().unwrap().drain(..));
            if !append.is_empty() {
                self.send(json!({ "append": append }));
            }
            ctx.request_repaint();
        }

        egui::Panel::top("demo").show(ui, |ui| {
            // no title bar: the title and the empty middle of this strip move the window; the buttons stay buttons
            // (a drag area laid over the whole strip would take their clicks)
            ui.horizontal(|ui| {
                let title = ui.add(egui::Label::new(egui::RichText::new("dev-logger").strong()).selectable(false).sense(egui::Sense::drag()));
                ui.with_layout(egui::Layout::right_to_left(egui::Align::Center), |ui| {
                    if ui.button("Close").clicked() {
                        ctx.send_viewport_cmd(egui::ViewportCommand::Close);
                    }
                    ui.add(egui::Slider::new(&mut self.opacity, 0.0..=1.0).custom_formatter(|v, _| format!("{:.0}%", v * 100.0)))
                        .on_hover_text("How solid the window is: lower shows more of what is behind it");
                    ui.weak("opacity");
                    let middle = ui.allocate_response(ui.available_size_before_wrap(), egui::Sense::drag());
                    if title.drag_started() || middle.drag_started() {
                        ctx.send_viewport_cmd(egui::ViewportCommand::StartDrag);
                    }
                });
            });
            ui.horizontal(|ui| {
                ui.toggle_value(&mut self.running, "app running");
                ui.toggle_value(&mut self.dark, "dark");
                if ui.button("only calls (host filter)").clicked() {
                    self.send(json!({ "filter": { "kinds": ["http", "bridge"] } }));
                }
                if ui.button("everything").clicked() {
                    self.send(json!({ "filter": { "kinds": ["log", "http", "bridge", "perf"], "search": "", "errorsOnly": false } }));
                }
            });
        });
        egui::CentralPanel::default().show(ui, |ui| self.logger.ui(ui, &mut self.events));

        // no window edges to grab: the bottom-right corner resizes it
        let corner = egui::Rect::from_min_size(ctx.content_rect().max - egui::vec2(16.0, 16.0), egui::vec2(16.0, 16.0));
        let grip = ui.interact(corner, egui::Id::unique("resize-window"), egui::Sense::drag());
        let painter = ui.painter();
        for i in 1..=3 {
            let d = i as f32 * 4.0;
            painter.line_segment(
                [corner.max - egui::vec2(d, 2.0), corner.max - egui::vec2(2.0, d)],
                egui::Stroke::new(1.0, ui.visuals().weak_text_color()),
            );
        }
        if grip.hovered() {
            ctx.set_cursor_icon(egui::CursorIcon::ResizeNwSe);
        }
        if grip.drag_started() {
            ctx.send_viewport_cmd(egui::ViewportCommand::BeginResize(egui::viewport::ResizeDirection::SouthEast));
        }

        for event in self.events.drain() {
            println!("{event}");
        }
    }

    fn clear_color(&self, _visuals: &egui::Visuals) -> [f32; 4] {
        // nothing behind the panels: see-through where they are
        [0.0; 4]
    }
}

/// The window's icon (taskbar, Alt+Tab): `DEV_LOGGER_ICON=path\to\icon.png`, or egui-4j's logo
/// (assets/logo/egui-4j.svg) drawn here: a white window frame with a pulse, on a teal rounded square.
fn icon() -> egui::IconData {
    if let Ok(path) = std::env::var("DEV_LOGGER_ICON") {
        match std::fs::read(&path).map_err(|e| e.to_string()).and_then(|b| eframe::icon_data::from_png_bytes(&b).map_err(|e| e.to_string())) {
            Ok(icon) => return icon,
            Err(e) => eprintln!("DEV_LOGGER_ICON {path}: {e}; using egui-4j's logo"),
        }
    }
    // the SVG's geometry, in its 256 box
    const SIZE: usize = 64;
    let scale = 256.0 / SIZE as f32;
    fn rounded_box(p: egui::Vec2, center: egui::Vec2, half: egui::Vec2, r: f32) -> f32 {
        let q = (p - center).abs() - (half - egui::vec2(r, r));
        egui::vec2(q.x.max(0.0), q.y.max(0.0)).length() + q.x.max(q.y).min(0.0) - r
    }
    let segments = |points: &[(f32, f32)], p: egui::Vec2| {
        points
            .windows(2)
            .map(|w| {
                let (a, b) = (egui::vec2(w[0].0, w[0].1), egui::vec2(w[1].0, w[1].1));
                let t = ((p - a).dot(b - a) / (b - a).length_sq()).clamp(0.0, 1.0);
                (p - (a + (b - a) * t)).length()
            })
            .fold(f32::MAX, f32::min)
    };
    let pulse = [(72.0, 152.0), (100.0, 152.0), (112.0, 124.0), (128.0, 172.0), (142.0, 138.0), (152.0, 152.0), (184.0, 152.0)];
    let title = [(52.0, 98.0), (204.0, 98.0)];
    let (top, bottom) = ([0x2F, 0xD3, 0xA6], [0x0E, 0x9A, 0x7A]);
    let mut rgba = Vec::with_capacity(SIZE * SIZE * 4);
    for y in 0..SIZE {
        for x in 0..SIZE {
            let p = egui::vec2(x as f32 + 0.5, y as f32 + 0.5) * scale;
            // coverage, softened over one icon pixel (= `scale` SVG units)
            let cover = |d: f32| (0.5 - d / scale).clamp(0.0, 1.0);
            let square = cover(rounded_box(p, egui::vec2(128.0, 128.0), egui::vec2(128.0, 128.0), 56.0));
            let frame = rounded_box(p, egui::vec2(128.0, 128.0), egui::vec2(76.0, 64.0), 18.0).abs();
            let stroke = frame.min(segments(&title, p)).min(segments(&pulse, p)) - 6.0;
            let white = cover(stroke);
            let t = p.y / 256.0;
            for c in 0..3 {
                let bg = top[c] as f32 + (bottom[c] as f32 - top[c] as f32) * t;
                rgba.push((bg + (255.0 - bg) * white) as u8);
            }
            rgba.push((square * 255.0) as u8);
        }
    }
    egui::IconData { rgba, width: SIZE as u32, height: SIZE as u32 }
}

// ---- docking to a screen edge ----

#[derive(Debug, Clone, Copy, PartialEq)]
enum Edge {
    Left,
    Right,
    Top,
    Bottom,
}

/// Let go this close to a screen edge, the window docks there.
const SNAP: f32 = 28.0;
/// The window has stopped moving (let go) once its place has not changed for this long.
const SETTLE: f64 = 0.25;

/// Docks the window to the screen edge it is let go near, like a sidebar. As with Windows' own snapping, a docked
/// window that starts to move takes back its floating size at once (so it can be docked to another edge).
#[derive(Default)]
struct EdgeDock {
    last: Option<egui::Rect>,
    /// When it counts as let go (0: not moving).
    settle_at: f64,
    /// Docked: the edge, and the size it had floating.
    docked: Option<(Edge, egui::Vec2)>,
    /// Where it was just put (its own move, not the user's).
    placed: Option<egui::Rect>,
}

fn distance(rect: egui::Rect, work: egui::Rect, edge: Edge) -> f32 {
    match edge {
        Edge::Left => (rect.left() - work.left()).abs(),
        Edge::Right => (work.right() - rect.right()).abs(),
        Edge::Top => (rect.top() - work.top()).abs(),
        Edge::Bottom => (work.bottom() - rect.bottom()).abs(),
    }
}

fn near(a: egui::Rect, b: egui::Rect) -> bool {
    (a.min - b.min).length() <= 1.0 && (a.size() - b.size()).length() <= 1.0
}

impl EdgeDock {
    fn update(&mut self, ctx: &egui::Context, work: Option<egui::Rect>) {
        let Some(rect) = ctx.input(|i| i.viewport().outer_rect) else { return };
        let now = ctx.input(|i| i.time);
        if self.last != Some(rect) {
            let before = self.last.replace(rect);
            // where it first opened is not a place it was let go; where it was just put is its own doing
            let Some(before) = before else { return };
            if self.placed.is_some_and(|p| near(p, rect)) {
                self.placed = None;
                return;
            }
            // a docked window the user starts to move: floating again, at its old size
            let moved = (rect.min - before.min).length() > 1.0 && (rect.size() - before.size()).length() <= 1.0;
            if moved && let Some((_, floating)) = self.docked.take() {
                ctx.send_viewport_cmd(egui::ViewportCommand::InnerSize(floating));
            }
            self.settle_at = now + SETTLE;
            ctx.request_repaint_after(std::time::Duration::from_secs_f64(SETTLE + 0.05));
            return;
        }
        if self.settle_at == 0.0 || now < self.settle_at {
            return;
        }
        self.settle_at = 0.0;
        if self.docked.is_some() {
            return;
        }
        let Some(work) = work else { return };
        let nearest = [Edge::Left, Edge::Right, Edge::Top, Edge::Bottom]
            .into_iter()
            .map(|e| (e, distance(rect, work, e)))
            .filter(|(_, d)| *d < SNAP)
            .min_by(|a, b| a.1.total_cmp(&b.1));
        if let Some((edge, _)) = nearest {
            let (w, h) = (rect.width(), rect.height());
            let (pos, size) = match edge {
                Edge::Left => (work.left_top(), egui::vec2(w, work.height())),
                Edge::Right => (egui::pos2(work.right() - w, work.top()), egui::vec2(w, work.height())),
                Edge::Top => (work.left_top(), egui::vec2(work.width(), h)),
                Edge::Bottom => (egui::pos2(work.left(), work.bottom() - h), egui::vec2(work.width(), h)),
            };
            self.docked = Some((edge, rect.size()));
            self.placed = Some(egui::Rect::from_min_size(pos, size));
            ctx.send_viewport_cmd(egui::ViewportCommand::OuterPosition(pos));
            ctx.send_viewport_cmd(egui::ViewportCommand::InnerSize(size));
        }
    }
}

/// The screen the window is on, without the taskbar, in points.
#[cfg(windows)]
fn work_area(ctx: &egui::Context, frame: &eframe::Frame) -> Option<egui::Rect> {
    use raw_window_handle::{HasWindowHandle, RawWindowHandle};
    use windows_sys::Win32::Graphics::Gdi::{GetMonitorInfoW, MONITOR_DEFAULTTONEAREST, MONITORINFO, MonitorFromWindow};
    let RawWindowHandle::Win32(handle) = frame.window_handle().ok()?.as_raw() else { return None };
    let scale = ctx.input(|i| i.viewport().native_pixels_per_point).unwrap_or(1.0);
    // SAFETY: the handle is this window's, alive while it is drawn; MONITORINFO is filled by Windows
    unsafe {
        let monitor = MonitorFromWindow(handle.hwnd.get() as _, MONITOR_DEFAULTTONEAREST);
        let mut info: MONITORINFO = std::mem::zeroed();
        info.cbSize = std::mem::size_of::<MONITORINFO>() as u32;
        if GetMonitorInfoW(monitor, &mut info) == 0 {
            return None;
        }
        let r = info.rcWork;
        Some(egui::Rect::from_min_max(
            egui::pos2(r.left as f32 / scale, r.top as f32 / scale),
            egui::pos2(r.right as f32 / scale, r.bottom as f32 / scale),
        ))
    }
}

/// Elsewhere: the whole monitor (egui knows its size, not its taskbar).
#[cfg(not(windows))]
fn work_area(ctx: &egui::Context, _frame: &eframe::Frame) -> Option<egui::Rect> {
    ctx.input(|i| i.viewport().monitor_size).map(|size| egui::Rect::from_min_size(egui::Pos2::ZERO, size))
}
