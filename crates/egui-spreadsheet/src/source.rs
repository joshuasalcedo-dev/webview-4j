//! Data sources, for the [`crate::viewer`]: data to look at, not to edit, as big as it gets, and there before it is
//! all there. A source says its columns, how many rows it has so far, each cell's value, and whether it is still
//! loading. Anything can be one ([`DataSource`]); [`Rows`] is one ready to use: rows pushed from any thread (a file
//! being read, a query's results, a feed), kept by column.

use std::borrow::Cow;
use std::cmp::Ordering;
use std::sync::atomic::{AtomicU64, Ordering as Atomic};
use std::sync::{Arc, RwLock, RwLockReadGuard, RwLockWriteGuard};

/// A cell's value.
#[derive(Debug, Clone, PartialEq, Default)]
pub enum Value {
    #[default]
    Empty,
    Number(f64),
    Bool(bool),
    Text(Box<str>),
}

impl Value {
    /// A field as read from text: a number when it is one (`12`, `-3.5`, `1e3`), else text; empty is [`Value::Empty`].
    pub fn parse(field: &str) -> Value {
        let trimmed = field.trim();
        if trimmed.is_empty() {
            return Value::Empty;
        }
        // "inf", "nan" and the like are words, not numbers; a leading zero ("007", a code) stays text
        let numeric = trimmed.bytes().all(|b| b.is_ascii_digit() || matches!(b, b'.' | b'-' | b'+' | b'e' | b'E'));
        let code = trimmed.len() > 1 && trimmed.starts_with('0') && !trimmed.starts_with("0.");
        match trimmed.parse::<f64>() {
            Ok(n) if numeric && !code && n.is_finite() => Value::Number(n),
            _ => Value::Text(field.into()),
        }
    }

    /// As shown: numbers without a trailing `.0`, and at most 10 decimals.
    pub fn text(&self) -> Cow<'_, str> {
        match self {
            Value::Empty => Cow::Borrowed(""),
            Value::Text(t) => Cow::Borrowed(t),
            Value::Bool(b) => Cow::Borrowed(if *b { "TRUE" } else { "FALSE" }),
            Value::Number(n) if n.fract() == 0.0 && n.abs() < 1e15 => Cow::Owned(format!("{n:.0}")),
            Value::Number(n) => Cow::Owned(format!("{n:.10}").trim_end_matches('0').trim_end_matches('.').to_owned()),
        }
    }

    pub fn number(&self) -> Option<f64> {
        match self {
            Value::Number(n) => Some(*n),
            _ => None,
        }
    }

    pub fn is_empty(&self) -> bool {
        matches!(self, Value::Empty)
    }

    /// Excel's sort order: numbers, text (ignoring case), true/false; empty last whichever way.
    pub fn sort_cmp(&self, other: &Value, descending: bool) -> Ordering {
        let rank = |v: &Value| match v {
            Value::Number(_) => 0,
            Value::Text(_) => 1,
            Value::Bool(_) => 2,
            Value::Empty => 3,
        };
        let (a, b) = (rank(self), rank(other));
        if a == 3 || b == 3 {
            return a.cmp(&b);
        }
        let order = match (self, other) {
            (Value::Number(x), Value::Number(y)) => x.total_cmp(y),
            (Value::Text(x), Value::Text(y)) => cmp_ignoring_case(x, y),
            (Value::Bool(x), Value::Bool(y)) => x.cmp(y),
            _ => a.cmp(&b),
        };
        if descending { order.reverse() } else { order }
    }
}

fn cmp_ignoring_case(a: &str, b: &str) -> Ordering {
    a.chars().flat_map(char::to_lowercase).cmp(b.chars().flat_map(char::to_lowercase))
}

impl From<&str> for Value {
    fn from(text: &str) -> Value {
        if text.is_empty() { Value::Empty } else { Value::Text(text.into()) }
    }
}
impl From<String> for Value {
    fn from(text: String) -> Value {
        if text.is_empty() { Value::Empty } else { Value::Text(text.into_boxed_str()) }
    }
}
impl From<f64> for Value {
    fn from(n: f64) -> Value {
        Value::Number(n)
    }
}
impl From<i64> for Value {
    fn from(n: i64) -> Value {
        Value::Number(n as f64)
    }
}
impl From<i32> for Value {
    fn from(n: i32) -> Value {
        Value::Number(f64::from(n))
    }
}
impl From<bool> for Value {
    fn from(b: bool) -> Value {
        Value::Bool(b)
    }
}
impl<T: Into<Value>> From<Option<T>> for Value {
    fn from(v: Option<T>) -> Value {
        v.map_or(Value::Empty, Into::into)
    }
}

/// Where a source is.
#[derive(Debug, Clone, PartialEq, Default)]
pub enum Status {
    /// All there.
    #[default]
    Done,
    /// Still coming: how far, 0 to 1, when it is known.
    Loading { progress: Option<f32> },
    /// Stopped: why (the rows that came stay).
    Failed(String),
}

