//! `dev-logger`: what an app does while it is developed, in one list: log lines, every API call (the page's
//! fetch/XHR, the calls between the page and Java, Java's own HTTP) with bodies, headers, status and time, and
//! performance samples. It is a [`Monitor`] with a config of its own ([`DevLogger::config`]): the entries' format
//! below is checked as they come, then the monitor shows them, counts them and times them.
//!
//! Props (all optional):
//! ```json
//! {
//!   "append": [
//!     { "kind": "log",    "time": 1732500000123, "level": "warn", "target": "app::sync", "message": "retrying" },
//!     { "kind": "http",   "time": 1732500000200, "source": "page", "method": "GET", "url": "/api/todos",
//!       "status": 200, "durationMs": 41.5,
//!       "request":  { "headers": { "accept": "application/json" } },
//!       "response": { "headers": { "content-type": "application/json" }, "body": "[{\"id\":1}]" } },
//!     { "kind": "bridge", "time": 1732500000300, "direction": "toJava", "name": "TodoController.add",
//!       "durationMs": 3.2, "request": { "body": { "title": "milk" } }, "response": { "body": { "id": 7 } } },
//!     { "kind": "perf",   "time": 1732500000400, "name": "long task", "value": 87, "unit": "ms" }
//!   ],
//!   "maxEntries": 2000, "slowMs": 500, "clear": true,
//!   "filter": { "kinds": ["http", "bridge"], "search": "todos", "errorsOnly": false },
//!   "hostSearch": true,
//!   "found": { "text": "todos", "entries": [ ...entries, as above... ] }
//! }
//! ```
//! `time` is milliseconds since 1970 (`Date.now()`, `System.currentTimeMillis()`). A body is JSON, or text (JSON
//! text is read as JSON). `error` (any call) is why it failed. `maxEntries` keeps the latest (2000 at first).
//!
//! `hostSearch`: the host keeps everything (Java: in a file), the logger only the latest `maxEntries`; typing in
//! the search box sends a `search {text}` event, and the host answers with `found` (what matched, from all of it).
//!
//! Events: those of the monitor: `select {id}`, `clear`, `group {key}`, `layout {layout}`.

use std::collections::VecDeque;

use serde::Deserialize;
use serde_json::{Map, Value, json};

use crate::monitor::Monitor;
use crate::{Component, Events};

const DEFAULT_MAX: usize = 2000;
const DEFAULT_SLOW_MS: f64 = 500.0;

#[derive(Debug, Clone, Copy, PartialEq, Eq, Deserialize)]
#[serde(rename_all = "lowercase")]
pub enum Kind {
    Log,
    Http,
    Bridge,
    Perf,
}

/// Which way a bridge call went.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Deserialize)]
#[serde(rename_all = "camelCase")]
pub enum Direction {
    /// The page called Java (a controller method, a UI handler).
    ToJava,
    /// Java called the page.
    ToPage,
}

#[derive(Debug, Clone, Default, PartialEq, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct Payload {
    #[serde(default)]
    pub headers: Map<String, Value>,
    pub body: Option<Value>,
}

/// One entry, as given.
#[derive(Debug, Clone, PartialEq, Deserialize)]
#[serde(tag = "kind", rename_all = "lowercase", rename_all_fields = "camelCase", deny_unknown_fields)]
pub enum Data {
    Log {
        level: String,
        #[serde(default)]
        target: String,
        message: String,
    },
    Http {
        /// "page" (fetch/XHR) or "java" (Java's own HTTP).
        #[serde(default)]
        source: String,
        method: String,
        url: String,
        status: Option<u16>,
        duration_ms: Option<f64>,
        #[serde(default)]
        request: Payload,
        #[serde(default)]
        response: Payload,
        error: Option<String>,
    },
    Bridge {
        direction: Option<Direction>,
        name: String,
        duration_ms: Option<f64>,
        #[serde(default)]
        request: Payload,
        #[serde(default)]
        response: Payload,
        error: Option<String>,
    },
    Perf {
        name: String,
        value: f64,
        #[serde(default)]
        unit: String,
    },
}

