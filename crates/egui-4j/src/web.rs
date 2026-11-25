//! The page's side (wasm only): mounts a component on a `<canvas>`, passes it props, and hands its events to a
//! JavaScript callback as JSON strings. `egui-canvas.tsx` is the React host that uses it.
//!
//! ```js
//! const chart = await EguiComponent.mount(canvas, "line-chart", JSON.stringify(props), (json) => ...)
//! chart.update(JSON.stringify({ append: [...] }))   // throws, changing nothing, on bad props
//! chart.set_theme(true, "#0a84ff", "#1e1e1e")
//! chart.destroy()
//! ```

use std::any::Any;

use serde_json::Value;
use wasm_bindgen::prelude::*;

use crate::{Component, Events, Registry, parse_color};

/// The colours the page gives the component, so it looks like the kit around it.
#[derive(Clone)]
struct Theme {
    dark: bool,
    accent: Option<egui::Color32>,
    background: Option<egui::Color32>,
}

impl Theme {
    fn visuals(&self) -> egui::Visuals {
        let mut visuals = if self.dark { egui::Visuals::dark() } else { egui::Visuals::light() };
        if let Some(accent) = self.accent {
            visuals.selection.bg_fill = accent;
            visuals.hyperlink_color = accent;
            // what is written on the accent (a chosen toggle, selected text): white on a dark accent, black on a
            // light one (egui's own is a shade of its blue, unreadable on a strong accent)
            let [r, g, b, _] = accent.to_array();
            let luminance = 0.2126 * f32::from(r) + 0.7152 * f32::from(g) + 0.0722 * f32::from(b);
            let on_accent = if luminance < 150.0 { egui::Color32::WHITE } else { egui::Color32::BLACK };
            visuals.selection.stroke = egui::Stroke::new(1.0, on_accent);
        }
        if let Some(background) = self.background {
            visuals.panel_fill = background;
            visuals.window_fill = background;
        }
        visuals
    }
}

struct HostApp {
    component: Box<dyn Component>,
    events: Events,
    on_event: js_sys::Function,
    theme: Theme,
    theme_changed: bool,
    ctx: egui::Context,
}

impl eframe::App for HostApp {
    fn ui(&mut self, ui: &mut egui::Ui, _frame: &mut eframe::Frame) {
        if self.theme_changed {
            ui.ctx().set_visuals(self.theme.visuals());
            self.theme_changed = false;
        }
        // no background given: transparent, so the page shows through and the component sits on the kit's own
        let frame = match self.theme.background {
            Some(background) => egui::Frame::NONE.fill(background),
            None => egui::Frame::NONE,
        };
        egui::CentralPanel::default().frame(frame).show(ui, |ui| {
            self.component.ui(ui, &mut self.events);
        });
        for event in self.events.drain() {
            if let Err(e) = self.on_event.call1(&JsValue::NULL, &JsValue::from_str(&event.to_string())) {
                log::error!("the page's event handler failed on {event}: {e:?}");
            }
        }
    }

    fn clear_color(&self, _visuals: &egui::Visuals) -> [f32; 4] {
        self.theme.background.map_or([0.0; 4], |b| b.to_normalized_gamma_f32())
    }

    fn as_any_mut(&mut self) -> Option<&mut dyn Any> {
        Some(self)
    }
}

fn parse_props(json: &str) -> Result<Value, JsValue> {
    serde_json::from_str(json).map_err(|e| {
        log::warn!("props are not JSON: {e}");
        JsValue::from_str(&format!("props are not JSON: {e}"))
    })
}

fn optional_color(text: Option<String>) -> Result<Option<egui::Color32>, JsValue> {
    text.filter(|t| !t.is_empty()).as_deref().map(parse_color).transpose().map_err(|e| JsValue::from_str(&e))
}

/// One component on one canvas.
#[wasm_bindgen]
pub struct EguiComponent {
    runner: eframe::WebRunner,
    name: String,
}

#[wasm_bindgen]
impl EguiComponent {
    /// The components this module can make.
    pub fn names() -> Vec<String> {
        Registry::default().names().map(str::to_owned).collect()
    }

    /// How much of egui-4j's log reaches the console (and Java): "off", "error", "warn", "info" (at first),
    /// "debug" (props given) or "trace" (also each event, and each point streamed in).
    pub fn set_log_level(level: &str) -> Result<(), JsValue> {
        crate::console::install();
        crate::console::set_level(level).map_err(|e| JsValue::from_str(&e))
    }

    /// Makes the component `name` with its first `props` (JSON) and starts drawing it on `canvas`; each event is
    /// passed to `on_event` as a JSON string. Fails for an unknown name or bad props.
    pub async fn mount(
        canvas: web_sys::HtmlCanvasElement,
        name: String,
        props: String,
        on_event: js_sys::Function,
        dark: bool,
    ) -> Result<EguiComponent, JsValue> {
        crate::console::install();
        let Some(mut component) = Registry::default().create(&name) else {
            log::error!("no component {name:?} (there are: {})", Self::names().join(", "));
            return Err(JsValue::from_str(&format!("egui-4j has no component {name:?}")));
        };
        component.update(parse_props(&props)?).map_err(|e| JsValue::from_str(&e))?;

        let runner = eframe::WebRunner::new();
        let theme = Theme { dark, accent: None, background: None };
        runner
            .start(
                canvas,
                eframe::WebOptions::default(),
                Box::new(move |cc| {
                    cc.egui_ctx.set_visuals(theme.visuals());
                    Ok(Box::new(HostApp {
                        component,
                        events: Events::default(),
                        on_event,
                        theme,
                        theme_changed: false,
                        ctx: cc.egui_ctx.clone(),
                    }))
                }),
            )
            .await
            .inspect_err(|e| log::error!("{name} could not start: {e:?}"))?;
        log::info!("{name} is shown");
        Ok(EguiComponent { runner, name })
    }

    fn app(&self) -> Result<std::cell::RefMut<'_, HostApp>, JsValue> {
        self.runner.app_mut::<HostApp>().ok_or_else(|| JsValue::from_str("egui-4j: the component has stopped (it panicked)"))
    }

    /// New props (JSON): what is given replaces what was there. Throws, changing nothing, on bad props.
    pub fn update(&self, props: &str) -> Result<(), JsValue> {
        let props = parse_props(props)?;
        let mut app = self.app()?;
        app.component.update(props).map_err(|e| JsValue::from_str(&e))?;
        app.ctx.request_repaint();
        Ok(())
    }

    /// Light or dark, the accent colour, and the background (`#rrggbb`; empty or left out: transparent, the page's).
    pub fn set_theme(&self, dark: bool, accent: Option<String>, background: Option<String>) -> Result<(), JsValue> {
        let theme = Theme { dark, accent: optional_color(accent)?, background: optional_color(background)? };
        let mut app = self.app()?;
        app.theme = theme;
        app.theme_changed = true;
        app.ctx.request_repaint();
        Ok(())
    }

    /// Stops drawing and lets go of the canvas's event listeners.
    pub fn destroy(&self) {
        log::debug!("{} is gone", self.name);
        self.runner.destroy();
    }
}
