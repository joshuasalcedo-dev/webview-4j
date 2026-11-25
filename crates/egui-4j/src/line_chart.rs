//! `line-chart`: lines over x, zoomed and panned with the mouse, fed all at once or streamed point by point.
//!
//! Props (all optional; what is left out stays as it was):
//! ```json
//! {
//!   "series":    [{ "name": "cpu", "points": [[0, 12.5], [1, 14]], "color": "#0a84ff" }],
//!   "append":    [{ "series": "cpu", "points": [[2, 13]] }],
//!   "maxPoints": 600,
//!   "xLabel": "seconds", "yLabel": "%", "legend": true
//! }
//! ```
//! `series` replaces every line; `append` adds points to a line (a new one if there is none by that name), and
//! `maxPoints` then keeps each line's latest points only, so a stream never grows without end. Points that are
//! not finite numbers are left out.
//!
//! Events:
//! - `click`: `{x, y, nearest}`, where the user clicked, in the chart's values; `nearest` is the closest point
//!   within a few pixels (`{series, index, x, y}`) or null.
//! - `view`: `{xMin, xMax, yMin, yMax}` after the user zoomed or panned (not when new data moves the view).
//! - `reset`: the user double-clicked: the chart fits its data again, and follows it.

use std::sync::atomic::{AtomicU64, Ordering};

use egui_plot::{Legend, Line, Plot, PlotBounds, PlotPoint, PlotPoints, PlotTransform};
use serde::Deserialize;
use serde_json::{Value, json};

use crate::{Component, Events, parse_color};

/// How close (in pixels) a click has to be to a point for it to be that point.
const NEAREST_PIXELS: f32 = 12.0;

#[derive(Debug, Clone, PartialEq)]
pub struct Series {
    pub name: String,
    pub points: Vec<PlotPoint>,
    pub color: Option<egui::Color32>,
}

pub struct LineChart {
    id: u64,
    series: Vec<Series>,
    max_points: Option<usize>,
    x_label: String,
    y_label: String,
    legend: bool,
    /// The view the user chose (zoom, pan), as last reported; None while the chart fits its data.
    user_view: Option<[f64; 4]>,
    /// Double-clicked: the chart is on its way back to fitting its data (that is not a view the user chose).
    resetting: bool,
    /// Where the chart was last drawn: maps the chart's values to the screen (for the host's tests, and clicks).
    transform: Option<PlotTransform>,
}

impl Default for LineChart {
    fn default() -> Self {
        static NEXT: AtomicU64 = AtomicU64::new(0);
        LineChart {
            id: NEXT.fetch_add(1, Ordering::Relaxed),
            series: Vec::new(),
            max_points: None,
            x_label: String::new(),
            y_label: String::new(),
            legend: true,
            user_view: None,
            resetting: false,
            transform: None,
        }
    }
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase", deny_unknown_fields)]
struct Props {
    series: Option<Vec<SeriesProps>>,
    append: Option<Vec<AppendProps>>,
    max_points: Option<usize>,
    x_label: Option<String>,
    y_label: Option<String>,
    legend: Option<bool>,
}

#[derive(Deserialize)]
#[serde(deny_unknown_fields)]
struct SeriesProps {
    name: String,
    #[serde(default)]
    points: Vec<[f64; 2]>,
    color: Option<String>,
}

#[derive(Deserialize)]
#[serde(deny_unknown_fields)]
struct AppendProps {
    series: String,
    points: Vec<[f64; 2]>,
}

fn finite(points: &[[f64; 2]]) -> impl Iterator<Item = PlotPoint> + '_ {
    points.iter().filter(|[x, y]| x.is_finite() && y.is_finite()).map(|&[x, y]| PlotPoint::new(x, y))
}

impl LineChart {
    pub fn series(&self) -> &[Series] {
        &self.series
    }

    /// Where the value (x, y) was on the screen when the chart was last drawn (None before the first frame).
    pub fn screen_position(&self, x: f64, y: f64) -> Option<egui::Pos2> {
        self.transform.as_ref().map(|t| t.position_from_point(&PlotPoint::new(x, y)))
    }

    /// The bounds the chart showed when last drawn: `[xMin, xMax, yMin, yMax]`.
    pub fn view(&self) -> Option<[f64; 4]> {
        self.transform.as_ref().map(|t| bounds_array(t.bounds()))
    }

    fn trim(&mut self) {
        if let Some(max) = self.max_points {
            for s in &mut self.series {
                if s.points.len() > max {
                    s.points.drain(..s.points.len() - max);
                }
            }
        }
    }

