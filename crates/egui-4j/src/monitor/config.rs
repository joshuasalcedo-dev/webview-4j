//! What a monitor shows, as its user describes it: where the time is, the columns, what an error is, the
//! statistics, the groups, the charts and the details. Everything names fields of the messages by path
//! (`response.status`, `items.0.id`); text can be a template (`"{method} {url}"`, `"{level|upper}"`).

use std::collections::BTreeMap;

use serde::Deserialize;
use serde_json::Value;

#[derive(Debug, Clone, Default, Deserialize)]
#[serde(rename_all = "camelCase", deny_unknown_fields)]
pub struct Config {
    #[serde(default)]
    pub title: String,
    /// The message's time: milliseconds since 1970, or ISO 8601 text. Left out: when it arrived.
    pub time: Option<String>,
    #[serde(default)]
    pub columns: Vec<Column>,
    /// When a message is an error (a failed call, an error line): shown red, counted, filtered.
    pub error: Option<Rule>,
    /// A field whose values become toggles with counts ("Log 12", "HTTP 40") to show or hide each.
    pub facet: Option<String>,
    /// The facet's values in order, and their names (left out: as they come, named as they are).
    #[serde(default)]
    pub facet_values: Vec<FacetValue>,
    /// Shown above the panes, always (a summary: "120 calls", "p95 340 ms").
    #[serde(default)]
    pub summary: Vec<Stat>,
    /// The Stats pane.
    #[serde(default)]
    pub stats: Vec<Stat>,
    /// The Groups pane: messages grouped by these fields together, each group with its numbers.
    #[serde(default)]
    pub group_by: Vec<String>,
    /// The number the groups' percentiles are of (e.g. "durationMs").
    pub group_value: Option<String>,
    /// Which messages are grouped (left out: all).
    pub group_where: Option<Rule>,
    /// The group-value's format (e.g. duration).
    #[serde(default)]
    pub group_format: Format,
    #[serde(default)]
    pub charts: Vec<Chart>,
    /// The Details pane's tabs for the chosen message (left out: the whole message as JSON).
    #[serde(default)]
    pub details: Vec<Detail>,
    /// A field shown above the details, in red, when there (e.g. "error": why a call failed).
    pub details_note: Option<String>,
    /// The latest this many messages are kept (2000 at first).
    pub max_messages: Option<usize>,
    /// Statistics over the last this many seconds (left out: over every message kept).
    pub window_seconds: Option<f64>,
    /// The host searches (it keeps more than the monitor holds, e.g. in a file): typing in the search box sends a
    /// `search {text}` event, and the list shows what the host gives back (the `found` prop) while that is the search.
    #[serde(default)]
    pub host_search: bool,
}

#[derive(Debug, Clone, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct FacetValue {
    pub value: String,
    pub label: Option<String>,
}

#[derive(Debug, Clone, Default, Deserialize)]
#[serde(rename_all = "camelCase", deny_unknown_fields)]
pub struct Column {
    /// The header (left out: the field's name).
    pub label: Option<String>,
    /// The field it shows...
    pub field: Option<String>,
    /// ...or text made from the message: the first whose `when` holds (or that has none).
    #[serde(default)]
    pub text: Vec<Variant>,
    #[serde(default)]
    pub format: Format,
    /// Its width in points (left out: the rest of the row, for one column; 80 for the others).
    pub width: Option<f32>,
    /// Shown in the warning colour from this number up (e.g. a duration over 500 ms).
    pub warn_above: Option<f64>,
    /// Colours: the first whose `when` holds.
    #[serde(default)]
    pub colors: Vec<ColorWhen>,
    /// This column names the message (in links: "slowest 3 s · GET /api"). Left out: the first text column.
    #[serde(default)]
    pub summary: bool,
}

#[derive(Debug, Clone, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct Variant {
    pub when: Option<Rule>,
    pub text: String,
}

#[derive(Debug, Clone, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct ColorWhen {
    pub when: Rule,
    /// "error", "warn", "weak", "accent", or "#rrggbb".
    pub color: String,
}