#[derive(Debug, Clone, PartialEq)]
pub struct Entry {
    /// Given in order (the monitor's message id): what `select` names.
    pub id: u64,
    pub time: f64,
    pub data: Data,
}

impl Entry {
    pub fn kind(&self) -> Kind {
        match self.data {
            Data::Log { .. } => Kind::Log,
            Data::Http { .. } => Kind::Http,
            Data::Bridge { .. } => Kind::Bridge,
            Data::Perf { .. } => Kind::Perf,
        }
    }

    pub fn duration_ms(&self) -> Option<f64> {
        match &self.data {
            Data::Http { duration_ms, .. } | Data::Bridge { duration_ms, .. } => *duration_ms,
            _ => None,
        }
    }

    /// A failed call (an error, or a status of 400 or more), or a log line at error level.
    pub fn is_error(&self) -> bool {
        match &self.data {
            Data::Log { level, .. } => level.eq_ignore_ascii_case("error"),
            Data::Http { status, error, .. } => error.is_some() || status.is_some_and(|s| s >= 400),
            Data::Bridge { error, .. } => error.is_some(),
            Data::Perf { .. } => false,
        }
    }
}

/// The calls' times: how many, how many failed, the median, the 95th percentile and the slowest.
#[derive(Debug, Clone, Default, PartialEq)]
pub struct Timings {
    pub calls: usize,
    pub errors: usize,
    pub p50: Option<f64>,
    pub p95: Option<f64>,
    pub slowest: Option<(u64, f64)>,
}

pub struct DevLogger {
    monitor: Monitor,
    /// The entries, checked and typed (the monitor keeps them as JSON).
    entries: VecDeque<Entry>,
    next_id: u64,
    max_entries: usize,
    slow_ms: f64,
    host_search: bool,
}

