//! `monitor`: a monitoring window for any kind of message. Its user describes the messages (see [`config`]): where
//! the time is, the columns, what an error is, the statistics, the groups, the charts, the details. Then it
//! takes messages (any JSON) and shows them: a list that follows the newest, a summary line, and panes that dock
//! inside it like an IDE's (List, Stats, Groups, Charts, Details: drag a tab to split, stack or move it).
//!
//! Props (all optional; what is left out stays):
//! ```json
//! {
//!   "config": { "time": "createdAt", "columns": [...], "error": {...}, "summary": [...], "stats": [...],
//!               "groupBy": ["url"], "groupValue": "durationMs", "charts": [...], "details": [...] },
//!   "append": [ { ...any message... } ],
//!   "clear": true,
//!   "filter": { "facet": ["http", "bridge"], "search": "todos", "errorsOnly": false, "group": "GET /api" },
//!   "layout": { ...a layout the `layout` event gave... }
//! }
//! ```
//! Events: `select {id}` (a message chosen), `clear` (the user emptied it), `group {key}` (a group chosen, null: all
//! again), `layout {layout}` (the panes were moved: keep it, give it back as props to restore them).

pub mod config;
pub mod stats;

use std::collections::{BTreeSet, VecDeque};
use std::sync::atomic::{AtomicU64, Ordering};

use egui::{Color32, RichText};
use egui_dock::{DockArea, DockState, NodeIndex, TabViewer};
use egui_extras::{Column as TableColumn, TableBuilder};
use egui_plot::{Bar, BarChart, Legend, Line, Plot, PlotPoints};
use serde::{Deserialize, Serialize};
use serde_json::{Value, json};

use self::config::{Agg, Config, Format, Stat, facet_counts, facet_label, format_number, get, number, parse_time, text_of};
use self::stats::{Group, Message, Series};
use crate::json_view::{self, Body};
use crate::{Component, Events};

const DEFAULT_MAX: usize = 2000;

/// A pane of the monitor: a tab that docks anywhere in it.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
pub enum Pane {
    List,
    Stats,
    Groups,
    Charts,
    Details,
}

impl Pane {
    fn title(self) -> &'static str {
        match self {
            Pane::List => "List",
            Pane::Stats => "Stats",
            Pane::Groups => "Groups",
            Pane::Charts => "Charts",
            Pane::Details => "Details",
        }
    }
}

