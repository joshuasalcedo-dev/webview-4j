//! Immediate-mode UI for swaswa: egui, drawn with OpenGL into a Tao window. The window's events become egui's input;
//! a frame is run by whoever drives it ([`Surface::frame`] with a function that draws the UI anew, every frame, from its
//! own state); egui says when it wants the next one. One per window; all on the event loop's thread.

use std::num::NonZeroU32;
use std::sync::Arc;
use std::time::{Duration, Instant};

use glutin::config::{ConfigTemplateBuilder, GlConfig};
use glutin::context::{ContextAttributesBuilder, NotCurrentGlContext, PossiblyCurrentContext, PossiblyCurrentGlContext};
use glutin::display::{Display, DisplayApiPreference, GlDisplay};
use glutin::surface::{GlSurface, Surface as GlWindowSurface, SurfaceAttributesBuilder, SwapInterval, WindowSurface};
use raw_window_handle::{HasDisplayHandle, HasWindowHandle};
use tao::event::{ElementState, MouseButton, MouseScrollDelta, WindowEvent};
use tao::keyboard::{Key as TaoKey, ModifiersState};
use tao::window::{CursorIcon, Window};

/// What a frame drew where (for tests and tools: a widget found by its label, clicked by its place).
#[derive(Debug, Clone, PartialEq)]
pub struct Drawn {
    pub kind: &'static str,
    pub label: String,
    /// Logical pixels in the window: x, y, width, height.
    pub rect: [f32; 4],
}

/// egui in one window: its GL context and surface, egui's context and painter, and the input gathered since the
/// last frame.
pub struct Surface {
    pub ctx: egui::Context,
    gl_context: PossiblyCurrentContext,
    gl_surface: GlWindowSurface<WindowSurface>,
    gl: Arc<glow::Context>,
    painter: egui_glow::Painter,
    events: Vec<egui::Event>,
    pointer: Option<egui::Pos2>,
    modifiers: egui::Modifiers,
    focused: bool,
    started: Instant,
    /// What the last frame drew, in order.
    pub drawn: Vec<Drawn>,
    /// When egui wants the next frame (None: only on input).
    pub next: Option<Instant>,
    clear: [f32; 4],
}

impl Surface {
    /// egui on `window`: an OpenGL context of its own, on its own surface.
    pub fn new(window: &Window) -> Result<Self, String> {
        let display_handle = window.display_handle().map_err(|e| e.to_string())?.as_raw();
        let window_handle = window.window_handle().map_err(|e| e.to_string())?.as_raw();
        #[cfg(windows)]
        let preference = DisplayApiPreference::Wgl(Some(window_handle));
        #[cfg(not(windows))]
        let preference = DisplayApiPreference::Egl;
        // SAFETY: the handles are the live window's, on its own thread.
        let display = unsafe { Display::new(display_handle, preference) }.map_err(|e| format!("no OpenGL display: {e}"))?;
        let template = ConfigTemplateBuilder::new().compatible_with_native_window(window_handle).with_alpha_size(8).build();
        let config = unsafe { display.find_configs(template) }
            .map_err(|e| format!("no OpenGL configuration: {e}"))?
            .reduce(|best, c| if c.num_samples() > best.num_samples() { c } else { best })
            .ok_or("no OpenGL configuration")?;
        let attributes = ContextAttributesBuilder::new().build(Some(window_handle));
        let context = unsafe { display.create_context(&config, &attributes) }.map_err(|e| format!("no OpenGL context: {e}"))?;
        let size = window.inner_size();
        let surface_attributes = SurfaceAttributesBuilder::<WindowSurface>::new().build(
            window_handle,
            NonZeroU32::new(size.width.max(1)).expect("positive"),
            NonZeroU32::new(size.height.max(1)).expect("positive"),
        );
        let gl_surface =
            unsafe { display.create_window_surface(&config, &surface_attributes) }.map_err(|e| format!("no OpenGL surface: {e}"))?;
        let gl_context = context.make_current(&gl_surface).map_err(|e| format!("the OpenGL context: {e}"))?;
        // in step with the display: no frame drawn that is not shown
        let _ = gl_surface.set_swap_interval(&gl_context, SwapInterval::Wait(NonZeroU32::new(1).expect("one")));
        let gl = Arc::new(unsafe { glow::Context::from_loader_function_cstr(|name| display.get_proc_address(name)) });
        let painter = egui_glow::Painter::new(Arc::clone(&gl), "", None, false).map_err(|e| format!("egui's painter: {e}"))?;
        let ctx = egui::Context::default();
        ctx.set_pixels_per_point(window.scale_factor() as f32);
        Ok(Surface {
            ctx,
            gl_context,
            gl_surface,
            gl,
            painter,
            events: Vec::new(),
            pointer: None,
            modifiers: egui::Modifiers::default(),
            focused: true,
            started: Instant::now(),
            drawn: Vec::new(),
            next: Some(Instant::now()),
            clear: [0.11, 0.11, 0.12, 1.0],
        })
    }

