//! The viewer: a [`DataSource`] shown as a grid, read-only, for data too big for a spreadsheet engine (millions of
//! rows), and for data still arriving (shown as it comes, the rows there so far). Only what is in view is drawn.
//!
//! - **Sort**: click a column's header (A-Z, then Z-A, then as it came), or its menu. Rows are ordered by an index:
//!   the data is never moved.
//! - **Filter**: a column's ▾ menu: tick values, or a "contains" text (the same [`Condition`]s as the spreadsheet's).
//! - **Search**: the box at the top finds rows with the text in any column (Ctrl+F).
//!
//! Select with the pointer (Shift to extend) or the arrows; Ctrl+C copies the selection as tab-separated text; the
//! line at the bottom says the selection's count, sum, average, min and max.

use std::collections::{BTreeMap, BTreeSet};
use std::sync::Arc;

use egui::{Align2, CursorIcon, FontId, Key, Pos2, Rect, Sense, Stroke, Ui, pos2, vec2};

use crate::filter::Condition;
use crate::source::{DataSource, Status, Value};

const HEADER_H: f32 = 24.0;
const NUMBER_W: f32 = 64.0;
const BUTTON: f32 = 16.0;
/// Past this many cells, a copy or the figures stop (a million rows by 20 columns is 20 million).
const MAX_CELLS: usize = 200_000;
/// Distinct values a filter menu lists; more, and it offers "contains" only.
const MAX_VALUES: usize = 1_000;

/// A column's menu, open over the grid.
struct ColumnMenu {
    col: usize,
    at: Pos2,
    values: Vec<String>,
    /// More distinct values than listed.
    more: bool,
    ticked: BTreeSet<String>,
    contains: String,
    search: String,
}

/// A data source on screen. Keep it between frames (it holds the sort, filters, search, selection and widths).
pub struct Viewer {
    id: egui::Id,
    source: Arc<dyn DataSource>,
    columns: Vec<String>,
    widths: Vec<f32>,
    seen: u64,
    /// The rows of the source the index has looked at.
    indexed: usize,
    /// The rows shown, as rows of the source, in order (None: all, as they came).
    order: Option<Vec<u32>>,
    /// The index must be made again (the sort, a filter or the search changed).
    dirty: bool,
    rebuilt_at: f64,
    sort: Option<(usize, bool)>,
    filters: BTreeMap<usize, Condition>,
    search: String,
    /// The selection, as (shown row, column): the cursor and where it started.
    cursor: Option<(usize, usize)>,
    anchor: (usize, usize),
    active: bool,
    focus_search: bool,
    menu: Option<ColumnMenu>,
    /// The shown rows drawn in the last frame.
    visible: std::ops::Range<usize>,
    /// Keep the last rows in view as they come (as a log does): scrolled to the bottom, it stays there.
    pub follow: bool,
    pub row_height: f32,
    /// Columns narrower than the window are widened to fill it, keeping their proportions (on at first).
    pub fill_width: bool,
}

impl Viewer {
    pub fn new(source: Arc<dyn DataSource>) -> Viewer {
        static NEXT: std::sync::atomic::AtomicU64 = std::sync::atomic::AtomicU64::new(0);
        Viewer {
            id: egui::Id::unique(("egui-spreadsheet-viewer", NEXT.fetch_add(1, std::sync::atomic::Ordering::Relaxed))),
            source,
            columns: Vec::new(),
            widths: Vec::new(),
            seen: 0,
            indexed: 0,
            order: None,
            dirty: false,
            rebuilt_at: f64::NEG_INFINITY,
            sort: None,
            filters: BTreeMap::new(),
            search: String::new(),
            cursor: None,
            anchor: (0, 0),
            active: false,
            focus_search: false,
            menu: None,
            follow: false,
            visible: 0..0,
            row_height: 22.0,
            fill_width: true,
        }
    }

    /// A CSV file's viewer (read in the background: the rows show as they are read).
    pub fn open(path: impl AsRef<std::path::Path>) -> std::io::Result<Viewer> {
        let path = path.as_ref();
        let name = path.file_name().map_or_else(|| path.display().to_string(), |n| n.to_string_lossy().into_owned());
        Ok(Viewer::from_csv(std::fs::read(path)?, &name))
    }