/// What the list shows: the user's choice, or the host's through `filter`.
#[derive(Debug, Clone, Default, PartialEq)]
struct Filter {
    /// Facet values hidden (lowercase).
    hidden: BTreeSet<String>,
    search: String,
    errors_only: bool,
    group: Option<String>,
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase", deny_unknown_fields)]
struct Props {
    config: Option<Config>,
    append: Option<Vec<Value>>,
    #[serde(default)]
    clear: bool,
    filter: Option<FilterProps>,
    layout: Option<Value>,
    /// What the host found for a search (`hostSearch`): the text searched for, and the messages.
    found: Option<FoundProps>,
}

#[derive(Deserialize)]
#[serde(deny_unknown_fields)]
struct FoundProps {
    text: String,
    messages: Vec<Value>,
}

/// The ids of found messages: far from those of the messages kept, so the two never meet.
const FOUND_IDS: u64 = 1 << 62;

/// How long the search box has to be still before the host is asked (a search a keystroke is too many).
const SEARCH_PAUSE: f64 = 0.3;

#[derive(Deserialize)]
#[serde(rename_all = "camelCase", deny_unknown_fields)]
struct FilterProps {
    /// The facet values shown (the others hidden).
    facet: Option<Vec<String>>,
    search: Option<String>,
    errors_only: Option<bool>,
    /// Only this group (null: all).
    #[serde(default, deserialize_with = "some_or_null")]
    group: Option<Option<String>>,
}

fn some_or_null<'de, D: serde::Deserializer<'de>>(d: D) -> Result<Option<Option<String>>, D::Error> {
    Option::<String>::deserialize(d).map(Some)
}

/// Everything but the dock (the dock's tabs draw from it).
struct Core {
    uid: egui::Id,
    config: Config,
    messages: VecDeque<Message>,
    next_id: u64,
    /// Bumped at each change of the messages: what is cached is redone.
    version: u64,
    filter: Filter,
    selected: Option<u64>,
    detail_tab: usize,
    /// The chosen message's JSON body, read once: (message, tab, body).
    body: Option<(u64, usize, Option<Body>)>,
    visible: Vec<u64>,
    visible_key: Option<(u64, Filter)>,
    /// Statistics, groups and charts, redone when the messages change.
    cache: Option<(u64, Cached)>,
    at_end: bool,
    appended: bool,
    /// hostSearch: what the host found, for which text.
    found: Option<(String, Vec<Message>)>,
    /// hostSearch: the text the host was last asked for, and when the search box last changed.
    asked: String,
    search_changed_at: f64,
    list_height: f32,
}

#[derive(Default)]
struct Cached {
    summary: Vec<(Option<f64>, Option<u64>)>,
    stats: Vec<(Option<f64>, Option<u64>)>,
    groups: Vec<Group>,
    charts: Vec<Series>,
}

pub struct Monitor {
    core: Core,
    dock: DockState<Pane>,
    /// The layout's shape last reported (the `layout` event is sent when it changes).
    layout_sent: String,
}

impl Default for Monitor {
    fn default() -> Self {
        static NEXT: AtomicU64 = AtomicU64::new(0);
        let config = Config::default();
        let dock = default_dock(&config);
        let layout_sent = layout_shape(&dock);
        Monitor {
            core: Core {
                uid: egui::Id::unique(("egui4j-monitor", NEXT.fetch_add(1, Ordering::Relaxed))),
                config,
                messages: VecDeque::new(),
                next_id: 1,
                version: 0,
                filter: Filter::default(),
                selected: None,
                detail_tab: 0,
                body: None,
                visible: Vec::new(),
                visible_key: None,
                cache: None,
                at_end: true,
                appended: false,
                found: None,
                asked: String::new(),
                search_changed_at: 0.0,
                list_height: 0.0,
            },
            dock,
            layout_sent,
        }
    }
}

/// The panes a config has something for.
fn panes(config: &Config) -> Vec<Pane> {
    let mut side = vec![Pane::Stats];
    if !config.group_by.is_empty() {
        side.push(Pane::Groups);
    }
    if !config.charts.is_empty() {
        side.push(Pane::Charts);
    }
    side
}

/// The list on the left above the details; stats, groups and charts as tabs on the right.
fn default_dock(config: &Config) -> DockState<Pane> {
    let mut dock = DockState::new(vec![Pane::List]);
    let surface = dock.main_surface_mut();
    let [list, _side] = surface.split_right(NodeIndex::root(), 0.7, panes(config));
    surface.split_below(list, 0.6, vec![Pane::Details]);
    dock
}

/// A layout's shape: the tree, the fractions and the tabs, without what changes every frame (sizes, focus, scroll).
fn layout_shape(dock: &DockState<Pane>) -> String {
    fn strip(v: &mut Value) {
        match v {
            Value::Object(map) => {
                for key in ["rect", "viewport", "focused_node", "focused_surface", "scroll"] {
                    map.remove(key);
                }
                map.values_mut().for_each(strip);
            }
            Value::Array(items) => items.iter_mut().for_each(strip),
            _ => {}
        }
    }
    let mut v = serde_json::to_value(dock).unwrap_or(Value::Null);
    strip(&mut v);
    v.to_string()
}

fn now_ms() -> f64 {
    web_time::SystemTime::now().duration_since(web_time::UNIX_EPOCH).map_or(0.0, |d| d.as_secs_f64() * 1000.0)
}

impl Monitor {
    pub fn config(&self) -> &Config {
        &self.core.config
    }

    pub fn messages(&self) -> impl Iterator<Item = &Message> {
        self.core.messages.iter()
    }

    pub fn selected(&self) -> Option<u64> {
        self.core.selected
    }

    /// The ids of the rows shown when the list was last drawn, in order.
    pub fn visible(&self) -> &[u64] {
        &self.core.visible
    }

    /// The panes' layout, as the `layout` event gives it (and the `layout` prop takes it). Sizes on the screen
    /// are left at zero: they are worked out again when drawn (and before the first draw they are infinite,
    /// which JSON cannot hold).
    pub fn layout(&self) -> Value {
        fn zero_rects(v: &mut Value) {
            match v {
                Value::Object(map) => {
                    for (key, value) in map.iter_mut() {
                        if key == "rect" || key == "viewport" {
                            *value = json!({ "min": { "x": 0.0, "y": 0.0 }, "max": { "x": 0.0, "y": 0.0 } });
                        } else {
                            zero_rects(value);
                        }
                    }
                }
                Value::Array(items) => items.iter_mut().for_each(zero_rects),
                _ => {}
            }
        }
        let mut v = serde_json::to_value(&self.dock).unwrap_or(Value::Null);
        zero_rects(&mut v);
        v
    }

    /// The groups, as the Groups pane shows them.
    pub fn groups(&mut self) -> Vec<Group> {
        self.core.cached().groups.clone()
    }

    /// The value of the Stats pane's statistic `label`.
    pub fn stat(&mut self, label: &str) -> Option<f64> {
        let at = self.core.config.stats.iter().position(|s| s.label == label)?;
        self.core.cached().stats[at].0
    }