#[derive(Debug, Clone, Copy, Default, PartialEq, Eq, Deserialize)]
#[serde(rename_all = "camelCase")]
pub enum Format {
    /// As it is.
    #[default]
    Text,
    /// A number, up to 2 decimals.
    Number,
    /// Milliseconds: "42 ms", "1.20 s".
    Duration,
    /// Bytes: "512 B", "1.5 KB", "3.2 MB".
    Bytes,
    /// Seconds since the first message kept, "12.345" (for a time column).
    Since,
    /// A percentage of 0 to 1: "12.5%".
    Percent,
}

/// A condition on a message. The fields given must all hold.
#[derive(Debug, Clone, Default, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct Rule {
    pub field: Option<String>,
    /// Equal (text: ignoring case).
    pub eq: Option<Value>,
    pub ne: Option<Value>,
    /// One of these (text: ignoring case).
    #[serde(rename = "in")]
    pub one_of: Option<Vec<Value>>,
    pub gt: Option<f64>,
    pub gte: Option<f64>,
    pub lt: Option<f64>,
    pub lte: Option<f64>,
    /// There (not null, not empty text), or not.
    pub exists: Option<bool>,
    /// Contains this text (ignoring case).
    pub contains: Option<String>,
    /// The message is an error (by the monitor's error rule), or not.
    pub error: Option<bool>,
    pub any: Option<Vec<Rule>>,
    pub all: Option<Vec<Rule>>,
    pub not: Option<Box<Rule>>,
}

#[derive(Debug, Clone, Deserialize)]
#[serde(rename_all = "camelCase", deny_unknown_fields)]
pub struct Stat {
    pub label: String,
    pub agg: Agg,
    /// The number it is of (for min, max, avg, sum, percentiles).
    pub field: Option<String>,
    /// Which messages count (left out: all).
    #[serde(rename = "where")]
    pub filter: Option<Rule>,
    #[serde(default)]
    pub format: Format,
    pub warn_above: Option<f64>,
    /// How it reads: "{label} {value}" at first ("{value} calls" puts the number first).
    pub text: Option<String>,
    /// For min/max: a link to that message, named by its summary column.
    #[serde(default)]
    pub link: bool,
    /// Shown in the error colour when not 0 (e.g. failed calls).
    #[serde(default)]
    pub error_when_nonzero: bool,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Deserialize)]
#[serde(rename_all = "camelCase")]
pub enum Agg {
    Count,
    /// Messages a second.
    Rate,
    ErrorCount,
    /// Errors as a share of the messages (0 to 1).
    ErrorRate,
    Min,
    Max,
    Avg,
    Sum,
    P50,
    P90,
    P95,
    P99,
}

#[derive(Debug, Clone, Deserialize)]
#[serde(rename_all = "camelCase", deny_unknown_fields)]
pub struct Chart {
    pub title: Option<String>,
    #[serde(rename = "type")]
    pub kind: ChartKind,
    /// Line: the number per time bucket (left out: how many messages).
    pub y: Option<String>,
    /// Bar: the field whose values are the bars.
    pub x: Option<String>,
    #[serde(default = "count")]
    pub agg: Agg,
    /// Line: the bucket ("500ms", "1s", "1m"); 1s at first.
    pub every: Option<String>,
    /// Line: one line per value of this field.
    pub series_by: Option<String>,
    #[serde(rename = "where")]
    pub filter: Option<Rule>,
}

fn count() -> Agg {
    Agg::Count
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Deserialize)]
#[serde(rename_all = "camelCase")]
pub enum ChartKind {
    Line,
    Bar,
}

#[derive(Debug, Clone, Deserialize)]
#[serde(rename_all = "camelCase", deny_unknown_fields)]
pub struct Detail {
    pub tab: String,
    /// Shown only for messages this holds for.
    pub when: Option<Rule>,
    /// A field shown as a JSON tree (JSON text read as JSON).
    pub json: Option<String>,
    /// Text made from the message (a template).
    pub text: Option<String>,
    /// Fields that are objects, shown as name/value tables, each under its title.
    #[serde(default)]
    pub tables: Vec<DetailTable>,
    /// What shows when the field is not there ("(none)" at first).
    pub empty: Option<String>,
}