    /// A CSV's viewer, from its bytes (the first row names the columns).
    pub fn from_csv(bytes: Vec<u8>, name: &str) -> Viewer {
        Viewer::new(crate::source::Rows::load_csv(bytes, name).source())
    }

    /// A dataset's viewer: its column names and its rows.
    pub fn from_rows(name: &str, columns: impl IntoIterator<Item = impl Into<String>>, rows: impl IntoIterator<Item = Vec<Value>>) -> Viewer {
        let data = crate::source::Rows::new(name, columns);
        data.push_many(rows);
        Viewer::new(data.source())
    }

    pub fn source(&self) -> &Arc<dyn DataSource> {
        &self.source
    }

    /// Whether it shows this source (the same one, not equal data).
    pub fn shows(&self, source: &Arc<dyn DataSource>) -> bool {
        std::ptr::addr_eq(Arc::as_ptr(&self.source), Arc::as_ptr(source))
    }

    /// Sorts by a column (from 0): A-Z, or Z-A. Rows that tie keep the order they came in.
    pub fn sort_by(&mut self, col: usize, descending: bool) {
        self.sort = Some((col, descending));
        self.dirty = true;
    }

    pub fn clear_sort(&mut self) {
        self.sort = None;
        self.dirty = true;
    }

    pub fn sorted_by(&self) -> Option<(usize, bool)> {
        self.sort
    }

    /// Shows only the rows whose cell in this column meets the condition (with the other columns' too).
    pub fn filter(&mut self, col: usize, condition: Condition) {
        self.filters.insert(col, condition);
        self.dirty = true;
    }

    pub fn clear_filter(&mut self, col: usize) {
        self.dirty |= self.filters.remove(&col).is_some();
    }

    pub fn clear_filters(&mut self) {
        self.dirty |= !self.filters.is_empty();
        self.filters.clear();
    }

    pub fn filters(&self) -> &BTreeMap<usize, Condition> {
        &self.filters
    }

    /// Shows only the rows with this text in any column, ignoring case ("" shows all).
    pub fn search(&mut self, text: &str) {
        if self.search != text {
            self.search = text.to_owned();
            self.dirty = true;
        }
    }

    /// The rows shown (after filters and search), as the last frame had them; [`Viewer::refresh`] works them out now.
    pub fn shown_rows(&self) -> usize {
        self.order.as_ref().map_or(self.indexed, Vec::len)
    }

    /// The source's row shown at a place (from 0).
    pub fn source_row(&self, shown: usize) -> usize {
        self.order.as_ref().map_or(shown, |o| o[shown] as usize)
    }

    /// A shown cell's text.
    pub fn text(&self, shown: usize, col: usize) -> String {
        self.source.value(self.source_row(shown), col).text().into_owned()
    }

    fn narrowed(&self) -> bool {
        self.sort.is_some() || !self.filters.is_empty() || !self.search.is_empty()
    }

    /// Catches up with the source and with the sort, filters and search (the viewer does it each frame).
    pub fn refresh(&mut self) {
        self.refresh_at(f64::INFINITY);
    }

    fn refresh_at(&mut self, now: f64) {
        let version = self.source.version();
        if version != self.seen {
            let columns = self.source.columns();
            if columns != self.columns {
                self.columns = columns;
                self.measure();
            }
        }
        let rows = self.source.row_count();
        if !self.narrowed() {
            self.order = None;
            self.indexed = rows;
        } else if self.dirty || self.order.is_none() {
            self.rebuild(rows, now);
        } else if rows > self.indexed {
            if self.sort.is_some() {
                // sorted: the new rows go in their places, at most every so often (a sort of millions takes a while)
                let every = if rows > 200_000 { 2.0 } else { 0.4 };
                if now.is_infinite() || now - self.rebuilt_at >= every {
                    self.rebuild(rows, now);
                }
            } else {
                let found = self.matching(self.indexed..rows);
                self.order.get_or_insert_with(Vec::new).extend(found);
                self.indexed = rows;
            }
        } else if rows < self.indexed {
            self.rebuild(rows, now);
        }
        self.seen = version;
        // the selection stays among the rows there are
        let shown = self.shown_rows();
        if let Some((row, col)) = self.cursor
            && (row >= shown || col >= self.columns.len())
        {
            self.cursor = (shown > 0 && !self.columns.is_empty()).then(|| (row.min(shown - 1), col.min(self.columns.len() - 1)));
            self.anchor = (self.anchor.0.min(shown.saturating_sub(1)), self.anchor.1.min(self.columns.len().saturating_sub(1)));
        }
    }