    /// The value of the summary's statistic `label`.
    pub fn summary_stat(&mut self, label: &str) -> Option<f64> {
        let at = self.core.config.summary.iter().position(|s| s.label == label)?;
        self.core.cached().summary[at].0
    }

    /// A chart's data, as the Charts pane draws it.
    pub fn chart(&mut self, index: usize) -> Option<Series> {
        self.core.cached().charts.get(index).cloned()
    }

    fn apply(&mut self, props: Value) -> Result<(), String> {
        let props: Props = serde_json::from_value(props).map_err(|e| format!("monitor: {e}"))?;
        if let Some(c) = &props.config {
            config::validate(c).map_err(|e| format!("monitor: {e}"))?;
        }
        let layout: Option<DockState<Pane>> = match props.layout {
            Some(l) => Some(serde_json::from_value(l).map_err(|e| format!("monitor: layout: {e}"))?),
            None => None,
        };
        let core = &mut self.core;
        if let Some(c) = props.config {
            let new_panes = panes(&c) != panes(&core.config);
            core.config = c;
            // the rule may have changed: what is an error is decided again
            let rule = core.config.error.clone();
            for m in &mut core.messages {
                m.error = rule.as_ref().is_some_and(|r| r.holds(&m.value, &|| false));
            }
            if new_panes {
                self.dock = default_dock(&core.config);
            }
            core.body = None;
            core.version += 1;
        }
        if let Some(l) = layout {
            self.dock = l;
        }
        self.layout_sent = layout_shape(&self.dock);
        if props.clear {
            core.clear();
        }
        if let Some(f) = props.filter {
            if let Some(shown) = f.facet {
                let shown: BTreeSet<String> = shown.iter().map(|s| s.to_lowercase()).collect();
                core.filter.hidden = core.facet_values().into_iter().filter(|v| !shown.contains(v)).collect();
            }
            if let Some(search) = f.search {
                core.filter.search = search;
                // the host's own search: no typing to wait out
                core.search_changed_at = f64::NEG_INFINITY;
            }
            if let Some(errors_only) = f.errors_only {
                core.filter.errors_only = errors_only;
            }
            if let Some(group) = f.group {
                core.filter.group = group;
            }
        }
        let now = now_ms();
        if let Some(found) = props.found {
            let messages = found
                .messages
                .into_iter()
                .enumerate()
                .map(|(i, value)| {
                    let time = core.config.time.as_deref().and_then(|f| get(&value, f)).and_then(parse_time).unwrap_or(now);
                    let error = core.config.error.as_ref().is_some_and(|r| r.holds(&value, &|| false));
                    Message { id: FOUND_IDS + i as u64, time, value, error }
                })
                .collect();
            core.found = Some((found.text, messages));
            core.version += 1;
        }
        let added = props.append.as_ref().map_or(0, Vec::len);
        for value in props.append.into_iter().flatten() {
            let time = core.config.time.as_deref().and_then(|f| get(&value, f)).and_then(parse_time).unwrap_or(now);
            let error = core.config.error.as_ref().is_some_and(|r| r.holds(&value, &|| false));
            core.messages.push_back(Message { id: core.next_id, time, value, error });
            core.next_id += 1;
        }
        let max = core.config.max_messages.unwrap_or(DEFAULT_MAX);
        while core.messages.len() > max {
            let gone = core.messages.pop_front().map(|m| m.id);
            if gone.is_some() && gone == core.selected {
                core.selected = None;
                core.body = None;
            }
        }
        if added > 0 {
            core.version += 1;
            core.appended = true;
        }
        log::trace!("{added} added, {} kept", core.messages.len());
        Ok(())
    }
}

impl Component for Monitor {
    fn update(&mut self, props: Value) -> Result<(), String> {
        let result = self.apply(props);
        if let Err(e) = &result {
            log::warn!("refused props: {e}");
        }
        result
    }

    fn ui(&mut self, ui: &mut egui::Ui, events: &mut Events) {
        self.core.toolbar(ui, events);
        self.core.ask_host(ui, events);
        self.core.summary_line(ui);
        ui.separator();
        let style = egui_dock::Style::from_egui(ui.style());
        DockArea::new(&mut self.dock)
            .id(self.core.uid.with("dock"))
            .style(style)
            .show_close_buttons(false)
            .show_leaf_close_all_buttons(false)
            .show_leaf_collapse_buttons(false)
            .show_inside(ui, &mut Viewer { core: &mut self.core, events });

        // the panes moved: say so once the pointer lets go, so the host can keep the layout
        if ui.input(|i| i.pointer.any_released() || !i.pointer.any_down()) {
            let now = layout_shape(&self.dock);
            if now != self.layout_sent {
                self.layout_sent = now;
                events.emit("layout", json!({ "layout": self.layout() }));
            }
        }
    }
}

struct Viewer<'a> {
    core: &'a mut Core,
    events: &'a mut Events,
}