impl Default for DevLogger {
    fn default() -> Self {
        let mut monitor = Monitor::default();
        monitor.update(json!({ "config": DevLogger::config(DEFAULT_MAX, DEFAULT_SLOW_MS, false) })).expect("the dev-logger's config");
        DevLogger { monitor, entries: VecDeque::new(), next_id: 1, max_entries: DEFAULT_MAX, slow_ms: DEFAULT_SLOW_MS, host_search: false }
    }
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase", deny_unknown_fields)]
struct Props {
    append: Option<Vec<RawEntry>>,
    max_entries: Option<usize>,
    slow_ms: Option<f64>,
    #[serde(default)]
    clear: bool,
    filter: Option<FilterProps>,
    host_search: Option<bool>,
    /// Checked here, given to the monitor as JSON.
    #[allow(dead_code)]
    found: Option<FoundProps>,
}

/// Read only to check the entries are in the format; then given to the monitor as JSON.
#[derive(Deserialize)]
#[serde(deny_unknown_fields)]
#[allow(dead_code)]
struct FoundProps {
    text: String,
    entries: Vec<RawEntry>,
}

#[derive(Deserialize)]
struct RawEntry {
    time: f64,
    #[serde(flatten)]
    data: Data,
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase", deny_unknown_fields)]
struct FilterProps {
    kinds: Option<Vec<Kind>>,
    search: Option<String>,
    errors_only: Option<bool>,
}

impl DevLogger {
    /// The monitor config the dev-logger is: a good example of what a monitor can be told.
    pub fn config(max_entries: usize, slow_ms: f64, host_search: bool) -> Value {
        let calls = json!({ "field": "kind", "in": ["http", "bridge"] });
        let kind = |k: &str| json!({ "field": "kind", "eq": k });
        json!({
            "time": "time",
            "facet": "kind",
            "facetValues": [
                { "value": "log", "label": "Log" }, { "value": "http", "label": "HTTP" },
                { "value": "bridge", "label": "Bridge" }, { "value": "perf", "label": "Perf" }
            ],
            "error": { "any": [
                { "field": "status", "gte": 400 },
                { "field": "error", "exists": true },
                { "all": [kind("log"), { "field": "level", "eq": "error" }] }
            ]},
            "columns": [
                { "label": "Time", "field": "time", "format": "since", "width": 72,
                  "colors": [{ "when": {}, "color": "weak" }] },
                { "label": "Kind", "width": 52, "text": [
                    { "when": kind("log"), "text": "Log" }, { "when": kind("http"), "text": "HTTP" },
                    { "when": kind("bridge"), "text": "Bridge" }, { "when": kind("perf"), "text": "Perf" }
                ]},
                { "label": "What", "summary": true, "text": [
                    { "when": { "all": [kind("log"), { "field": "target", "exists": true }] }, "text": "{level|upper} [{target}] {message}" },
                    { "when": kind("log"), "text": "{level|upper} {message}" },
                    { "when": { "all": [kind("http"), { "field": "source", "exists": true }] }, "text": "{method} {url}  ({source})" },
                    { "when": kind("http"), "text": "{method} {url}" },
                    { "when": { "field": "direction", "eq": "toJava" }, "text": "{name}  (page to Java)" },
                    { "when": { "field": "direction", "eq": "toPage" }, "text": "{name}  (Java to page)" },
                    { "when": kind("bridge"), "text": "{name}" },
                    { "when": kind("perf"), "text": "{name} {value}{unit}" }
                  ],
                  "colors": [
                    { "when": { "field": "level", "eq": "error" }, "color": "error" },
                    { "when": { "field": "level", "in": ["warn", "warning"] }, "color": "warn" },
                    { "when": { "field": "level", "in": ["debug", "trace"] }, "color": "weak" },
                    { "when": { "error": true }, "color": "error" }
                  ]},
                { "label": "Status", "width": 48, "text": [
                    { "when": { "field": "status", "exists": true }, "text": "{status}" },
                    { "when": { "field": "error", "exists": true }, "text": "failed" }
                  ], "colors": [{ "when": { "error": true }, "color": "error" }] },
                { "label": "Took", "field": "durationMs", "format": "duration", "width": 72, "warnAbove": slow_ms }
            ],
            "summary": [
                { "label": "calls", "agg": "count", "where": calls, "text": "{value} calls" },
                { "label": "failed", "agg": "errorCount", "where": calls, "text": "{value} failed", "errorWhenNonzero": true },
                { "label": "p50", "agg": "p50", "field": "durationMs", "where": calls, "format": "duration", "warnAbove": slow_ms },
                { "label": "p95", "agg": "p95", "field": "durationMs", "where": calls, "format": "duration", "warnAbove": slow_ms },
                { "label": "slowest", "agg": "max", "field": "durationMs", "where": calls, "format": "duration", "link": true }
            ],
            "stats": [
                { "label": "calls a second", "agg": "rate", "where": calls },
                { "label": "failed share", "agg": "errorRate", "where": calls },
                { "label": "average", "agg": "avg", "field": "durationMs", "where": calls, "format": "duration" },
                { "label": "p99", "agg": "p99", "field": "durationMs", "where": calls, "format": "duration", "warnAbove": slow_ms },
                { "label": "log errors", "agg": "errorCount", "where": kind("log"), "errorWhenNonzero": true },
                { "label": "long tasks", "agg": "count", "where": kind("perf") },
                { "label": "longest task", "agg": "max", "field": "value", "where": kind("perf"), "format": "duration" }
            ],
            "groupBy": ["method", "url", "name"],
            "groupValue": "durationMs",
            "groupWhere": calls,
            "groupFormat": "duration",
            "charts": [
                { "title": "p95 time of calls, each second", "type": "line", "y": "durationMs", "agg": "p95", "every": "1s",
                  "seriesBy": "kind", "where": calls },
                { "title": "calls each second", "type": "line", "agg": "count", "every": "1s", "seriesBy": "kind", "where": calls },
                { "title": "HTTP status", "type": "bar", "x": "status", "agg": "count", "where": kind("http") }
            ],
            "details": [
                { "tab": "Response", "when": calls, "json": "response.body", "empty": "(no body)" },
                { "tab": "Request", "when": calls, "json": "request.body", "empty": "(no body)" },
                { "tab": "Headers", "when": calls, "tables": [
                    { "title": "Request", "field": "request.headers" }, { "title": "Response", "field": "response.headers" }
                ]},
                { "tab": "Message", "when": kind("log"), "text": "{level|upper}  {target}\n\n{message}" },
                { "tab": "Sample", "when": kind("perf"), "text": "{name}: {value} {unit}" }
            ],
            "detailsNote": "error",
            "maxMessages": max_entries,
            "hostSearch": host_search
        })
    }