    /// The window's event as egui's input. True when it is worth a frame (it changes what egui would draw).
    pub fn on_event(&mut self, window: &Window, event: &WindowEvent) -> bool {
        let scale = window.scale_factor() as f32;
        let point = |x: f64, y: f64| egui::pos2(x as f32 / scale, y as f32 / scale);
        match event {
            WindowEvent::Resized(size) => {
                if let (Some(w), Some(h)) = (NonZeroU32::new(size.width), NonZeroU32::new(size.height)) {
                    self.gl_surface.resize(&self.gl_context, w, h);
                }
                true
            }
            WindowEvent::ScaleFactorChanged { scale_factor, .. } => {
                self.ctx.set_pixels_per_point(*scale_factor as f32);
                true
            }
            WindowEvent::CursorMoved { position, .. } => {
                let at = point(position.x, position.y);
                self.pointer = Some(at);
                self.events.push(egui::Event::PointerMoved(at));
                true
            }
            WindowEvent::CursorLeft { .. } => {
                self.pointer = None;
                self.events.push(egui::Event::PointerGone);
                true
            }
            WindowEvent::MouseInput { state, button, .. } => {
                let Some(pos) = self.pointer else { return false };
                let button = match button {
                    MouseButton::Left => egui::PointerButton::Primary,
                    MouseButton::Right => egui::PointerButton::Secondary,
                    MouseButton::Middle => egui::PointerButton::Middle,
                    _ => return false,
                };
                self.events.push(egui::Event::PointerButton { pos, button, pressed: *state == ElementState::Pressed, modifiers: self.modifiers });
                true
            }
            WindowEvent::MouseWheel { delta, .. } => {
                let (unit, delta) = match delta {
                    MouseScrollDelta::LineDelta(x, y) => (egui::MouseWheelUnit::Line, egui::vec2(*x, *y)),
                    MouseScrollDelta::PixelDelta(p) => (egui::MouseWheelUnit::Point, egui::vec2(p.x as f32 / scale, p.y as f32 / scale)),
                    _ => return false,
                };
                self.events.push(egui::Event::MouseWheel {
                    unit,
                    delta,
                    modifiers: self.modifiers,
                    phase: egui::TouchPhase::Move,
                    source: egui::MouseWheelSource::Unknown,
                });
                true
            }
            WindowEvent::ModifiersChanged(state) => {
                self.modifiers = modifiers(*state);
                false
            }
            WindowEvent::KeyboardInput { event, .. } => {
                let pressed = event.state == ElementState::Pressed;
                if let Some(key) = key(&event.logical_key) {
                    self.events.push(egui::Event::Key { key, physical_key: None, pressed, repeat: event.repeat, modifiers: self.modifiers });
                    // the shortcuts egui knows as their own events
                    if pressed && (self.modifiers.ctrl || self.modifiers.mac_cmd) {
                        match key {
                            egui::Key::C => self.events.push(egui::Event::Copy),
                            egui::Key::X => self.events.push(egui::Event::Cut),
                            _ => {}
                        }
                    }
                }
                if pressed && !self.modifiers.ctrl && !self.modifiers.alt {
                    if let Some(text) = event.text.filter(|t| !t.chars().any(char::is_control)) {
                        self.events.push(egui::Event::Text(text.to_string()));
                    }
                }
                true
            }
            WindowEvent::ReceivedImeText(text) => {
                self.events.push(egui::Event::Text(text.clone()));
                true
            }
            WindowEvent::Focused(focused) => {
                self.focused = *focused;
                self.events.push(egui::Event::WindowFocused(*focused));
                true
            }
            _ => false,
        }
    }

    /// Input of the app's own (a test's pointer, a tool's): as if the person had done it.
    pub fn inject(&mut self, events: Vec<egui::Event>) {
        for e in &events {
            match e {
                egui::Event::PointerMoved(at) => self.pointer = Some(*at),
                egui::Event::PointerGone => self.pointer = None,
                _ => {}
            }
        }
        self.events.extend(events);
    }