impl TabViewer for Viewer<'_> {
    type Tab = Pane;

    fn title(&mut self, tab: &mut Pane) -> egui::WidgetText {
        tab.title().into()
    }

    fn id(&mut self, tab: &mut Pane) -> egui::Id {
        self.core.uid.with(tab.title())
    }

    fn ui(&mut self, ui: &mut egui::Ui, tab: &mut Pane) {
        match tab {
            Pane::List => self.core.list(ui, self.events),
            Pane::Stats => self.core.stats_pane(ui),
            Pane::Groups => self.core.groups_pane(ui, self.events),
            Pane::Charts => self.core.charts_pane(ui),
            Pane::Details => self.core.details_pane(ui),
        }
    }

    fn is_closeable(&self, _tab: &Pane) -> bool {
        false
    }
}

// ---- the core: filtering, caching, drawing each pane ----

fn named_color(name: &str, ui: &egui::Ui) -> Color32 {
    match name {
        "error" => ui.visuals().error_fg_color,
        "warn" => ui.visuals().warn_fg_color,
        "weak" => ui.visuals().weak_text_color(),
        "accent" => ui.visuals().selection.bg_fill,
        hex => Color32::from_hex(hex).unwrap_or(ui.visuals().text_color()),
    }
}

impl Core {
    fn clear(&mut self) {
        self.messages.clear();
        self.selected = None;
        self.body = None;
        self.version += 1;
    }

    fn message(&self, id: u64) -> Option<&Message> {
        self.messages
            .iter()
            .find(|m| m.id == id)
            .or_else(|| self.found.as_ref().and_then(|(_, list)| list.iter().find(|m| m.id == id)))
    }

    /// hostSearch with something in the search box: the list is what the host found for it.
    fn searching_host(&self) -> bool {
        self.config.host_search && !self.filter.search.trim().is_empty()
    }

    /// hostSearch: the host's results for the text in the box, once they have come.
    fn found_now(&self) -> Option<&[Message]> {
        self.found.as_ref().filter(|(text, _)| text == self.filter.search.trim()).map(|(_, list)| list.as_slice())
    }

    fn facet_values(&self) -> Vec<String> {
        let Some(facet) = &self.config.facet else { return Vec::new() };
        facet_counts(&self.config, self.messages.iter().map(|m| text_of(get(&m.value, facet))).collect::<Vec<_>>().iter().map(String::as_str))
            .into_iter()
            .map(|(v, _)| v)
            .collect()
    }

    fn shows(&self, m: &Message) -> bool {
        let f = &self.filter;
        if let Some(facet) = &self.config.facet
            && f.hidden.contains(&text_of(get(&m.value, facet)).to_lowercase())
        {
            return false;
        }
        if f.errors_only && !m.error {
            return false;
        }
        if let Some(group) = &f.group
            && (&stats::group_key(&self.config, &m.value) != group || self.config.group_where.as_ref().is_some_and(|r| !m.holds(r)))
        {
            return false;
        }
        // the host searched (all it kept): its results match already
        self.searching_host() || f.search.is_empty() || {
            let needle = f.search.to_lowercase();
            self.summary_of(m).to_lowercase().contains(&needle) || m.value.to_string().to_lowercase().contains(&needle)
        }
    }

    fn refresh_visible(&mut self) {
        let key = (self.version, self.filter.clone());
        if self.visible_key.as_ref() != Some(&key) {
            self.visible = if self.searching_host() {
                self.found_now().unwrap_or(&[]).iter().filter(|m| self.shows(m)).map(|m| m.id).collect()
            } else {
                self.messages.iter().filter(|m| self.shows(m)).map(|m| m.id).collect()
            };
            self.visible_key = Some(key);
        }
    }

    fn cached(&mut self) -> &Cached {
        if self.cache.as_ref().is_none_or(|(v, _)| *v != self.version) {
            let messages: Vec<Message> = self.messages.iter().cloned().collect();
            let window = self.config.window_seconds;
            let compute = |list: &[Stat]| list.iter().map(|s| stats::compute(s, &messages, window)).collect();
            let cached = Cached {
                summary: compute(&self.config.summary),
                stats: compute(&self.config.stats),
                groups: stats::groups(&self.config, &messages),
                charts: self.config.charts.iter().map(|c| stats::chart(c, &self.config, &messages)).collect(),
            };
            self.cache = Some((self.version, cached));
        }
        &self.cache.as_ref().expect("just made").1
    }

    /// The column that names a message: the one marked summary, or the first made of text.
    fn summary_column(&self) -> Option<&config::Column> {
        let columns = &self.config.columns;
        columns.iter().find(|c| c.summary).or_else(|| columns.iter().find(|c| !c.text.is_empty())).or(columns.first())
    }