    pub fn monitor(&mut self) -> &mut Monitor {
        &mut self.monitor
    }

    pub fn entries(&self) -> impl Iterator<Item = &Entry> {
        self.entries.iter()
    }

    pub fn selected(&self) -> Option<u64> {
        self.monitor.selected()
    }

    /// The ids of the rows shown when it was last drawn, in order.
    pub fn visible(&self) -> &[u64] {
        self.monitor.visible()
    }

    /// The calls' times, as the monitor counts them.
    pub fn timings(&mut self) -> Timings {
        let calls: Vec<&Entry> = self.entries.iter().filter(|e| matches!(e.kind(), Kind::Http | Kind::Bridge)).collect();
        let slowest = calls
            .iter()
            .filter_map(|e| e.duration_ms().filter(|d| d.is_finite()).map(|d| (e.id, d)))
            .max_by(|a, b| a.1.total_cmp(&b.1));
        let (calls, errors) = (calls.len(), calls.iter().filter(|e| e.is_error()).count());
        Timings {
            calls,
            errors,
            p50: self.monitor.summary_stat("p50"),
            p95: self.monitor.summary_stat("p95"),
            slowest,
        }
    }

    fn apply(&mut self, raw: Value) -> Result<(), String> {
        // checked whole, typed, before anything changes
        let props: Props = serde_json::from_value(raw.clone()).map_err(|e| format!("dev-logger: {e}"))?;
        if props.max_entries == Some(0) {
            return Err("dev-logger: maxEntries must be at least 1".to_owned());
        }
        if props.slow_ms.is_some_and(|s| !s.is_finite() || s < 0.0) {
            return Err("dev-logger: slowMs must be a number of 0 or more".to_owned());
        }
        let mut forward = Map::new();
        let max = props.max_entries.unwrap_or(self.max_entries);
        let slow = props.slow_ms.unwrap_or(self.slow_ms);
        let host_search = props.host_search.unwrap_or(self.host_search);
        if max != self.max_entries || slow != self.slow_ms || host_search != self.host_search {
            forward.insert("config".into(), DevLogger::config(max, slow, host_search));
        }
        if let Some(found) = raw.get("found") {
            forward.insert("found".into(), json!({ "text": found["text"], "messages": found["entries"] }));
        }
        if props.clear {
            forward.insert("clear".into(), Value::Bool(true));
        }
        if let Some(f) = &props.filter {
            let mut filter = Map::new();
            if let Some(kinds) = &f.kinds {
                let names: Vec<Value> = kinds.iter().map(|k| json!(format!("{k:?}").to_lowercase())).collect();
                filter.insert("facet".into(), Value::Array(names));
            }
            if let Some(search) = &f.search {
                filter.insert("search".into(), json!(search));
            }
            if let Some(errors_only) = f.errors_only {
                filter.insert("errorsOnly".into(), json!(errors_only));
            }
            forward.insert("filter".into(), Value::Object(filter));
        }
        if let Some(append) = raw.get("append") {
            forward.insert("append".into(), append.clone());
        }
        self.monitor.update(Value::Object(forward))?;

        self.max_entries = max;
        self.slow_ms = slow;
        self.host_search = host_search;
        if props.clear {
            self.entries.clear();
        }
        for e in props.append.into_iter().flatten() {
            self.entries.push_back(Entry { id: self.next_id, time: e.time, data: e.data });
            self.next_id += 1;
        }
        while self.entries.len() > self.max_entries {
            self.entries.pop_front();
        }
        Ok(())
    }
}

impl Component for DevLogger {
    fn update(&mut self, props: Value) -> Result<(), String> {
        let result = self.apply(props);
        if let Err(e) = &result {
            log::warn!("refused props: {e}");
        }
        result
    }

    fn ui(&mut self, ui: &mut egui::Ui, events: &mut Events) {
        let had_clear = events.has("clear");
        self.monitor.ui(ui, events);
        // the user emptied the list
        if !had_clear && events.has("clear") {
            self.entries.clear();
        }
    }
}