    /// Column widths from the header and the first rows: the ones not set yet.
    fn measure(&mut self) {
        let rows = self.source.row_count().min(200);
        for col in self.widths.len()..self.columns.len() {
            let widest = self.source.column(col, 0..rows).iter().map(|v| v.text().chars().count()).max().unwrap_or(0);
            let chars = widest.max(self.columns[col].chars().count() + 3);
            self.widths.push((chars as f32 * 7.2 + 20.0).clamp(60.0, 320.0));
        }
    }

    fn rebuild(&mut self, rows: usize, now: f64) {
        let mut order = self.matching(0..rows);
        if let Some((col, descending)) = self.sort {
            let keys = self.source.column(col, 0..rows);
            order.sort_by(|a, b| keys[*a as usize].sort_cmp(&keys[*b as usize], descending));
        }
        self.order = Some(order);
        self.indexed = rows;
        self.dirty = false;
        self.rebuilt_at = now;
    }

    /// The rows of a range that pass the filters and the search.
    fn matching(&self, rows: std::ops::Range<usize>) -> Vec<u32> {
        let mut keep = vec![true; rows.len()];
        for (col, condition) in &self.filters {
            for (i, value) in self.source.column(*col, rows.clone()).iter().enumerate() {
                keep[i] = keep[i] && condition.matches(&value.text(), value.number());
            }
        }
        let needle = self.search.trim().to_lowercase();
        if !needle.is_empty() {
            let mut found = vec![false; rows.len()];
            for col in 0..self.columns.len() {
                for (i, value) in self.source.column(col, rows.clone()).iter().enumerate() {
                    if keep[i] && !found[i] && value.text().to_lowercase().contains(&needle) {
                        found[i] = true;
                    }
                }
            }
            keep = found;
        }
        rows.zip(keep).filter_map(|(r, k)| k.then_some(r as u32)).collect()
    }

    /// The shown rows drawn in the last frame (from 0): what is in view.
    pub fn visible_rows(&self) -> std::ops::Range<usize> {
        self.visible.clone()
    }

    /// The selection: (first shown row, first column, last shown row, last column).
    pub fn selection(&self) -> Option<(usize, usize, usize, usize)> {
        let (row, col) = self.cursor?;
        let (r, c) = self.anchor;
        Some((row.min(r), col.min(c), row.max(r), col.max(c)))
    }

    /// Selects a rectangle of shown rows and columns.
    pub fn select(&mut self, from: (usize, usize), to: (usize, usize)) {
        self.anchor = from;
        self.cursor = Some(to);
    }

    /// The selection as tab-separated text (up to 200,000 cells).
    pub fn selection_text(&self) -> String {
        let Some((r0, c0, r1, c1)) = self.selection() else { return String::new() };
        let per_row = c1 - c0 + 1;
        let last = r1.min(r0 + MAX_CELLS / per_row);
        let mut out = String::new();
        for row in r0..=last {
            let source = self.source_row(row);
            let cells: Vec<String> = (c0..=c1).map(|c| self.source.value(source, c).text().replace(['\t', '\n'], " ")).collect();
            out.push_str(&cells.join("\t"));
            out.push('\n');
        }
        out
    }