    fn summary_of(&self, m: &Message) -> String {
        match self.summary_column() {
            Some(c) => c.raw_text(&m.value, &|| m.error),
            None => m.value.to_string(),
        }
    }

    fn first_time(&self) -> f64 {
        self.messages.front().map_or(0.0, |m| m.time)
    }

    fn cell(&self, column: &config::Column, m: &Message, ui: &egui::Ui) -> RichText {
        let raw = column.raw_text(&m.value, &|| m.error);
        let num = column.field.as_deref().and_then(|f| match column.format {
            Format::Since => get(&m.value, f).and_then(parse_time),
            _ => number(&m.value, f),
        });
        let text = match (column.format, num) {
            (Format::Since, Some(t)) => format_number(t - self.first_time(), Format::Since),
            (Format::Text, _) | (_, None) => raw,
            (format, Some(n)) => format_number(n, format),
        };
        let mut rich = RichText::new(text);
        if matches!(column.format, Format::Since | Format::Duration | Format::Number | Format::Bytes) {
            rich = rich.monospace();
        }
        let chosen = column.colors.iter().find(|c| m.holds(&c.when)).map(|c| named_color(&c.color, ui));
        if let Some(color) = chosen {
            rich = rich.color(color);
        } else if let (Some(limit), Some(n)) = (column.warn_above, num)
            && n >= limit
        {
            rich = rich.color(ui.visuals().warn_fg_color);
        }
        rich
    }

    fn select(&mut self, id: u64, events: &mut Events) {
        if self.selected != Some(id) {
            self.selected = Some(id);
            self.body = None;
        }
        events.emit("select", json!({ "id": id }));
    }

    fn stat_text(stat: &Stat, value: Option<f64>) -> Option<String> {
        let value = value?;
        let shown = match stat.agg {
            Agg::ErrorRate => format_number(value, Format::Percent),
            Agg::Count | Agg::ErrorCount => format_number(value, Format::Number),
            Agg::Rate => format!("{}/s", format_number(value, Format::Number)),
            _ => format_number(value, stat.format),
        };
        Some(stat.text.as_deref().unwrap_or("{label} {value}").replace("{label}", &stat.label).replace("{value}", &shown))
    }

    fn stat_color(stat: &Stat, value: f64, ui: &egui::Ui) -> Option<Color32> {
        if stat.error_when_nonzero && value > 0.0 {
            Some(ui.visuals().error_fg_color)
        } else if stat.warn_above.is_some_and(|w| value >= w) {
            Some(ui.visuals().warn_fg_color)
        } else {
            None
        }
    }

    /// hostSearch: asks the host to search, once the search box has been still a moment (and again when it changes).
    fn ask_host(&mut self, ui: &egui::Ui, events: &mut Events) {
        if !self.config.host_search {
            return;
        }
        let text = self.filter.search.trim().to_owned();
        if text == self.asked {
            return;
        }
        let still = ui.input(|i| i.time) - self.search_changed_at;
        if still < SEARCH_PAUSE {
            ui.ctx().request_repaint_after(std::time::Duration::from_secs_f64(SEARCH_PAUSE - still + 0.01));
            return;
        }
        self.asked = text.clone();
        if text.is_empty() {
            self.found = None;
            self.version += 1;
        } else {
            events.emit("search", json!({ "text": text }));
        }
    }

    // ---- above the panes ----

    fn toolbar(&mut self, ui: &mut egui::Ui, events: &mut Events) {
        ui.horizontal_wrapped(|ui| {
            if !self.config.title.is_empty() {
                ui.strong(&self.config.title);
                ui.separator();
            }
            if let Some(facet) = self.config.facet.clone() {
                let values: Vec<String> = self.messages.iter().map(|m| text_of(get(&m.value, &facet))).collect();
                for (value, count) in facet_counts(&self.config, values.iter().map(String::as_str)) {
                    let mut shown = !self.filter.hidden.contains(&value);
                    if ui.toggle_value(&mut shown, format!("{} {count}", facet_label(&self.config, &value))).changed() {
                        if shown {
                            self.filter.hidden.remove(&value);
                        } else {
                            self.filter.hidden.insert(value);
                        }
                    }
                }
                ui.separator();
            }
            if self.config.error.is_some() {
                ui.toggle_value(&mut self.filter.errors_only, "Errors");
            }
            let hint = if self.config.host_search { "Search everything" } else { "Search (bodies too)" };
            if ui.add(egui::TextEdit::singleline(&mut self.filter.search).hint_text(hint).desired_width(180.0)).changed() {
                self.search_changed_at = ui.input(|i| i.time);
            }
            if let Some(group) = self.filter.group.clone() {
                ui.separator();
                ui.label(RichText::new(format!("Group: {group}")).strong());
                if ui.small_button("All groups").clicked() {
                    self.filter.group = None;
                    events.emit("group", json!({ "key": Value::Null }));
                }
            }
            ui.separator();
            if ui.button("Clear").clicked() {
                self.clear();
                events.emit("clear", Value::Null);
            }
        });
    }