#[derive(Debug, Clone, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct DetailTable {
    pub title: String,
    pub field: String,
}

// ---- reading messages ----

/// The value at `path` ("a.b.0.c") in `message`.
pub fn get<'a>(message: &'a Value, path: &str) -> Option<&'a Value> {
    if path.is_empty() {
        return Some(message);
    }
    path.split('.').try_fold(message, |v, key| match v {
        Value::Object(map) => map.get(key),
        Value::Array(items) => key.parse::<usize>().ok().and_then(|i| items.get(i)),
        _ => None,
    })
}

pub fn number(message: &Value, path: &str) -> Option<f64> {
    match get(message, path)? {
        Value::Number(n) => n.as_f64().filter(|f| f.is_finite()),
        Value::String(s) => s.trim().parse::<f64>().ok().filter(|f| f.is_finite()),
        _ => None,
    }
}

/// A value as text: strings as they are, null as nothing, the rest as JSON.
pub fn text_of(value: Option<&Value>) -> String {
    match value {
        None | Some(Value::Null) => String::new(),
        Some(Value::String(s)) => s.clone(),
        Some(other) => other.to_string(),
    }
}

/// Fills a template: `{path}` with the field, `{path|upper}` / `{path|lower}` changed so.
pub fn fill(template: &str, message: &Value) -> String {
    let mut out = String::with_capacity(template.len());
    let mut rest = template;
    while let Some(start) = rest.find('{') {
        out.push_str(&rest[..start]);
        match rest[start..].find('}') {
            Some(end) => {
                let inner = &rest[start + 1..start + end];
                let (path, filter) = inner.split_once('|').unwrap_or((inner, ""));
                let text = text_of(get(message, path.trim()));
                out.push_str(&match filter.trim() {
                    "upper" => text.to_uppercase(),
                    "lower" => text.to_lowercase(),
                    _ => text,
                });
                rest = &rest[start + end + 1..];
            }
            None => {
                out.push_str(&rest[start..]);
                rest = "";
            }
        }
    }
    out.push_str(rest);
    out
}

fn same(a: &Value, b: &Value) -> bool {
    match (a, b) {
        (Value::String(x), Value::String(y)) => x.eq_ignore_ascii_case(y),
        (Value::Number(x), Value::Number(y)) => x.as_f64() == y.as_f64(),
        _ => a == b,
    }
}

fn present(value: Option<&Value>) -> bool {
    match value {
        None | Some(Value::Null) => false,
        Some(Value::String(s)) => !s.is_empty(),
        _ => true,
    }
}

impl Rule {
    /// Whether it holds for `message`; `is_error` answers `"error": true/false`.
    pub fn holds(&self, message: &Value, is_error: &dyn Fn() -> bool) -> bool {
        let value = self.field.as_deref().and_then(|f| get(message, f));
        let num = || self.field.as_deref().and_then(|f| number(message, f));
        if let Some(eq) = &self.eq
            && !value.is_some_and(|v| same(v, eq))
        {
            return false;
        }
        if let Some(ne) = &self.ne
            && value.is_some_and(|v| same(v, ne))
        {
            return false;
        }
        if let Some(list) = &self.one_of
            && !value.is_some_and(|v| list.iter().any(|x| same(v, x)))
        {
            return false;
        }
        let compare = |limit: Option<f64>, ok: fn(f64, f64) -> bool| limit.is_none_or(|l| num().is_some_and(|n| ok(n, l)));
        if !compare(self.gt, |n, l| n > l)
            || !compare(self.gte, |n, l| n >= l)
            || !compare(self.lt, |n, l| n < l)
            || !compare(self.lte, |n, l| n <= l)
        {
            return false;
        }
        if let Some(exists) = self.exists
            && present(value) != exists
        {
            return false;
        }
        if let Some(text) = &self.contains
            && !text_of(value).to_lowercase().contains(&text.to_lowercase())
        {
            return false;
        }
        if let Some(error) = self.error
            && is_error() != error
        {
            return false;
        }
        if let Some(any) = &self.any
            && !any.iter().any(|r| r.holds(message, is_error))
        {
            return false;
        }
        if let Some(all) = &self.all
            && !all.iter().all(|r| r.holds(message, is_error))
        {
            return false;
        }
        if let Some(not) = &self.not
            && not.holds(message, is_error)
        {
            return false;
        }
        true
    }
}

