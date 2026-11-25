//! The numbers: statistics over messages, groups, and the charts' series. Pure functions of the messages.

use std::collections::BTreeMap;

use serde_json::Value;

use super::config::{Agg, Chart, ChartKind, Config, Rule, Stat, get, number, parse_every, text_of};

/// A message as kept: its id (given in order), its time, itself, and whether it is an error.
#[derive(Debug, Clone, PartialEq)]
pub struct Message {
    pub id: u64,
    pub time: f64,
    pub value: Value,
    pub error: bool,
}

impl Message {
    pub fn holds(&self, rule: &Rule) -> bool {
        rule.holds(&self.value, &|| self.error)
    }
}

/// Nearest rank: the smallest value that a share `q` of the values are at most. `sorted` must be sorted.
pub fn percentile(sorted: &[f64], q: f64) -> Option<f64> {
    (!sorted.is_empty()).then(|| sorted[((sorted.len() as f64 * q).ceil() as usize).clamp(1, sorted.len()) - 1])
}

/// The messages a statistic is over: those its rule holds for, within the window (seconds before the newest).
pub fn in_scope<'a>(messages: &'a [Message], filter: Option<&Rule>, window_seconds: Option<f64>) -> Vec<&'a Message> {
    let newest = messages.iter().map(|m| m.time).fold(f64::NEG_INFINITY, f64::max);
    messages
        .iter()
        .filter(|m| window_seconds.is_none_or(|w| m.time >= newest - w * 1000.0))
        .filter(|m| filter.is_none_or(|r| m.holds(r)))
        .collect()
}

/// A statistic's value, and for min/max the message it came from.
pub fn compute(stat: &Stat, messages: &[Message], window_seconds: Option<f64>) -> (Option<f64>, Option<u64>) {
    let scope = in_scope(messages, stat.filter.as_ref(), window_seconds);
    aggregate(stat.agg, stat.field.as_deref(), &scope)
}

pub fn aggregate(agg: Agg, field: Option<&str>, scope: &[&Message]) -> (Option<f64>, Option<u64>) {
    let n = scope.len() as f64;
    let errors = scope.iter().filter(|m| m.error).count() as f64;
    let with_values = || -> Vec<(f64, u64)> {
        field.map_or_else(Vec::new, |f| scope.iter().filter_map(|m| number(&m.value, f).map(|v| (v, m.id))).collect())
    };
    let sorted = || {
        let mut v: Vec<f64> = with_values().into_iter().map(|(v, _)| v).collect();
        v.sort_by(f64::total_cmp);
        v
    };
    match agg {
        Agg::Count => (Some(n), None),
        Agg::ErrorCount => (Some(errors), None),
        Agg::ErrorRate => ((n > 0.0).then(|| errors / n), None),
        Agg::Rate => {
            let (first, last) = scope.iter().fold((f64::INFINITY, f64::NEG_INFINITY), |(a, b), m| (a.min(m.time), b.max(m.time)));
            // over at least a second, so a burst of a few messages is not thousands a second
            let span = ((last - first) / 1000.0).max(1.0);
            ((n > 0.0).then(|| n / span), None)
        }
        Agg::Min => with_values().into_iter().min_by(|a, b| a.0.total_cmp(&b.0)).map_or((None, None), |(v, id)| (Some(v), Some(id))),
        Agg::Max => with_values().into_iter().max_by(|a, b| a.0.total_cmp(&b.0)).map_or((None, None), |(v, id)| (Some(v), Some(id))),
        Agg::Sum => {
            let v = with_values();
            ((!v.is_empty()).then(|| v.iter().map(|x| x.0).sum()), None)
        }
        Agg::Avg => {
            let v = with_values();
            ((!v.is_empty()).then(|| v.iter().map(|x| x.0).sum::<f64>() / v.len() as f64), None)
        }
        Agg::P50 => (percentile(&sorted(), 0.50), None),
        Agg::P90 => (percentile(&sorted(), 0.90), None),
        Agg::P95 => (percentile(&sorted(), 0.95), None),
        Agg::P99 => (percentile(&sorted(), 0.99), None),
    }
}

