//! egui-4j: components drawn by egui (charts, editors...) for webview-4j.
//!
//! Each component takes its data as JSON props ([`Component::update`], only when the data changes) and draws
//! itself every frame ([`Component::ui`]), reporting what the user did as JSON events. Nothing here knows where
//! it runs: the same code runs in a native window (`cargo run --example <component>`), in the tests, and as wasm
//! in the page, where the host passes props from Java and sends the events back.

#[cfg(target_arch = "wasm32")]
mod console;
pub mod dev_logger;
pub mod json_view;
pub mod line_chart;
pub mod monitor;
#[cfg(target_arch = "wasm32")]
pub mod web;

use serde_json::{Map, Value};

/// A component: data in as props, events out, drawn by egui.
pub trait Component {
    /// New props: what is given replaces what was there, what is left out stays. On an error nothing changes.
    fn update(&mut self, props: Value) -> Result<(), String>;

    /// Draws the component in `ui` (every frame) and records what the user did in `events`.
    fn ui(&mut self, ui: &mut egui::Ui, events: &mut Events);
}

/// What the user did, in order, as `{"event": name, ...fields}`; the host takes them after each frame.
#[derive(Debug, Default)]
pub struct Events(Vec<Value>);

impl Events {
    pub fn emit(&mut self, event: &str, fields: Value) {
        let mut object = match fields {
            Value::Object(map) => map,
            Value::Null => Map::new(),
            other => Map::from_iter([("value".to_owned(), other)]),
        };
        object.insert("event".to_owned(), Value::from(event));
        let object = Value::Object(object);
        log::trace!("event {object}");
        self.0.push(object);
    }

    /// The events so far, oldest first; the list is empty afterwards.
    pub fn drain(&mut self) -> Vec<Value> {
        std::mem::take(&mut self.0)
    }

    /// Whether an event `name` is waiting.
    pub fn has(&self, name: &str) -> bool {
        self.0.iter().any(|e| e["event"] == name)
    }

    pub fn is_empty(&self) -> bool {
        self.0.is_empty()
    }
}

/// Makes a new component of one kind.
pub type Factory = fn() -> Box<dyn Component>;

/// The components a page can create by name: the built-in ones, and an app's own ([`Registry::register`]).
pub struct Registry {
    factories: Vec<(String, Factory)>,
}

impl Default for Registry {
    /// The built-in components.
    fn default() -> Self {
        let mut registry = Registry { factories: Vec::new() };
        registry.register("line-chart", || Box::new(line_chart::LineChart::default()));
        registry.register("monitor", || Box::new(monitor::Monitor::default()));
        registry.register("dev-logger", || Box::new(dev_logger::DevLogger::default()));
        registry
    }
}

impl Registry {
    /// Adds a component (or replaces the one with that name).
    pub fn register(&mut self, name: &str, factory: Factory) {
        self.factories.retain(|(n, _)| n != name);
        self.factories.push((name.to_owned(), factory));
    }

    pub fn create(&self, name: &str) -> Option<Box<dyn Component>> {
        self.factories.iter().find(|(n, _)| n == name).map(|(_, factory)| factory())
    }

    pub fn names(&self) -> impl Iterator<Item = &str> {
        self.factories.iter().map(|(n, _)| n.as_str())
    }
}

/// `"#rrggbb"` or `"#rrggbbaa"` as a colour.
pub(crate) fn parse_color(text: &str) -> Result<egui::Color32, String> {
    egui::Color32::from_hex(text).map_err(|_| format!("not a colour: {text:?} (expected #rrggbb or #rrggbbaa)"))
}