    /// One frame: `draw` builds the UI anew in egui's central panel; it is drawn and shown. What it drew is in
    /// [`Surface::drawn`] (the widgets `draw` reports), when egui wants the next frame in [`Surface::next`].
    pub fn frame(&mut self, window: &Window, mut draw: impl FnMut(&mut egui::Ui)) -> Result<(), String> {
        if !self.gl_context.is_current() {
            self.gl_context.make_current(&self.gl_surface).map_err(|e| e.to_string())?;
        }
        let size = window.inner_size();
        let scale = window.scale_factor() as f32;
        let had_input = !self.events.is_empty();
        let raw = egui::RawInput {
            screen_rect: Some(egui::Rect::from_min_size(egui::Pos2::ZERO, egui::vec2(size.width as f32 / scale, size.height as f32 / scale))),
            time: Some(self.started.elapsed().as_secs_f64()),
            events: std::mem::take(&mut self.events),
            focused: self.focused,
            ..Default::default()
        };
        let output = self.ctx.run_ui(raw, |root| {
            egui::CentralPanel::default().show(root, |ui| draw(ui));
        });
        // what egui wants of the window: its cursor; and when it wants the next frame
        window.set_cursor_icon(cursor(output.platform_output.cursor_icon));
        let delay = output.viewport_output.get(&egui::ViewportId::ROOT).map(|v| v.repaint_delay).unwrap_or(Duration::MAX);
        // a frame that had input gets one more: what the input changed (a click, a key) shows at once, the UI drawn
        // from the state it left (immediate mode's way: the click is known in the frame, after what drew it)
        let delay = if had_input { Duration::ZERO } else { delay };
        self.next = (delay < Duration::from_secs(3600)).then(|| Instant::now() + delay);
        let primitives = self.ctx.tessellate(output.shapes, output.pixels_per_point);
        let mut textures = output.textures_delta;
        self.painter.clear([size.width, size.height], self.clear);
        self.painter.paint_and_update_textures([size.width, size.height], output.pixels_per_point, &primitives, &mut textures);
        self.gl_surface.swap_buffers(&self.gl_context).map_err(|e| e.to_string())?;
        Ok(())
    }

    /// The colour behind the UI (egui's panel paints over most of it).
    pub fn set_clear(&mut self, rgba: [f32; 4]) {
        self.clear = rgba;
    }

    /// The GL objects freed while the context still exists: call before the window goes.
    pub fn destroy(&mut self) {
        if !self.gl_context.is_current() {
            let _ = self.gl_context.make_current(&self.gl_surface);
        }
        self.painter.destroy();
        let _ = &self.gl;
    }
}

fn modifiers(state: ModifiersState) -> egui::Modifiers {
    egui::Modifiers {
        alt: state.alt_key(),
        ctrl: state.control_key(),
        shift: state.shift_key(),
        mac_cmd: cfg!(target_os = "macos") && state.super_key(),
        command: if cfg!(target_os = "macos") { state.super_key() } else { state.control_key() },
    }
}

fn key(key: &TaoKey<'static>) -> Option<egui::Key> {
    use egui::Key as K;
    Some(match key {
        TaoKey::Enter => K::Enter,
        TaoKey::Tab => K::Tab,
        TaoKey::Backspace => K::Backspace,
        TaoKey::Delete => K::Delete,
        TaoKey::Escape => K::Escape,
        TaoKey::Space => K::Space,
        TaoKey::ArrowLeft => K::ArrowLeft,
        TaoKey::ArrowRight => K::ArrowRight,
        TaoKey::ArrowUp => K::ArrowUp,
        TaoKey::ArrowDown => K::ArrowDown,
        TaoKey::Home => K::Home,
        TaoKey::End => K::End,
        TaoKey::PageUp => K::PageUp,
        TaoKey::PageDown => K::PageDown,
        TaoKey::Insert => K::Insert,
        TaoKey::Character(c) => return K::from_name(&c.to_uppercase()),
        _ => return None,
    })
}

fn cursor(icon: egui::CursorIcon) -> CursorIcon {
    use egui::CursorIcon as E;
    match icon {
        E::PointingHand => CursorIcon::Hand,
        E::Text | E::VerticalText => CursorIcon::Text,
        E::Grab => CursorIcon::Grab,
        E::Grabbing => CursorIcon::Grabbing,
        E::Move | E::AllScroll => CursorIcon::Move,
        E::ResizeHorizontal | E::ResizeColumn | E::ResizeEast | E::ResizeWest => CursorIcon::EwResize,
        E::ResizeVertical | E::ResizeRow | E::ResizeNorth | E::ResizeSouth => CursorIcon::NsResize,
        E::ResizeNeSw | E::ResizeNorthEast | E::ResizeSouthWest => CursorIcon::NeswResize,
        E::ResizeNwSe | E::ResizeNorthWest | E::ResizeSouthEast => CursorIcon::NwseResize,
        E::NotAllowed | E::NoDrop => CursorIcon::NotAllowed,
        E::Wait => CursorIcon::Wait,
        E::Progress => CursorIcon::Progress,
        E::Crosshair => CursorIcon::Crosshair,
        E::Help => CursorIcon::Help,
        _ => CursorIcon::Default,
    }
}

// ---- the widgets a driver draws with: one frame's UI, the egui Ui it is drawn in on a stack ----

use std::cell::RefCell;