/// A message's group: its group-by fields' values, the empty ones left out, with spaces between.
pub fn group_key(config: &Config, message: &Value) -> String {
    config.group_by.iter().map(|f| text_of(get(message, f))).filter(|t| !t.is_empty()).collect::<Vec<_>>().join(" ")
}

/// One group: the messages with the same values of the group-by fields.
#[derive(Debug, Clone, PartialEq)]
pub struct Group {
    pub key: String,
    pub count: usize,
    pub errors: usize,
    pub p50: Option<f64>,
    pub p95: Option<f64>,
    pub max: Option<f64>,
    pub last: f64,
}

impl Group {
    pub fn error_rate(&self) -> f64 {
        if self.count == 0 { 0.0 } else { self.errors as f64 / self.count as f64 }
    }
}

/// The groups, most messages first.
pub fn groups(config: &Config, messages: &[Message]) -> Vec<Group> {
    if config.group_by.is_empty() {
        return Vec::new();
    }
    let scope = in_scope(messages, config.group_where.as_ref(), config.window_seconds);
    let mut by_key: BTreeMap<String, Vec<&Message>> = BTreeMap::new();
    for m in scope {
        let key = group_key(config, &m.value);
        by_key.entry(key).or_default().push(m);
    }
    let mut out: Vec<Group> = by_key
        .into_iter()
        .map(|(key, list)| {
            let mut values: Vec<f64> =
                config.group_value.as_deref().map_or_else(Vec::new, |f| list.iter().filter_map(|m| number(&m.value, f)).collect());
            values.sort_by(f64::total_cmp);
            Group {
                count: list.len(),
                errors: list.iter().filter(|m| m.error).count(),
                p50: percentile(&values, 0.5),
                p95: percentile(&values, 0.95),
                max: values.last().copied(),
                last: list.iter().map(|m| m.time).fold(f64::NEG_INFINITY, f64::max),
                key,
            }
        })
        .collect();
    out.sort_by(|a, b| b.count.cmp(&a.count).then_with(|| a.key.cmp(&b.key)));
    out
}

/// A chart's data: lines (name, points of seconds-since-first and value) or bars (name, value).
#[derive(Debug, Clone, PartialEq)]
pub enum Series {
    Lines(Vec<(String, Vec<[f64; 2]>)>),
    Bars(Vec<(String, f64)>),
}

pub fn chart(chart: &Chart, config: &Config, messages: &[Message]) -> Series {
    let scope = in_scope(messages, chart.filter.as_ref(), config.window_seconds);
    match chart.kind {
        ChartKind::Bar => {
            let x = chart.x.as_deref().unwrap_or_default();
            let mut by: BTreeMap<String, Vec<&Message>> = BTreeMap::new();
            for m in &scope {
                by.entry(text_of(get(&m.value, x))).or_default().push(m);
            }
            Series::Bars(by.into_iter().filter_map(|(k, list)| aggregate(chart.agg, chart.y.as_deref(), &list).0.map(|v| (k, v))).collect())
        }
        ChartKind::Line => {
            let every = chart.every.as_deref().and_then(parse_every).unwrap_or(1000.0);
            let first = messages.iter().map(|m| m.time).fold(f64::INFINITY, f64::min);
            let mut lines: BTreeMap<String, BTreeMap<i64, Vec<&Message>>> = BTreeMap::new();
            for m in &scope {
                let name = chart.series_by.as_deref().map_or_else(String::new, |f| text_of(get(&m.value, f)));
                let bucket = ((m.time - first) / every).floor() as i64;
                lines.entry(name).or_default().entry(bucket).or_default().push(m);
            }
            Series::Lines(
                lines
                    .into_iter()
                    .map(|(name, buckets)| {
                        let points = buckets
                            .into_iter()
                            .filter_map(|(b, list)| {
                                aggregate(chart.agg, chart.y.as_deref(), &list).0.map(|v| [b as f64 * every / 1000.0, v])
                            })
                            .collect();
                        (name, points)
                    })
                    .collect(),
            )
        }
    }
}