    fn summary_line(&mut self, ui: &mut egui::Ui) {
        if self.config.summary.is_empty() {
            return;
        }
        let values = self.cached().summary.clone();
        let mut chosen = None;
        ui.horizontal_wrapped(|ui| {
            for (stat, (value, linked)) in self.config.summary.iter().zip(&values) {
                let Some(text) = Self::stat_text(stat, *value) else { continue };
                if stat.link
                    && let Some(id) = linked
                    && let Some(m) = self.message(*id)
                {
                    if ui.link(format!("{text} · {}", self.summary_of(m))).clicked() {
                        chosen = Some(*id);
                    }
                    continue;
                }
                let mut rich = RichText::new(text);
                if let Some(color) = value.and_then(|v| Self::stat_color(stat, v, ui)) {
                    rich = rich.color(color);
                }
                ui.label(rich);
            }
        });
        if let Some(id) = chosen {
            self.selected = Some(id);
            self.body = None;
        }
    }

    // ---- List ----

    fn list(&mut self, ui: &mut egui::Ui, events: &mut Events) {
        self.refresh_visible();
        if self.visible.is_empty() {
            ui.centered_and_justified(|ui| {
                ui.weak(if self.searching_host() && self.found_now().is_none() {
                    "Searching…"
                } else if self.messages.is_empty() && !self.searching_host() {
                    "Nothing yet"
                } else {
                    "Nothing matches"
                });
            });
            return;
        }
        // always follows the newest: at the end, it is put back at the end when messages come in or its height
        // changes (egui's own stick-to-bottom lets go after a wheel or bar movement a fraction of a pixel short).
        // Scrolled up to read, it stays put until scrolled back down.
        let height = ui.available_height();
        let resized = (height - std::mem::replace(&mut self.list_height, height)).abs() > 0.5;
        let pin = self.at_end && (std::mem::take(&mut self.appended) || resized);
        if pin {
            ui.ctx().request_repaint();
        }
        let row_height = ui.text_style_height(&egui::TextStyle::Body) + 4.0;
        let wide = self.summary_column().map(|c| c as *const config::Column);
        let mut table = TableBuilder::new(ui)
            .id_salt(self.uid.with("rows"))
            .striped(false)
            .sense(egui::Sense::click())
            .cell_layout(egui::Layout::left_to_right(egui::Align::Center))
            .auto_shrink([false, false])
            .min_scrolled_height(0.0)
            .animate_scrolling(false);
        for column in &self.config.columns {
            table = table.column(match column.width {
                Some(w) => TableColumn::exact(w),
                None if Some(column as *const _) == wide => TableColumn::remainder().clip(true),
                None => TableColumn::exact(80.0),
            });
        }
        if self.config.columns.is_empty() {
            table = table.column(TableColumn::remainder().clip(true));
        }
        if pin {
            table = table.scroll_to_row(self.visible.len() - 1, Some(egui::Align::BOTTOM));
        }
        let mut chosen = None;
        let this = &*self;
        let output = table
            .header(row_height, |mut header| {
                for column in &this.config.columns {
                    header.col(|ui| {
                        ui.strong(column.title());
                    });
                }
                if this.config.columns.is_empty() {
                    header.col(|ui| {
                        ui.strong("Message");
                    });
                }
            })
            .body(|body| {
                body.rows(row_height, this.visible.len(), |mut row| {
                    let id = this.visible[row.index()];
                    let Some(m) = this.message(id) else { return };
                    row.set_selected(this.selected == Some(id));
                    let plain = |ui: &mut egui::Ui, text: RichText| {
                        ui.add(egui::Label::new(text).selectable(false).truncate());
                    };
                    for column in &this.config.columns {
                        row.col(|ui| {
                            let text = this.cell(column, m, ui);
                            plain(ui, text);
                        });
                    }
                    if this.config.columns.is_empty() {
                        row.col(|ui| plain(ui, RichText::new(m.value.to_string()).monospace()));
                    }
                    if row.response().clicked() {
                        chosen = Some(id);
                    }
                });
            });
        let end = (output.content_size.y - output.inner_rect.height()).max(0.0);
        self.at_end = pin || output.state.clamped_offset().y >= end - 8.0;
        if let Some(id) = chosen {
            self.select(id, events);
        }
    }

    // ---- Stats ----

