//! A JSON tree: objects and arrays fold, values in the colour of their type, long arrays and strings cut short.

use egui::{Color32, RichText, Ui};
use serde_json::Value;

/// How many items of an array (or keys of an object) are shown before "… n more".
const MAX_ITEMS: usize = 200;
/// How many characters of a string are shown before "…" (the whole string is on hover).
const MAX_CHARS: usize = 300;

/// A body as received: JSON text is read as JSON, anything else is shown as it is.
#[derive(Debug, Clone, PartialEq)]
pub enum Body {
    Json(Value),
    Text(String),
}

impl Body {
    pub fn from_value(value: &Value) -> Body {
        match value {
            Value::String(text) => match serde_json::from_str::<Value>(text) {
                Ok(json @ (Value::Object(_) | Value::Array(_))) => Body::Json(json),
                _ => Body::Text(text.clone()),
            },
            other => Body::Json(other.clone()),
        }
    }

    /// The body as one would paste it: JSON indented, text as it is.
    pub fn to_text(&self) -> String {
        match self {
            Body::Json(v) => serde_json::to_string_pretty(v).unwrap_or_default(),
            Body::Text(t) => t.clone(),
        }
    }
}

struct Colors {
    key: Color32,
    string: Color32,
    number: Color32,
    other: Color32,
    muted: Color32,
}

fn colors(ui: &Ui) -> Colors {
    if ui.visuals().dark_mode {
        Colors {
            key: Color32::from_rgb(156, 220, 254),
            string: Color32::from_rgb(206, 145, 120),
            number: Color32::from_rgb(181, 206, 168),
            other: Color32::from_rgb(86, 156, 214),
            muted: Color32::GRAY,
        }
    } else {
        Colors {
            key: Color32::from_rgb(0, 16, 128),
            string: Color32::from_rgb(163, 21, 21),
            number: Color32::from_rgb(9, 134, 88),
            other: Color32::from_rgb(0, 0, 255),
            muted: Color32::DARK_GRAY,
        }
    }
}

/// Shows `body`: a tree for JSON (the first two levels open), monospace text otherwise.
pub fn show(ui: &mut Ui, id: egui::Id, body: &Body) {
    match body {
        Body::Text(text) if text.is_empty() => {
            ui.weak("(empty)");
        }
        Body::Text(text) => {
            ui.add(egui::Label::new(RichText::new(cut(text, 20_000)).monospace()).wrap());
        }
        Body::Json(value) => {
            let c = colors(ui);
            node(ui, id, None, value, 0, &c);
        }
    }
}

fn cut(text: &str, max: usize) -> String {
    match text.char_indices().nth(max) {
        Some((at, _)) => format!("{}…", &text[..at]),
        None => text.to_owned(),
    }
}

fn key_text(key: Option<&str>, c: &Colors) -> Option<RichText> {
    key.map(|k| RichText::new(format!("{k}:")).monospace().color(c.key))
}

fn node(ui: &mut Ui, id: egui::Id, key: Option<&str>, value: &Value, depth: usize, c: &Colors) {
    match value {
        Value::Object(map) => {
            let title = format!("{{{}}}", map.len());
            folder(ui, id, key, &title, depth, c, |ui| {
                for (i, (k, v)) in map.iter().enumerate() {
                    if i == MAX_ITEMS {
                        ui.weak(format!("… {} more", map.len() - MAX_ITEMS));
                        break;
                    }
                    node(ui, id.with(k), Some(k), v, depth + 1, c);
                }
            });
        }
        Value::Array(items) => {
            let title = format!("[{}]", items.len());
            folder(ui, id, key, &title, depth, c, |ui| {
                for (i, v) in items.iter().enumerate() {
                    if i == MAX_ITEMS {
                        ui.weak(format!("… {} more", items.len() - MAX_ITEMS));
                        break;
                    }
                    node(ui, id.with(i), Some(&i.to_string()), v, depth + 1, c);
                }
            });
        }
        leaf => {
            ui.horizontal_wrapped(|ui| {
                ui.spacing_mut().item_spacing.x = 6.0;
                if let Some(k) = key_text(key, c) {
                    ui.label(k);
                }
                let (text, color) = match leaf {
                    Value::String(s) => (format!("\"{}\"", cut(s, MAX_CHARS)), c.string),
                    Value::Number(n) => (n.to_string(), c.number),
                    Value::Bool(b) => (b.to_string(), c.other),
                    _ => ("null".to_owned(), c.other),
                };
                let response = ui.label(RichText::new(text).monospace().color(color));
                if let Value::String(s) = leaf
                    && s.chars().count() > MAX_CHARS
                {
                    response.on_hover_text(cut(s, 5_000));
                }
            });
        }
    }
}

#[allow(clippy::too_many_arguments)]
fn folder(ui: &mut Ui, id: egui::Id, key: Option<&str>, title: &str, depth: usize, c: &Colors, body: impl FnOnce(&mut Ui)) {
    let state = egui::collapsing_header::CollapsingState::load_with_default_open(ui.ctx(), id, depth < 2);
    state
        .show_header(ui, |ui| {
            if let Some(k) = key_text(key, c) {
                ui.label(k);
            }
            ui.label(RichText::new(title).monospace().color(c.muted));
        })
        .body(body);
}