impl Column {
    pub fn title(&self) -> String {
        self.label.clone().or_else(|| self.field.clone()).unwrap_or_default()
    }

    /// The cell's text for `message` (unformatted for a field: see [`format_value`]).
    pub fn raw_text(&self, message: &Value, is_error: &dyn Fn() -> bool) -> String {
        if let Some(field) = &self.field {
            return text_of(get(message, field));
        }
        self.text
            .iter()
            .find(|v| v.when.as_ref().is_none_or(|r| r.holds(message, is_error)))
            .map(|v| fill(&v.text, message))
            .unwrap_or_default()
    }
}

/// A number written the way `format` says.
pub fn format_number(n: f64, format: Format) -> String {
    match format {
        Format::Duration if n >= 1000.0 => format!("{:.2} s", n / 1000.0),
        Format::Duration => format!("{n:.0} ms"),
        Format::Bytes => {
            let units = ["B", "KB", "MB", "GB", "TB"];
            let mut v = n;
            let mut unit = 0;
            while v >= 1024.0 && unit < units.len() - 1 {
                v /= 1024.0;
                unit += 1;
            }
            if unit == 0 { format!("{v:.0} B") } else { format!("{v:.1} {}", units[unit]) }
        }
        Format::Since => format!("{:.3}", n / 1000.0),
        Format::Percent => format!("{:.1}%", n * 100.0),
        Format::Number | Format::Text => {
            if n.fract() == 0.0 && n.abs() < 1e15 {
                format!("{n:.0}")
            } else {
                let s = format!("{n:.2}");
                s.trim_end_matches('0').trim_end_matches('.').to_owned()
            }
        }
    }
}

/// "500ms", "1s", "2m", "1h" as milliseconds.
pub fn parse_every(text: &str) -> Option<f64> {
    let text = text.trim();
    let (digits, unit) = text.split_at(text.find(|c: char| !c.is_ascii_digit() && c != '.')?);
    let n: f64 = digits.parse().ok()?;
    let ms = match unit {
        "ms" => n,
        "s" => n * 1000.0,
        "m" => n * 60_000.0,
        "h" => n * 3_600_000.0,
        _ => return None,
    };
    (ms > 0.0).then_some(ms)
}

/// A message's time in milliseconds since 1970: a number, or ISO 8601 text
/// ("2026-11-25T06:12:30Z", "2026-11-25T06:12:30.123+08:00").
pub fn parse_time(value: &Value) -> Option<f64> {
    match value {
        Value::Number(n) => n.as_f64().filter(|f| f.is_finite()),
        Value::String(s) => parse_iso(s),
        _ => None,
    }
}

