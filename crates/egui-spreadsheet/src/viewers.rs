//! Viewer windows, floating over the app: one follows the current context (whichever data source is selected:
//! [`Viewers::follow`]), and any source can be opened in a window of its own that stays ([`Viewers::open`], or the
//! context window's Pin). A window can pop out of the app into a window of the system, kept on top (on the web,
//! where there is one window, it stays in the page).

use std::sync::Arc;

use egui::{Ui, ViewportBuilder, ViewportClass, ViewportId};

use crate::source::DataSource;
use crate::viewer::Viewer;

/// A window with a viewer.
struct Window {
    id: egui::Id,
    viewer: Viewer,
    open: bool,
    popped_out: bool,
}

impl Window {
    fn new(source: Arc<dyn DataSource>, salt: &str) -> Window {
        let viewer = Viewer::new(source);
        Window { id: egui::Id::unique((salt, viewer.source().name(), Arc::as_ptr(viewer.source()) as *const () as usize)), viewer, open: true, popped_out: false }
    }
}

/// What a window's buttons asked for.
#[derive(Default)]
struct Asked {
    pin: bool,
}

/// The viewer windows: the one that follows the context, and the pinned ones.
pub struct Viewers {
    /// What the context is (None: nothing selected).
    followed: Option<Arc<dyn DataSource>>,
    context: Option<Window>,
    /// The context window's place in the app (shown, or closed until the context changes).
    context_open: bool,
    pinned: Vec<Window>,
    /// Pop-out windows are kept on top of the others (on at first).
    pub always_on_top: bool,
}

impl Default for Viewers {
    fn default() -> Self {
        Viewers::new()
    }
}

impl Viewers {
    pub fn new() -> Viewers {
        Viewers { followed: None, context: None, context_open: true, pinned: Vec::new(), always_on_top: true }
    }

    /// The current context's source: the context window shows it (keeping its sort, filters and search while it
    /// stays the same). Call it each frame, or when the selection changes. A source pinned already is shown in its
    /// own window, not twice.
    pub fn follow(&mut self, source: Option<Arc<dyn DataSource>>) {
        let same = match (&self.followed, &source) {
            (Some(a), Some(b)) => std::ptr::addr_eq(Arc::as_ptr(a), Arc::as_ptr(b)),
            (None, None) => true,
            _ => false,
        };
        if same {
            return;
        }
        self.followed = source.clone();
        // a new context opens the window again, if it was closed
        self.context_open = true;
        let pinned = source.as_ref().is_some_and(|s| self.is_open(s));
        match source {
            Some(s) if !pinned => match &mut self.context {
                Some(window) => {
                    window.viewer = Viewer::new(s);
                }
                None => self.context = Some(Window::new(s, "context")),
            },
            // nothing selected, or it has its own window: the context window says so
            _ => self.context = None,
        }
    }

    /// Opens a source in a window of its own, which stays when the context changes (or brings its window back).
    pub fn open(&mut self, source: Arc<dyn DataSource>) {
        if let Some(window) = self.pinned.iter_mut().find(|w| w.viewer.shows(&source)) {
            window.open = true;
            return;
        }
        // the context window's, with its sort and filters, when it shows it
        if let Some(window) = self.context.take_if(|w| w.viewer.shows(&source)) {
            self.pinned.push(window);
            return;
        }
        self.pinned.push(Window::new(source, "pinned"));
    }

    /// Whether a source has a window of its own.
    pub fn is_open(&self, source: &Arc<dyn DataSource>) -> bool {
        self.pinned.iter().any(|w| w.viewer.shows(source))
    }

    /// The viewer of a source's own window.
    pub fn viewer(&mut self, source: &Arc<dyn DataSource>) -> Option<&mut Viewer> {
        self.pinned.iter_mut().find(|w| w.viewer.shows(source)).map(|w| &mut w.viewer)
    }

    /// The context window's viewer (None: nothing selected, or it is pinned).
    pub fn context_viewer(&mut self) -> Option<&mut Viewer> {
        self.context.as_mut().map(|w| &mut w.viewer)
    }

    /// How many windows of their own.
    pub fn pinned(&self) -> usize {
        self.pinned.len()
    }

    /// Draws the windows (over the app the Ui is in).
    pub fn show(&mut self, ui: &mut Ui) {
        let ctx = ui.ctx().clone();
        let on_top = self.always_on_top;

        // the context window
        if self.context_open {
            let mut open = true;
            let mut asked = Asked::default();
            let followed = self.followed.as_ref().map(|s| s.name());
            match &mut self.context {
                Some(window) => {
                    let title = format!("Data: {}", window.viewer.source().name());
                    show_window(&ctx, window, &title, true, on_top, &mut asked);
                    open = window.open;
                }
                None => {
                    let text = match followed {
                        Some(name) => format!("{name} is in a window of its own."),
                        None => "Select a data source to see it here.".to_owned(),
                    };
                    egui::Window::new("Data")
                        .id(egui::Id::unique("egui-spreadsheet-viewers-context-empty"))
                        .open(&mut open)
                        .default_size([420.0, 120.0])
                        .show(&ctx, |ui| {
                            ui.weak(text);
                        });
                }
            }
            if !open {
                self.context_open = false;
                if let Some(window) = &mut self.context {
                    window.open = true;
                }
            }
            if asked.pin
                && let Some(window) = self.context.take()
            {
                self.pinned.push(window);
            }
        }

        // the pinned ones
        for window in &mut self.pinned {
            let title = window.viewer.source().name();
            show_window(&ctx, window, &title, false, on_top, &mut Asked::default());
        }
        self.pinned.retain(|w| w.open);
    }
}

fn show_window(ctx: &egui::Context, window: &mut Window, title: &str, pinnable: bool, on_top: bool, asked: &mut Asked) {
    let buttons = |ui: &mut Ui, popped_out: &mut bool, asked: &mut Asked| {
        ui.horizontal(|ui| {
            if pinnable && ui.small_button("Pin").on_hover_text("Keep it in a window of its own").clicked() {
                asked.pin = true;
            }
            let label = if *popped_out { "Back in the app" } else { "Pop out" };
            if ui.small_button(label).on_hover_text("A window of its own, on top of the others").clicked() {
                *popped_out = !*popped_out;
            }
        });
    };
    if window.popped_out {
        let builder = ViewportBuilder::default().with_title(title).with_inner_size([900.0, 600.0]);
        let builder = if on_top { builder.with_always_on_top() } else { builder };
        let Window { id, viewer, open, popped_out } = window;
        ctx.show_viewport_immediate(ViewportId::from_hash_of(*id), builder, |ui, class| {
            let mut body = |ui: &mut Ui| {
                buttons(ui, popped_out, asked);
                viewer.show(ui);
            };
            if class == ViewportClass::EmbeddedWindow {
                body(ui);
            } else {
                egui::CentralPanel::default().show(ui, body);
            }
            // its own close button closes it
            if ui.input(|i| i.viewport().close_requested()) {
                *open = false;
            }
        });
    } else {
        let Window { id, viewer, open, popped_out } = window;
        egui::Window::new(title).id(*id).open(open).default_size([760.0, 440.0]).resizable(true).constrain(true).show(ctx, |ui| {
            buttons(ui, popped_out, asked);
            viewer.show(ui);
        });
    }
}