/// Data the viewer shows. Read from the UI thread while it may be written from others: take a consistent look with
/// short reads (the viewer asks only for what is on screen, and for whole columns to sort or find).
pub trait DataSource: Send + Sync {
    fn name(&self) -> String;
    fn columns(&self) -> Vec<String>;
    /// The rows there so far.
    fn row_count(&self) -> usize;
    /// A cell (row and column from 0); [`Value::Empty`] past the end.
    fn value(&self, row: usize, col: usize) -> Value;
    /// Changes whenever anything does (rows came, columns, status): the viewer works things out again then.
    fn version(&self) -> u64;
    fn status(&self) -> Status {
        Status::Done
    }
    /// A column's values for some rows (to sort or find by): a source may give them at once, faster.
    fn column(&self, col: usize, rows: std::ops::Range<usize>) -> Vec<Value> {
        rows.map(|r| self.value(r, col)).collect()
    }
}

/// Rows pushed in, from any thread: a source that grows while it is shown. Cheap to clone (the same rows).
#[derive(Clone)]
pub struct Rows {
    shared: Arc<Shared>,
}

struct Shared {
    name: RwLock<String>,
    data: RwLock<Data>,
    version: AtomicU64,
}

#[derive(Default)]
struct Data {
    columns: Vec<String>,
    /// By column: `cells[col][row]`.
    cells: Vec<Vec<Value>>,
    rows: usize,
    status: Status,
}

impl Rows {
    /// Empty, with these columns (more come when a row is longer).
    pub fn new(name: &str, columns: impl IntoIterator<Item = impl Into<String>>) -> Rows {
        let columns: Vec<String> = columns.into_iter().map(Into::into).collect();
        let cells = vec![Vec::new(); columns.len()];
        Rows {
            shared: Arc::new(Shared {
                name: RwLock::new(name.to_owned()),
                data: RwLock::new(Data { columns, cells, rows: 0, status: Status::Done }),
                version: AtomicU64::new(1),
            }),
        }
    }

    /// As a source for the viewer.
    pub fn source(&self) -> Arc<dyn DataSource> {
        Arc::new(self.clone())
    }

    fn read(&self) -> RwLockReadGuard<'_, Data> {
        self.shared.data.read().unwrap_or_else(|e| e.into_inner())
    }

    fn write(&self) -> RwLockWriteGuard<'_, Data> {
        self.shared.data.write().unwrap_or_else(|e| e.into_inner())
    }

    fn bump(&self) {
        self.shared.version.fetch_add(1, Atomic::Relaxed);
    }

    pub fn set_name(&self, name: &str) {
        *self.shared.name.write().unwrap_or_else(|e| e.into_inner()) = name.to_owned();
        self.bump();
    }

    /// Names the columns (the first ones; a row longer than them adds `Column N`).
    pub fn set_columns(&self, columns: impl IntoIterator<Item = impl Into<String>>) {
        let mut data = self.write();
        let names: Vec<String> = columns.into_iter().map(Into::into).collect();
        let rows = data.rows;
        for (i, name) in names.into_iter().enumerate() {
            if i < data.columns.len() {
                data.columns[i] = name;
            } else {
                data.columns.push(name);
                data.cells.push(vec![Value::Empty; rows]);
            }
        }
        drop(data);
        self.bump();
    }

    /// Adds a row.
    pub fn push(&self, row: impl IntoIterator<Item = impl Into<Value>>) {
        self.push_many(std::iter::once(row.into_iter().map(Into::into).collect::<Vec<Value>>()));
    }

    /// Adds rows at once (one lock: the way to add many).
    pub fn push_many(&self, rows: impl IntoIterator<Item = Vec<Value>>) {
        let mut data = self.write();
        for row in rows {
            data.add(row);
        }
        drop(data);
        self.bump();
    }

    /// Still loading: how far (0 to 1) when known.
    pub fn loading(&self, progress: Option<f32>) {
        self.write().status = Status::Loading { progress: progress.map(|p| p.clamp(0.0, 1.0)) };
        self.bump();
    }

    /// All there.
    pub fn finish(&self) {
        self.write().status = Status::Done;
        self.bump();
    }

    /// Stopped (the rows that came stay).
    pub fn fail(&self, why: impl Into<String>) {
        self.write().status = Status::Failed(why.into());
        self.bump();
    }

    /// No rows (the columns stay).
    pub fn clear(&self) {
        let mut data = self.write();
        data.rows = 0;
        data.cells.iter_mut().for_each(Vec::clear);
        drop(data);
        self.bump();
    }

    /// Adds rows from CSV text (one line or many; quoted fields may span lines). With no columns yet, its first
    /// row names them. Err, adding nothing, on text that is not CSV.
    pub fn push_csv(&self, text: &str, separator: u8) -> Result<(), String> {
        let mut reader = csv::ReaderBuilder::new().has_headers(false).flexible(true).delimiter(separator).from_reader(text.as_bytes());
        let mut records = Vec::new();
        for (line, record) in (1..).zip(reader.records()) {
            records.push(record.map_err(|e| format!("line {line}: {e}"))?);
        }
        let mut records = records.into_iter();
        if self.read().columns.is_empty()
            && let Some(header) = records.next()
        {
            self.set_columns(header.iter());
        }
        self.push_many(records.map(|r| r.iter().map(Value::parse).collect()));
        Ok(())
    }

    /// A CSV's rows, read in the background (on the web, at once): the first row names the columns. The rows show
    /// as they are read.
    pub fn load_csv(bytes: Vec<u8>, name: &str) -> Rows {
        let rows = Rows::new(name, Vec::<String>::new());
        rows.loading(Some(0.0));
        let writer = rows.clone();
        let read = move || {
            if let Err(e) = writer.read_csv(&bytes) {
                writer.fail(e);
            }
        };
        #[cfg(not(target_arch = "wasm32"))]
        std::thread::Builder::new().name(format!("csv {name}")).spawn(read).expect("a thread to read the CSV");
        #[cfg(target_arch = "wasm32")]
        read();
        rows
    }

    fn read_csv(&self, bytes: &[u8]) -> Result<(), String> {
        const CHUNK: usize = 20_000;
        let text = crate::load::csv_text(bytes);
        let total = text.len().max(1) as f32;
        let mut reader = csv::ReaderBuilder::new()
            .has_headers(false)
            .flexible(true)
            .delimiter(crate::load::separator(&text))
            .from_reader(text.as_bytes());
        let mut records = reader.records();
        match records.next() {
            Some(header) => self.set_columns(header.map_err(|e| format!("line 1: {e}"))?.iter()),
            None => {
                self.finish();
                return Ok(());
            }
        }
        let mut chunk = Vec::with_capacity(CHUNK);
        for (line, record) in (2..).zip(records) {
            let record = record.map_err(|e| format!("line {line}: {e}"))?;
            chunk.push(record.iter().map(Value::parse).collect());
            if chunk.len() == CHUNK {
                let at = record.position().map_or(0, |p| p.byte()) as f32;
                self.push_many(std::mem::replace(&mut chunk, Vec::with_capacity(CHUNK)));
                self.loading(Some(at / total));
            }
        }
        self.push_many(chunk);
        self.finish();
        Ok(())
    }
}