fn parse_iso(s: &str) -> Option<f64> {
    let s = s.trim();
    let b = s.as_bytes();
    let num = |from: usize, len: usize| -> Option<i64> { s.get(from..from + len)?.parse().ok() };
    if b.len() < 19 || b[4] != b'-' || b[7] != b'-' || !matches!(b[10], b'T' | b't' | b' ') || b[13] != b':' || b[16] != b':' {
        return None;
    }
    let (y, mo, d, h, mi, sec) = (num(0, 4)?, num(5, 2)?, num(8, 2)?, num(11, 2)?, num(14, 2)?, num(17, 2)?);
    if !(1..=12).contains(&mo) || !(1..=31).contains(&d) || h > 23 || mi > 59 || sec > 60 {
        return None;
    }
    let mut i = 19;
    let mut millis = 0.0;
    if b.get(i) == Some(&b'.') {
        let start = i + 1;
        i = start;
        while b.get(i).is_some_and(u8::is_ascii_digit) {
            i += 1;
        }
        millis = format!("0.{}", &s[start..i]).parse::<f64>().ok()? * 1000.0;
    }
    let offset_min = match b.get(i) {
        None | Some(b'Z' | b'z') => 0,
        Some(sign @ (b'+' | b'-')) => {
            let oh = num(i + 1, 2)?;
            let om = num(i + 4, 2)?;
            let total = oh * 60 + om;
            if *sign == b'+' { total } else { -total }
        }
        _ => return None,
    };
    // days from the civil date (Howard Hinnant's algorithm)
    let (y2, m2) = if mo <= 2 { (y - 1, mo + 9) } else { (y, mo - 3) };
    let era = y2.div_euclid(400);
    let yoe = y2 - era * 400;
    let doy = (153 * m2 + 2) / 5 + d - 1;
    let doe = yoe * 365 + yoe / 4 - yoe / 100 + doy;
    let days = era * 146_097 + doe - 719_468;
    let seconds = days * 86_400 + h * 3600 + mi * 60 + sec - offset_min * 60;
    Some(seconds as f64 * 1000.0 + millis)
}

/// Facet values in the configured order, then the others as first seen.
pub fn facet_label(config: &Config, value: &str) -> String {
    config
        .facet_values
        .iter()
        .find(|f| f.value.eq_ignore_ascii_case(value))
        .and_then(|f| f.label.clone())
        .unwrap_or_else(|| value.to_owned())
}

/// Checks what serde cannot: formats of `every`, colour names.
pub fn validate(config: &Config) -> Result<(), String> {
    for chart in &config.charts {
        if let Some(every) = &chart.every
            && parse_every(every).is_none()
        {
            return Err(format!("chart every {every:?}: expected e.g. 500ms, 1s, 1m, 1h"));
        }
        if chart.kind == ChartKind::Bar && chart.x.is_none() {
            return Err("a bar chart needs x (the field whose values are the bars)".to_owned());
        }
    }
    for column in &config.columns {
        if column.field.is_none() && column.text.is_empty() {
            return Err(format!("column {:?} needs a field or text", column.title()));
        }
        for c in &column.colors {
            if !matches!(c.color.as_str(), "error" | "warn" | "weak" | "accent") && egui::Color32::from_hex(&c.color).is_err() {
                return Err(format!("colour {:?}: expected error, warn, weak, accent or #rrggbb", c.color));
            }
        }
    }
    for stat in config.summary.iter().chain(&config.stats) {
        let needs_field = !matches!(stat.agg, Agg::Count | Agg::Rate | Agg::ErrorCount | Agg::ErrorRate);
        if needs_field && stat.field.is_none() {
            return Err(format!("stat {:?} ({:?}) needs a field", stat.label, stat.agg));
        }
    }
    if config.max_messages == Some(0) {
        return Err("maxMessages must be at least 1".to_owned());
    }
    if config.window_seconds.is_some_and(|w| !w.is_finite() || w <= 0.0) {
        return Err("windowSeconds must be more than 0".to_owned());
    }
    Ok(())
}

/// Counts per facet value, in the configured order then as first seen.
pub fn facet_counts<'a>(config: &Config, values: impl Iterator<Item = &'a str>) -> Vec<(String, usize)> {
    let mut counts: BTreeMap<String, usize> = BTreeMap::new();
    let mut seen: Vec<String> = Vec::new();
    for v in values {
        let key = v.to_lowercase();
        if !counts.contains_key(&key) {
            seen.push(key.clone());
        }
        *counts.entry(key).or_default() += 1;
    }
    let mut out: Vec<(String, usize)> =
        config.facet_values.iter().map(|f| (f.value.to_lowercase(), counts.get(&f.value.to_lowercase()).copied().unwrap_or(0))).collect();
    for key in seen {
        if !out.iter().any(|(k, _)| *k == key) {
            let n = counts[&key];
            out.push((key, n));
        }
    }
    out
}