    fn stats_pane(&mut self, ui: &mut egui::Ui) {
        let list: Vec<Stat> = self.config.summary.iter().chain(&self.config.stats).cloned().collect();
        if list.is_empty() {
            ui.weak("No statistics described");
            return;
        }
        let cached = self.cached();
        let values: Vec<(Option<f64>, Option<u64>)> = cached.summary.iter().chain(&cached.stats).cloned().collect();
        if let Some(w) = self.config.window_seconds {
            ui.weak(format!("Over the last {} s", format_number(w, Format::Number)));
        }
        egui::Grid::new(self.uid.with("stats")).num_columns(2).striped(false).spacing([24.0, 6.0]).show(ui, |ui| {
            for (stat, (value, _)) in list.iter().zip(values) {
                ui.label(&stat.label);
                let shown = value.map_or_else(
                    || "–".to_owned(),
                    |v| match stat.agg {
                        Agg::ErrorRate => format_number(v, Format::Percent),
                        Agg::Count | Agg::ErrorCount => format_number(v, Format::Number),
                        Agg::Rate => format!("{}/s", format_number(v, Format::Number)),
                        _ => format_number(v, stat.format),
                    },
                );
                let mut rich = RichText::new(shown).monospace().strong();
                if let Some(color) = value.and_then(|v| Self::stat_color(stat, v, ui)) {
                    rich = rich.color(color);
                }
                ui.label(rich);
                ui.end_row();
            }
        });
    }

    // ---- Groups ----

    fn groups_pane(&mut self, ui: &mut egui::Ui, events: &mut Events) {
        let groups = self.cached().groups.clone();
        if groups.is_empty() {
            ui.weak("No groups yet");
            return;
        }
        let format = self.config.group_format;
        let has_value = self.config.group_value.is_some();
        let row_height = ui.text_style_height(&egui::TextStyle::Body) + 4.0;
        let mut chosen: Option<Option<String>> = None;
        let current = self.filter.group.clone();
        let mut table = TableBuilder::new(ui)
            .id_salt(self.uid.with("groups"))
            .striped(false)
            .sense(egui::Sense::click())
            .cell_layout(egui::Layout::left_to_right(egui::Align::Center))
            .auto_shrink([false, false])
            .column(TableColumn::remainder().clip(true).at_least(80.0))
            .column(TableColumn::exact(56.0))
            .column(TableColumn::exact(56.0));
        if has_value {
            table = table.columns(TableColumn::exact(70.0), 3);
        }
        table
            .header(row_height, |mut h| {
                let mut titles = vec!["Group", "Count", "Errors"];
                if has_value {
                    titles.extend(["p50", "p95", "Max"]);
                }
                for t in titles {
                    h.col(|ui| {
                        ui.strong(t);
                    });
                }
            })
            .body(|body| {
                body.rows(row_height, groups.len(), |mut row| {
                    let g = &groups[row.index()];
                    row.set_selected(current.as_deref() == Some(g.key.as_str()));
                    let plain = |ui: &mut egui::Ui, text: RichText| {
                        ui.add(egui::Label::new(text).selectable(false).truncate());
                    };
                    row.col(|ui| plain(ui, RichText::new(if g.key.trim().is_empty() { "(none)" } else { &g.key })));
                    row.col(|ui| plain(ui, RichText::new(g.count.to_string()).monospace()));
                    row.col(|ui| {
                        let text = RichText::new(format_number(g.error_rate(), Format::Percent)).monospace();
                        plain(ui, if g.errors > 0 { text.color(ui.visuals().error_fg_color) } else { text });
                    });
                    if has_value {
                        for v in [g.p50, g.p95, g.max] {
                            row.col(|ui| plain(ui, RichText::new(v.map_or("–".to_owned(), |v| format_number(v, format))).monospace()));
                        }
                    }
                    if row.response().clicked() {
                        // the same group again: all groups
                        chosen = Some(if current.as_deref() == Some(g.key.as_str()) { None } else { Some(g.key.clone()) });
                    }
                });
            });
        if let Some(group) = chosen {
            events.emit("group", json!({ "key": group }));
            self.filter.group = group;
        }
    }

    // ---- Charts ----