impl Data {
    fn add(&mut self, mut row: Vec<Value>) {
        while row.len() > self.columns.len() {
            self.columns.push(format!("Column {}", self.columns.len() + 1));
            self.cells.push(vec![Value::Empty; self.rows]);
        }
        row.resize(self.columns.len(), Value::Empty);
        for (col, value) in row.into_iter().enumerate() {
            self.cells[col].push(value);
        }
        self.rows += 1;
    }
}

impl DataSource for Rows {
    fn name(&self) -> String {
        self.shared.name.read().unwrap_or_else(|e| e.into_inner()).clone()
    }

    fn columns(&self) -> Vec<String> {
        self.read().columns.clone()
    }

    fn row_count(&self) -> usize {
        self.read().rows
    }

    fn value(&self, row: usize, col: usize) -> Value {
        self.read().cells.get(col).and_then(|c| c.get(row)).cloned().unwrap_or_default()
    }

    fn version(&self) -> u64 {
        self.shared.version.load(Atomic::Relaxed)
    }

    fn status(&self) -> Status {
        self.read().status.clone()
    }

    fn column(&self, col: usize, rows: std::ops::Range<usize>) -> Vec<Value> {
        let data = self.read();
        let Some(cells) = data.cells.get(col) else { return vec![Value::Empty; rows.len()] };
        let end = rows.end.min(cells.len());
        let mut out = cells[rows.start.min(end)..end].to_vec();
        out.resize(rows.len(), Value::Empty);
        out
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn fields_are_numbers_when_they_are_numbers() {
        assert_eq!(Value::parse("12"), Value::Number(12.0));
        assert_eq!(Value::parse(" -3.5 "), Value::Number(-3.5));
        assert_eq!(Value::parse("007"), Value::Text("007".into()), "a code keeps its zeros");
        assert_eq!(Value::parse("inf"), Value::Text("inf".into()));
        assert_eq!(Value::parse("2026-01-05"), Value::Text("2026-01-05".into()));
        assert_eq!(Value::parse(""), Value::Empty);
        assert_eq!(Value::Number(0.1 + 0.2).text(), "0.3");
        assert_eq!(Value::Number(1500.0).text(), "1500");
    }

    #[test]
    fn rows_grow_and_widen() {
        let rows = Rows::new("t", ["a", "b"]);
        rows.push([Value::from(1), Value::from("x")]);
        rows.push([Value::from(2), Value::from("y"), Value::from(true)]);
        assert_eq!(rows.columns(), ["a", "b", "Column 3"]);
        assert_eq!(rows.row_count(), 2);
        assert_eq!(rows.value(0, 2), Value::Empty);
        assert_eq!(rows.value(1, 2), Value::Bool(true));
        assert_eq!(rows.column(0, 0..3), [Value::Number(1.0), Value::Number(2.0), Value::Empty]);
    }
}