thread_local! {
    /// The egui Uis being drawn in, innermost last: valid only while a frame (or a nested layout) runs.
    static UIS: RefCell<Vec<*mut egui::Ui>> = const { RefCell::new(Vec::new()) };
    /// What this frame drew.
    static DRAWN: RefCell<Vec<Drawn>> = const { RefCell::new(Vec::new()) };
}

/// Runs `f` with the innermost Ui of the frame under way; None outside one.
pub fn with_ui<R>(f: impl FnOnce(&mut egui::Ui) -> R) -> Option<R> {
    let top = UIS.with(|u| u.borrow().last().copied())?;
    // SAFETY: the pointer is pushed by `enter` for the length of a closure that holds the &mut Ui, on this thread,
    // and popped before it returns; nothing else uses it meanwhile.
    Some(f(unsafe { &mut *top }))
}

/// Runs `inside` with `ui` as the innermost Ui (a frame's, or a nested layout's).
pub fn enter<R>(ui: &mut egui::Ui, inside: impl FnOnce() -> R) -> R {
    UIS.with(|u| u.borrow_mut().push(ui as *mut egui::Ui));
    let out = inside();
    UIS.with(|u| u.borrow_mut().pop());
    out
}

/// Notes what a widget drew where (logical pixels).
pub fn drew(kind: &'static str, label: &str, rect: egui::Rect) {
    DRAWN.with(|d| d.borrow_mut().push(Drawn { kind, label: label.to_string(), rect: [rect.min.x, rect.min.y, rect.width(), rect.height()] }));
}

/// What the frame drew, taken (it starts empty for the next).
pub fn take_drawn() -> Vec<Drawn> {
    DRAWN.with(|d| std::mem::take(&mut *d.borrow_mut()))
}

#[cfg(test)]
mod tests {
    use super::*;

    /// The widget layer without a window: egui run headless, the frame's UI drawn through `enter`/`with_ui`.
    fn run(ctx: &egui::Context, events: Vec<egui::Event>, draw: impl FnMut(&mut egui::Ui)) -> Vec<Drawn> {
        let mut draw = draw;
        let raw = egui::RawInput {
            screen_rect: Some(egui::Rect::from_min_size(egui::Pos2::ZERO, egui::vec2(400.0, 300.0))),
            events,
            ..Default::default()
        };
        let mut output = ctx.run_ui(raw, |root| {
            egui::CentralPanel::default().show(root, |ui| enter(ui, || draw_with(&mut draw)));
        });
        output.textures_delta.clear(); // headless: nothing to upload them to
        take_drawn()
    }

    fn draw_with(draw: &mut impl FnMut(&mut egui::Ui)) {
        with_ui(|ui| draw(ui));
    }

    fn button(label: &str) -> bool {
        with_ui(|ui| {
            let r = ui.button(label);
            drew("button", label, r.rect);
            r.clicked()
        })
        .unwrap_or(false)
    }

    #[test]
    fn a_button_is_drawn_and_clicked_where_it_is() {
        let ctx = egui::Context::default();
        let mut clicked = false;
        let drawn = run(&ctx, vec![], |_| clicked |= button("Save"));
        assert!(!clicked);
        let save = drawn.iter().find(|d| d.label == "Save").expect("drawn").rect;
        let at = egui::pos2(save[0] + save[2] / 2.0, save[1] + save[3] / 2.0);
        let press = vec![
            egui::Event::PointerMoved(at),
            egui::Event::PointerButton { pos: at, button: egui::PointerButton::Primary, pressed: true, modifiers: Default::default() },
            egui::Event::PointerButton { pos: at, button: egui::PointerButton::Primary, pressed: false, modifiers: Default::default() },
        ];
        run(&ctx, press, |_| clicked |= button("Save"));
        assert!(clicked, "clicked where it was drawn");
    }

    #[test]
    fn outside_a_frame_there_is_no_ui() {
        assert!(with_ui(|_| ()).is_none());
        assert!(!button("nowhere"));
    }

    #[test]
    fn letters_have_glyphs_not_boxes() {
        // without egui's default_fonts feature, every letter is drawn as a box
        let ctx = egui::Context::default();
        let _ = run(&ctx, vec![], |ui| {
            ui.label("x");
        });
        let mut has = |c: char| ctx.fonts_mut(|fonts| fonts.has_glyphs(&egui::FontId::proportional(14.0), &c.to_string()));
        for c in ['A', 'z', '0', 'é', '?'] {
            assert!(has(c), "a glyph for {c:?}");
        }
    }

    #[test]
    fn keys_are_eguis() {
        assert_eq!(key(&TaoKey::Enter), Some(egui::Key::Enter));
        assert_eq!(key(&TaoKey::Character("a")), Some(egui::Key::A));
        assert_eq!(cursor(egui::CursorIcon::Text), CursorIcon::Text);
    }
}
