//! live-csv: rows that keep coming (an import, a log), shown as they come, scrolled to the newest: the viewer of
//! egui-spreadsheet (sort, filter, search, copy) as a component. Rows are pushed with each update.
//!
//! Props (each optional; an update with a bad one changes nothing):
//! - `name`: its title.
//! - `columns`: `["time", "level", "message"]`, the columns' names.
//! - `append`: rows to add, `[[1, "info", "started"], ...]` (numbers, text, true/false, null for empty).
//! - `csv`: CSV text to add, a line or many; with no columns yet, its first line names them.
//! - `separator`: of the CSV text, `","` at first.
//! - `clear`: `true` takes the rows away (before the update's own rows are added).
//! - `follow`: keep the newest rows in view (`true` at first).
//! - `progress`: still coming, how far from 0 to 1 (`null`: unknown, a spinning bar); `done`: `true`, all there;
//!   `failed`: why it stopped.

use egui_spreadsheet::source::{Rows, Value as Cell};
use egui_spreadsheet::Viewer;
use serde_json::Value;

use crate::{Component, Events};

pub struct LiveCsv {
    rows: Rows,
    viewer: Viewer,
    separator: u8,
}

impl Default for LiveCsv {
    fn default() -> Self {
        let rows = Rows::new("rows", Vec::<String>::new());
        let mut viewer = Viewer::new(rows.source());
        viewer.follow = true;
        LiveCsv { rows, viewer, separator: b',' }
    }
}

impl LiveCsv {
    /// The rows (to push from Rust).
    pub fn rows(&self) -> &Rows {
        &self.rows
    }

    pub fn viewer(&mut self) -> &mut Viewer {
        &mut self.viewer
    }
}

fn cell(value: &Value) -> Result<Cell, String> {
    Ok(match value {
        Value::Null => Cell::Empty,
        Value::Bool(b) => Cell::Bool(*b),
        Value::Number(n) => Cell::Number(n.as_f64().ok_or("a number too big")?),
        Value::String(s) => Cell::from(s.as_str()),
        other => return Err(format!("a cell is a number, text, true/false or null, not {other}")),
    })
}

fn strings(value: &Value, what: &str) -> Result<Vec<String>, String> {
    value
        .as_array()
        .ok_or(format!("{what} is a list of names"))?
        .iter()
        .map(|v| v.as_str().map(str::to_owned).ok_or(format!("{what} is a list of names")))
        .collect()
}

impl Component for LiveCsv {
    fn update(&mut self, props: Value) -> Result<(), String> {
        let props = props.as_object().ok_or("props are an object")?;
        // read all of it first: a bad prop changes nothing
        let name = props.get("name").map(|v| v.as_str().map(str::to_owned).ok_or("name is text")).transpose()?;
        let columns = props.get("columns").map(|v| strings(v, "columns")).transpose()?;
        let append: Option<Vec<Vec<Cell>>> = props
            .get("append")
            .map(|rows| {
                rows.as_array()
                    .ok_or("append is a list of rows")?
                    .iter()
                    .map(|row| row.as_array().ok_or("a row is a list of cells".to_owned())?.iter().map(cell).collect())
                    .collect::<Result<_, String>>()
            })
            .transpose()?;
        let separator = match props.get("separator") {
            None => self.separator,
            Some(v) => match v.as_str().map(str::as_bytes) {
                Some([b]) => *b,
                _ => return Err("separator is one character, e.g. \",\" or \"\\t\"".into()),
            },
        };
        let csv = props.get("csv").map(|v| v.as_str().ok_or("csv is text")).transpose()?;
        let follow = props.get("follow").map(|v| v.as_bool().ok_or("follow is true or false")).transpose()?;
        let clear = props.get("clear").map(|v| v.as_bool().ok_or("clear is true or false")).transpose()?.unwrap_or(false);
        let progress = match props.get("progress") {
            None => None,
            Some(Value::Null) => Some(None),
            Some(v) => Some(Some(v.as_f64().ok_or("progress is a number from 0 to 1, or null")? as f32)),
        };
        let done = props.get("done").map(|v| v.as_bool().ok_or("done is true or false")).transpose()?.unwrap_or(false);
        let failed = props.get("failed").map(|v| v.as_str().map(str::to_owned).ok_or("failed is text")).transpose()?;
        // the CSV is checked before anything is added
        if let Some(text) = csv {
            // the same reading push_csv does, into a throwaway
            Rows::new("", Vec::<String>::new()).push_csv(text, separator)?;
        }

        if let Some(name) = name {
            self.rows.set_name(&name);
        }
        if clear {
            self.rows.clear();
        }
        if let Some(columns) = columns {
            self.rows.set_columns(columns);
        }
        if let Some(rows) = append {
            log::trace!("{} rows", rows.len());
            self.rows.push_many(rows);
        }
        self.separator = separator;
        if let Some(text) = csv {
            self.rows.push_csv(text, separator)?;
        }
        if let Some(follow) = follow {
            self.viewer.follow = follow;
        }
        if let Some(progress) = progress {
            self.rows.loading(progress);
        }
        if done {
            self.rows.finish();
        }
        if let Some(why) = failed {
            self.rows.fail(why);
        }
        Ok(())
    }

    fn ui(&mut self, ui: &mut egui::Ui, _events: &mut Events) {
        self.viewer.show(ui);
    }
}