    fn charts_pane(&mut self, ui: &mut egui::Ui) {
        if self.config.charts.is_empty() {
            ui.weak("No charts described");
            return;
        }
        let series = self.cached().charts.clone();
        let charts = self.config.charts.clone();
        let uid = self.uid;
        egui::ScrollArea::vertical().auto_shrink([false, false]).show(ui, |ui| {
            for (i, (chart, data)) in charts.iter().zip(series).enumerate() {
                let title = chart.title.clone().unwrap_or_else(|| match (&chart.y, &chart.x) {
                    (Some(y), _) => format!("{:?} of {y}", chart.agg).to_lowercase(),
                    (None, Some(x)) => format!("{:?} by {x}", chart.agg).to_lowercase(),
                    _ => format!("{:?}", chart.agg).to_lowercase(),
                });
                ui.strong(title);
                let plot = Plot::new(uid.with(("chart", i))).height(160.0).allow_scroll(false).legend(Legend::default());
                match data {
                    Series::Lines(lines) => {
                        plot.x_axis_label("seconds").show(ui, |p| {
                            for (name, points) in lines {
                                let name = if name.is_empty() { chart.y.clone().unwrap_or_else(|| "count".into()) } else { name };
                                p.line(Line::new(name, PlotPoints::from(points)));
                            }
                        });
                    }
                    Series::Bars(bars) => {
                        let labels: Vec<String> = bars.iter().map(|(k, _)| if k.is_empty() { "(none)".into() } else { k.clone() }).collect();
                        let names = labels.clone();
                        plot.x_axis_formatter(move |mark, _| {
                            let i = mark.value.round();
                            if (mark.value - i).abs() < 1e-6 && i >= 0.0 { names.get(i as usize).cloned().unwrap_or_default() } else { String::new() }
                        })
                        .show(ui, |p| {
                            let bars: Vec<Bar> =
                                bars.iter().enumerate().map(|(i, (_, v))| Bar::new(i as f64, *v).name(&labels[i]).width(0.6)).collect();
                            p.bar_chart(BarChart::new(chart.x.clone().unwrap_or_default(), bars));
                        });
                    }
                }
                ui.add_space(8.0);
            }
        });
    }

    // ---- Details ----

    fn details_pane(&mut self, ui: &mut egui::Ui) {
        let Some(id) = self.selected else {
            ui.centered_and_justified(|ui| {
                ui.weak("Choose a row to see it here");
            });
            return;
        };
        let Some(m) = self.message(id).cloned() else {
            self.selected = None;
            return;
        };
        ui.horizontal(|ui| {
            ui.strong(self.summary_of(&m));
            ui.with_layout(egui::Layout::right_to_left(egui::Align::Center), |ui| {
                if ui.small_button("Close").clicked() {
                    self.selected = None;
                }
            });
        });
        if let Some(note) = &self.config.details_note {
            let text = text_of(get(&m.value, note));
            if !text.is_empty() {
                ui.colored_label(ui.visuals().error_fg_color, text);
            }
        }
        let tabs: Vec<config::Detail> = self.config.details.iter().filter(|d| d.when.as_ref().is_none_or(|r| m.holds(r))).cloned().collect();
        if tabs.is_empty() {
            // nothing described: the whole message
            if self.body.as_ref().is_none_or(|(at, ..)| *at != id) {
                self.body = Some((id, 0, Some(Body::Json(m.value.clone()))));
            }
            if let Some((_, _, Some(body))) = &self.body {
                egui::ScrollArea::both().auto_shrink([false, false]).show(ui, |ui| json_view::show(ui, self.uid.with(id), body));
            }
            return;
        }
        if self.detail_tab >= tabs.len() {
            self.detail_tab = 0;
        }
        if tabs.len() > 1 {
            ui.horizontal(|ui| {
                for (i, t) in tabs.iter().enumerate() {
                    if ui.selectable_label(self.detail_tab == i, &t.tab).clicked() {
                        self.detail_tab = i;
                    }
                }
            });
            ui.separator();
        }
        let tab = &tabs[self.detail_tab];
        let empty = tab.empty.clone().unwrap_or_else(|| "(none)".to_owned());
        if let Some(field) = &tab.json
            && self.body.as_ref().is_none_or(|(at, t, _)| *at != id || *t != self.detail_tab)
        {
            self.body = Some((id, self.detail_tab, get(&m.value, field).filter(|v| !v.is_null()).map(Body::from_value)));
        }
        let uid = self.uid;
        let detail_tab = self.detail_tab;
        egui::ScrollArea::both().auto_shrink([false, false]).show(ui, |ui| {
            if tab.json.is_some() {
                match self.body.as_ref().and_then(|(_, _, b)| b.as_ref()) {
                    None => {
                        ui.weak(&empty);
                    }
                    Some(body) => {
                        if ui.small_button("Copy").clicked() {
                            ui.ctx().copy_text(body.to_text());
                        }
                        json_view::show(ui, uid.with((id, detail_tab)), body);
                    }
                }
            }
            if let Some(text) = &tab.text {
                ui.add(egui::Label::new(RichText::new(config::fill(text, &m.value)).monospace()).wrap());
            }
            for table in &tab.tables {
                ui.strong(&table.title);
                match get(&m.value, &table.field) {
                    Some(Value::Object(map)) if !map.is_empty() => {
                        egui::Grid::new(uid.with((id, &table.title))).num_columns(2).striped(false).show(ui, |ui| {
                            for (k, v) in map {
                                ui.monospace(k);
                                ui.monospace(text_of(Some(v)));
                                ui.end_row();
                            }
                        });
                    }
                    _ => {
                        ui.weak(&empty);
                    }
                }
                ui.add_space(6.0);
            }
        });
    }
}