    /// The selection's figures, as the spreadsheet's status bar gives them.
    pub fn selection_stats(&self) -> Option<crate::statusbar::Stats> {
        let (r0, c0, r1, c1) = self.selection()?;
        if (r0, c0) == (r1, c1) {
            return None;
        }
        let mut stats = crate::statusbar::Stats { min: f64::INFINITY, max: f64::NEG_INFINITY, ..Default::default() };
        let mut seen = 0;
        'all: for row in r0..=r1 {
            let source = self.source_row(row);
            for col in c0..=c1 {
                seen += 1;
                if seen > MAX_CELLS {
                    break 'all;
                }
                match self.source.value(source, col) {
                    Value::Empty => {}
                    Value::Number(n) => {
                        stats.count += 1;
                        stats.numbers += 1;
                        stats.sum += n;
                        stats.min = stats.min.min(n);
                        stats.max = stats.max.max(n);
                    }
                    _ => stats.count += 1,
                }
            }
        }
        if stats.numbers == 0 {
            (stats.min, stats.max) = (0.0, 0.0);
        }
        Some(stats)
    }

    /// Draws it, filling the space it is given.
    pub fn show(&mut self, ui: &mut Ui) {
        let now = ui.input(|i| i.time);
        self.refresh_at(now);
        let status = self.source.status();
        if matches!(status, Status::Loading { .. }) || (self.sort.is_some() && self.indexed < self.source.row_count()) {
            // rows are coming: look again soon (the writer may be on another thread)
            ui.ctx().request_repaint_after(std::time::Duration::from_millis(100));
        }
        self.top_bar(ui, &status);
        ui.separator();
        let grid_h = (ui.available_height() - 24.0).max(60.0);
        ui.allocate_ui(vec2(ui.available_width(), grid_h), |ui| self.grid(ui));
        ui.separator();
        self.bottom_line(ui);
        self.column_menu(ui);
        self.keys(ui);
    }

    fn top_bar(&mut self, ui: &mut Ui, status: &Status) {
        ui.horizontal(|ui| {
            ui.strong(self.source.name());
            let mut text = self.search.clone();
            let search = ui.add(egui::TextEdit::singleline(&mut text).hint_text("Search all columns (Ctrl+F)").desired_width(220.0));
            if std::mem::take(&mut self.focus_search) {
                search.request_focus();
            }
            if search.changed() {
                self.search(&text);
            }
            if !self.search.is_empty() && ui.small_button("x").on_hover_text("Clear the search").clicked() {
                self.search("");
            }
            let total = self.source.row_count();
            if self.order.is_some() && (self.search.len() + self.filters.len()) > 0 {
                ui.label(format!("{} of {} rows", thousands(self.shown_rows()), thousands(total)));
            } else {
                ui.label(format!("{} rows", thousands(total)));
            }
            match status {
                Status::Loading { progress } => {
                    let bar = egui::ProgressBar::new(progress.unwrap_or(0.0)).desired_width(140.0);
                    ui.add(if progress.is_some() { bar.show_percentage() } else { bar.text("loading").animate(true) });
                }
                Status::Failed(why) => {
                    ui.colored_label(ui.visuals().error_fg_color, format!("stopped: {why}"));
                }
                Status::Done => {}
            }
            ui.checkbox(&mut self.follow, "Follow").on_hover_text("Keep the newest rows in view, as a log does");
            if !self.filters.is_empty() && ui.button("Clear filters").clicked() {
                self.clear_filters();
            }
            if let Some((col, descending)) = self.sort {
                let name = self.columns.get(col).cloned().unwrap_or_default();
                if ui.button(format!("Sorted by {name} {}  x", if descending { "Z-A" } else { "A-Z" })).on_hover_text("As they came").clicked() {
                    self.clear_sort();
                }
            }
        });
    }

    fn grid(&mut self, ui: &mut Ui) {
        let row_h = self.row_height;
        let shown = self.shown_rows();
        let cols = self.columns.len();
        // the columns fill the window when they are narrower than it (wider: it scrolls)
        let natural: f32 = self.widths[..cols].iter().sum();
        let room = ui.available_width() - NUMBER_W - ui.spacing().scroll.bar_width - 4.0;
        let stretch = if self.fill_width && natural > 0.0 && natural < room { room / natural } else { 1.0 };
        let mut col_x = Vec::with_capacity(cols + 1);
        col_x.push(0.0);
        for w in &self.widths[..cols] {
            col_x.push(col_x.last().copied().unwrap_or(0.0) + w * stretch);
        }
        let total = vec2(NUMBER_W + col_x[cols], HEADER_H + shown as f32 * row_h);
        let visuals = ui.visuals().clone();
        let font = FontId::proportional(13.0);
        let line = Stroke::new(1.0, visuals.widgets.noninteractive.bg_stroke.color.gamma_multiply(0.6));
        let mut clicked: Option<((usize, usize), bool)> = None;
        let mut dragged: Option<(usize, usize)> = None;
        let mut header_click: Option<usize> = None;
        let mut open_menu: Option<(usize, Pos2)> = None;
        let mut resized: Option<(usize, f32)> = None;
        let mut drawn = 0..0;
        let selection = self.selection();
        let follow = self.follow;

        egui::ScrollArea::both().id_salt(self.id.with("scroll")).auto_shrink([false, false]).stick_to_bottom(follow).show_viewport(ui, |ui, view| {
            let (rect, response) = ui.allocate_exact_size(total, Sense::click_and_drag());
            let painter = ui.painter().clone();
            let origin = rect.min;
            let top = origin.y + view.min.y;
            let side = origin.x + view.min.x;
            let first_row = ((view.min.y - HEADER_H) / row_h).floor().max(0.0) as usize;
            let last_row = (((view.max.y - HEADER_H) / row_h).ceil().max(0.0) as usize).min(shown);
            let first_col = col_x.partition_point(|x| *x + NUMBER_W <= view.min.x).saturating_sub(1).min(cols);
            let last_col = col_x.partition_point(|x| *x + NUMBER_W < view.max.x).min(cols);
            drawn = first_row.min(last_row)..last_row;
            let x_of = |col: usize| origin.x + NUMBER_W + col_x[col];
            let y_of = |row: usize| origin.y + HEADER_H + row as f32 * row_h;

            // the cells in view: their values read once
            for row in first_row..last_row {
                let source = self.source_row(row);
                let y = y_of(row);
                for col in first_col..last_col {
                    let cell = Rect::from_min_max(pos2(x_of(col), y), pos2(x_of(col + 1), y + row_h));
                    if let Some((r0, c0, r1, c1)) = selection
                        && (r0..=r1).contains(&row)
                        && (c0..=c1).contains(&col)
                    {
                        painter.rect_filled(cell, 0.0, visuals.selection.bg_fill.gamma_multiply(0.35));
                    }
                    let value = self.source.value(source, col);
                    if !value.is_empty() {
                        let (anchor, x) = match value {
                            Value::Number(_) => (Align2::RIGHT_CENTER, cell.max.x - 5.0),
                            Value::Bool(_) => (Align2::CENTER_CENTER, cell.center().x),
                            _ => (Align2::LEFT_CENTER, cell.min.x + 5.0),
                        };
                        painter.with_clip_rect(cell.shrink(1.0)).text(pos2(x, cell.center().y), anchor, value.text(), font.clone(), visuals.text_color());
                    }
                }
                painter.hline(x_of(first_col)..=x_of(last_col), y + row_h, line);
            }
            for col in first_col..=last_col {
                painter.vline(x_of(col), top..=y_of(last_row), line);
            }
            if let Some((row, col)) = self.cursor
                && row < shown
                && col < cols
            {
                let cell = Rect::from_min_max(pos2(x_of(col), y_of(row)), pos2(x_of(col + 1), y_of(row) + row_h));
                painter.rect_stroke(cell, 0.0, Stroke::new(2.0, visuals.selection.stroke.color), egui::StrokeKind::Inside);
            }

            // the row numbers, where the view's left is
            let header_fill = visuals.widgets.noninteractive.weak_bg_fill;
            painter.rect_filled(Rect::from_min_max(pos2(side, top), pos2(side + NUMBER_W, origin.y + view.max.y)), 0.0, header_fill);
            for row in first_row..last_row {
                let y = y_of(row);
                let number = thousands(self.source_row(row) + 1);
                painter.text(pos2(side + NUMBER_W - 6.0, y + row_h / 2.0), Align2::RIGHT_CENTER, number, FontId::proportional(11.0), visuals.weak_text_color());
                painter.hline(side..=side + NUMBER_W, y + row_h, line);
            }
            painter.vline(side + NUMBER_W, top..=origin.y + view.max.y, line);

            // the header, where the view's top is: name, sort mark, filter button, a border to drag
            painter.rect_filled(Rect::from_min_max(pos2(side, top), pos2(origin.x + view.max.x, top + HEADER_H)), 0.0, header_fill);
            for col in first_col..last_col {
                let cell = Rect::from_min_max(pos2(x_of(col), top), pos2(x_of(col + 1), top + HEADER_H));
                let text_room = Rect::from_min_max(cell.min, pos2(cell.max.x - BUTTON - 16.0, cell.max.y));
                painter.with_clip_rect(text_room).text(pos2(cell.min.x + 6.0, cell.center().y), Align2::LEFT_CENTER, &self.columns[col], FontId::proportional(13.0), visuals.strong_text_color());
                let name = self.columns[col].clone();
                let hit = ui.interact(cell.shrink2(vec2(3.0, 0.0)), self.id.with(("header", col)), Sense::click());
                hit.widget_info(|| egui::WidgetInfo::labeled(egui::Role::Button, true, &name));
                if hit.clicked() {
                    header_click = Some(col);
                }
                hit.on_hover_text("Sort: A-Z, Z-A, as they came");
                if let Some((sorted, descending)) = self.sort
                    && sorted == col
                {
                    let c = pos2(cell.max.x - BUTTON - 12.0, cell.center().y);
                    let ink = visuals.selection.stroke.color;
                    let points = if descending {
                        vec![pos2(c.x - 4.0, c.y - 2.0), pos2(c.x + 4.0, c.y - 2.0), pos2(c.x, c.y + 3.0)]
                    } else {
                        vec![pos2(c.x - 4.0, c.y + 2.0), pos2(c.x + 4.0, c.y + 2.0), pos2(c.x, c.y - 3.0)]
                    };
                    painter.add(egui::Shape::convex_polygon(points, ink, Stroke::NONE));
                }
                let button = Rect::from_min_size(pos2(cell.max.x - BUTTON - 4.0, cell.center().y - BUTTON / 2.0), vec2(BUTTON, BUTTON));
                let menu = ui.interact(button, self.id.with(("menu", col)), Sense::click());
                menu.widget_info(|| egui::WidgetInfo::labeled(egui::Role::Button, true, format!("Sort and filter {name}")));
                crate::grid::drop_button(&painter, button, self.filters.contains_key(&col) || menu.hovered(), &visuals);
                if menu.on_hover_text("Sort and filter").clicked() {
                    open_menu = Some((col, button.left_bottom()));
                }
                let border = Rect::from_center_size(pos2(cell.max.x, cell.center().y), vec2(6.0, HEADER_H));
                let drag = ui.interact(border, self.id.with(("width", col)), Sense::drag()).on_hover_cursor(CursorIcon::ResizeHorizontal);
                if drag.dragged() {
                    resized = Some((col, drag.drag_delta().x));
                }
                painter.vline(cell.max.x, top..=top + HEADER_H, line);
            }
            painter.hline(side..=origin.x + view.max.x, top + HEADER_H, line);

            // the pointer on the cells
            let at = |pos: Pos2| -> Option<(usize, usize)> {
                if pos.y < top + HEADER_H || pos.x < side + NUMBER_W || shown == 0 || cols == 0 {
                    return None;
                }
                let row = (((pos.y - origin.y - HEADER_H) / row_h).floor().max(0.0) as usize).min(shown - 1);
                let col = col_x.partition_point(|x| *x <= pos.x - origin.x - NUMBER_W).saturating_sub(1).min(cols - 1);
                Some((row, col))
            };
            if let Some(pos) = response.interact_pointer_pos() {
                if response.clicked() || response.drag_started() {
                    if let Some(cell) = at(pos) {
                        clicked = Some((cell, ui.input(|i| i.modifiers.shift)));
                    }
                } else if response.dragged() {
                    dragged = at(pos);
                }
            }
        });

        self.visible = drawn;
        if let Some((cell, extend)) = clicked {
            self.cursor = Some(cell);
            if !extend {
                self.anchor = cell;
            }
            self.active = true;
            self.menu = None;
        } else if ui.input(|i| i.pointer.any_pressed()) && !ui.rect_contains_pointer(ui.max_rect()) {
            self.active = false;
        }
        if let Some(cell) = dragged {
            self.cursor = Some(cell);
        }
        if let Some(col) = header_click {
            match self.sort {
                Some((c, false)) if c == col => self.sort_by(col, true),
                Some((c, true)) if c == col => self.clear_sort(),
                _ => self.sort_by(col, false),
            }
        }
        if let Some((col, dx)) = resized {
            // the drag is on screen; the width is before stretching
            self.widths[col] = (self.widths[col] + dx / stretch).clamp(30.0, 2000.0);
        }
        if let Some((col, at)) = open_menu {
            self.open_menu(col, at);
        }
    }

    fn open_menu(&mut self, col: usize, at: Pos2) {
        let rows = self.source.row_count();
        let mut values = BTreeSet::new();
        let mut more = false;
        // a column of millions is read in parts, and no further than enough values
        let mut start = 0;
        while start < rows && !more {
            let end = (start + 100_000).min(rows);
            for value in self.source.column(col, start..end) {
                values.insert(value.text().into_owned());
                if values.len() > MAX_VALUES {
                    more = true;
                    break;
                }
            }
            start = end;
        }
        let values: Vec<String> = values.into_iter().take(MAX_VALUES).collect();
        let (ticked, contains) = match self.filters.get(&col) {
            Some(Condition::Values(set)) => (set.clone(), String::new()),
            Some(Condition::Contains(text)) => (values.iter().cloned().collect(), text.clone()),
            _ => (values.iter().cloned().collect(), String::new()),
        };
        self.menu = Some(ColumnMenu { col, at, values, more, ticked, contains, search: String::new() });
    }

    fn column_menu(&mut self, ui: &mut Ui) {
        let Some(menu) = self.menu.as_mut() else { return };
        let mut sort: Option<bool> = None;
        let mut apply: Option<Option<Condition>> = None;
        let mut close = false;
        let area = egui::Area::new(self.id.with("column menu")).order(egui::Order::Foreground).fixed_pos(menu.at).show(ui.ctx(), |ui| {
            egui::Frame::popup(ui.style()).show(ui, |ui| {
                ui.set_width(240.0);
                ui.horizontal(|ui| {
                    if ui.button("Sort A-Z").clicked() {
                        sort = Some(false);
                    }
                    if ui.button("Sort Z-A").clicked() {
                        sort = Some(true);
                    }
                });
                ui.separator();
                ui.add(egui::TextEdit::singleline(&mut menu.contains).hint_text("Contains...").desired_width(f32::INFINITY));
                ui.separator();
                if menu.more {
                    ui.weak(format!("More than {} values: filter with \"contains\".", thousands(MAX_VALUES)));
                } else {
                    ui.add(egui::TextEdit::singleline(&mut menu.search).hint_text("Find a value").desired_width(f32::INFINITY));
                    ui.horizontal(|ui| {
                        if ui.small_button("Select all").clicked() {
                            menu.ticked.extend(menu.values.iter().cloned());
                        }
                        if ui.small_button("None").clicked() {
                            menu.ticked.clear();
                        }
                    });
                    let needle = menu.search.to_lowercase();
                    egui::ScrollArea::vertical().max_height(260.0).show(ui, |ui| {
                        for value in menu.values.iter().filter(|v| v.to_lowercase().contains(&needle)) {
                            let mut on = menu.ticked.contains(value);
                            let label = if value.is_empty() { "(blank)" } else { value.as_str() };
                            if ui.checkbox(&mut on, label).changed() {
                                if on {
                                    menu.ticked.insert(value.clone());
                                } else {
                                    menu.ticked.remove(value);
                                }
                            }
                        }
                    });
                }
                ui.separator();
                ui.horizontal(|ui| {
                    if ui.button("OK").clicked() {
                        apply = Some(if !menu.contains.trim().is_empty() {
                            Some(Condition::Contains(menu.contains.trim().to_owned()))
                        } else if !menu.more && menu.ticked.len() < menu.values.len() {
                            Some(Condition::Values(menu.ticked.clone()))
                        } else {
                            None
                        });
                    }
                    if ui.button("Clear filter").clicked() {
                        apply = Some(None);
                    }
                    if ui.button("Cancel").clicked() {
                        close = true;
                    }
                });
            });
        });
        // a click outside it closes it (not the click that opened it: that was on the button)
        let col = menu.col;
        if ui.input(|i| i.pointer.any_pressed())
            && let Some(p) = ui.input(|i| i.pointer.interact_pos())
            && !area.response.rect.contains(p)
        {
            close = true;
        }
        if let Some(descending) = sort {
            self.sort_by(col, descending);
            close = true;
        }
        if let Some(condition) = apply {
            match condition {
                Some(c) => self.filter(col, c),
                None => self.clear_filter(col),
            }
            close = true;
        }
        if close {
            self.menu = None;
        }
    }

    fn keys(&mut self, ui: &mut Ui) {
        if ui.input_mut(|i| i.consume_key(egui::Modifiers::COMMAND, Key::F)) {
            self.focus_search = true;
        }
        if !self.active || self.menu.is_some() || ui.memory(|m| m.focused().is_some()) {
            return;
        }
        let (shown, cols) = (self.shown_rows(), self.columns.len());
        if shown == 0 || cols == 0 {
            return;
        }
        let page = 20;
        for event in ui.input(|i| i.events.clone()) {
            match event {
                egui::Event::Key { key, pressed: true, modifiers, .. } => {
                    let (row, col) = self.cursor.unwrap_or((0, 0));
                    let to = match key {
                        Key::ArrowDown => (row + 1, col),
                        Key::ArrowUp => (row.saturating_sub(1), col),
                        Key::ArrowRight => (row, col + 1),
                        Key::ArrowLeft => (row, col.saturating_sub(1)),
                        Key::PageDown => (row + page, col),
                        Key::PageUp => (row.saturating_sub(page), col),
                        Key::Home if modifiers.command => (0, 0),
                        Key::End if modifiers.command => (shown - 1, col),
                        Key::Home => (row, 0),
                        Key::End => (row, cols - 1),
                        _ => continue,
                    };
                    let to = (to.0.min(shown - 1), to.1.min(cols - 1));
                    self.cursor = Some(to);
                    if !modifiers.shift {
                        self.anchor = to;
                    }
                }
                egui::Event::Copy => {
                    let text = self.selection_text();
                    if !text.is_empty() {
                        ui.ctx().copy_text(text);
                    }
                }
                _ => {}
            }
        }
    }

    fn bottom_line(&self, ui: &mut Ui) {
        ui.horizontal(|ui| {
            if let Some((row, col)) = self.cursor {
                let name = self.columns.get(col).map_or("", String::as_str);
                ui.weak(format!("row {}, {name}", thousands(self.source_row(row) + 1)));
            }
            ui.with_layout(egui::Layout::right_to_left(egui::Align::Center), |ui| {
                if let Some(stats) = self.selection_stats() {
                    let text = match stats.average() {
                        Some(average) => format!(
                            "Average {}   Count {}   Min {}   Max {}   Sum {}",
                            Value::Number(average).text(),
                            stats.count,
                            Value::Number(stats.min).text(),
                            Value::Number(stats.max).text(),
                            Value::Number(stats.sum).text()
                        ),
                        None => format!("Count {}", stats.count),
                    };
                    ui.weak(text);
                }
            });
        });
    }
}

/// `1234567` as `1,234,567`.
pub(crate) fn thousands(n: usize) -> String {
    let digits = n.to_string();
    let mut out = String::with_capacity(digits.len() + digits.len() / 3);
    for (i, ch) in digits.chars().enumerate() {
        if i > 0 && (digits.len() - i).is_multiple_of(3) {
            out.push(',');
        }
        out.push(ch);
    }
    out
}