    /// The point closest to `pos` on the screen, if one is within [`NEAREST_PIXELS`].
    fn nearest(&self, transform: &PlotTransform, pos: egui::Pos2) -> Option<Value> {
        let mut best: Option<(f32, usize, usize)> = None;
        for (si, s) in self.series.iter().enumerate() {
            for (pi, p) in s.points.iter().enumerate() {
                let d = transform.position_from_point(p).distance(pos);
                if d <= NEAREST_PIXELS && best.is_none_or(|(bd, ..)| d < bd) {
                    best = Some((d, si, pi));
                }
            }
        }
        best.map(|(_, si, pi)| {
            let p = self.series[si].points[pi];
            json!({ "series": self.series[si].name, "index": pi, "x": p.x, "y": p.y })
        })
    }
}

fn bounds_array(b: &PlotBounds) -> [f64; 4] {
    [b.min()[0], b.max()[0], b.min()[1], b.max()[1]]
}

impl Component for LineChart {
    fn update(&mut self, props: Value) -> Result<(), String> {
        let result = self.apply(props);
        if let Err(e) = &result {
            log::warn!("refused props: {e}");
        }
        result
    }

    fn ui(&mut self, ui: &mut egui::Ui, events: &mut Events) {
        self.draw(ui, events);
    }
}

impl LineChart {
    fn apply(&mut self, props: Value) -> Result<(), String> {
        let props: Props = serde_json::from_value(props).map_err(|e| format!("line-chart: {e}"))?;
        if props.max_points == Some(0) {
            return Err("line-chart: maxPoints must be at least 1".to_owned());
        }
        // everything is checked before anything changes
        let replaced = match props.series {
            Some(list) => Some(
                list.into_iter()
                    .map(|s| {
                        let color = s.color.as_deref().map(parse_color).transpose()?;
                        Ok(Series { name: s.name, points: finite(&s.points).collect(), color })
                    })
                    .collect::<Result<Vec<_>, String>>()
                    .map_err(|e| format!("line-chart: {e}"))?,
            ),
            None => None,
        };

        let replacing = replaced.is_some();
        if let Some(series) = replaced {
            self.series = series;
        }
        for append in props.append.into_iter().flatten() {
            let index = match self.series.iter().position(|s| s.name == append.series) {
                Some(i) => i,
                None => {
                    self.series.push(Series { name: append.series, points: Vec::new(), color: None });
                    self.series.len() - 1
                }
            };
            self.series[index].points.extend(finite(&append.points));
        }
        if props.max_points.is_some() {
            self.max_points = props.max_points;
        }
        self.trim();
        if let Some(label) = props.x_label {
            self.x_label = label;
        }
        if let Some(label) = props.y_label {
            self.y_label = label;
        }
        if let Some(legend) = props.legend {
            self.legend = legend;
        }
        // a stream appends many times a second: its lines are trace, a new set of lines is debug
        let level = if replacing { log::Level::Debug } else { log::Level::Trace };
        log::log!(level, "{} lines, {} points", self.series.len(), self.series.iter().map(|s| s.points.len()).sum::<usize>());
        Ok(())
    }

    fn draw(&mut self, ui: &mut egui::Ui, events: &mut Events) {
        let mut plot = Plot::new(("egui4j-line-chart", self.id))
            .x_axis_label(self.x_label.clone())
            .y_axis_label(self.y_label.clone());
        if self.legend {
            plot = plot.legend(Legend::default());
        }

        let series = &self.series;
        let response = plot.show(ui, |plot_ui| {
            for s in series {
                let mut line = Line::new(s.name.clone(), PlotPoints::Borrowed(&s.points));
                if let Some(color) = s.color {
                    line = line.color(color);
                }
                plot_ui.line(line);
            }
            plot_ui.auto_bounds().all()
        });
        let fits_data = response.inner;
        let transform = response.transform;

        if response.response.double_clicked() {
            self.user_view = None;
            self.resetting = true;
            events.emit("reset", Value::Null);
        } else if response.response.clicked()
            && let Some(pos) = response.response.interact_pointer_pos()
        {
            let value = transform.value_from_position(pos);
            let nearest = self.nearest(&transform, pos);
            events.emit("click", json!({ "x": value.x, "y": value.y, "nearest": nearest }));
        }

        // the view the user chose: reported once it changes (a pan when the drag ends), never for the data moving it
        if fits_data {
            self.resetting = false;
        } else if !self.resetting && !response.response.dragged() {
            let view = bounds_array(transform.bounds());
            if self.user_view != Some(view) {
                self.user_view = Some(view);
                let [x_min, x_max, y_min, y_max] = view;
                events.emit("view", json!({ "xMin": x_min, "xMax": x_max, "yMin": y_min, "yMax": y_max }));
            }
        }
        self.transform = Some(transform);
    }
}
