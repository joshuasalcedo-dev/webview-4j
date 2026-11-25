//! The spreadsheet on screen, fast on Excel-sized sheets: only the cells in view are drawn (rows *and* columns),
//! each visible cell's formatted value is kept until the workbook changes, and the grid's lines are drawn once per
//! visible row and column, not per cell. The headers stay while the sheet scrolls under them.
//!
//! Click a cell to choose it, Shift (click, drag or arrows) for a rectangle. Type, Enter, F2 or a double-click
//! edits; Enter commits and goes down, Tab right, Escape gives up. Delete clears. Ctrl+C, Ctrl+X, Ctrl+V copy, cut and
//! paste (tab-separated: what other spreadsheets read; copied here, formulas move with the paste), Ctrl+Z / Ctrl+Y
//! undo and redo. The status line shows the selection's count, sum and average, as Excel's does.

use std::collections::HashMap;
use std::sync::atomic::{AtomicU64, Ordering};

use egui::{Align2, Key, Pos2, Rect, RichText, Sense, Stroke, Ui, pos2, vec2};
use ironcalc::base::cell::CellValue;
use ironcalc::base::expressions::types::Area;
use ironcalc::base::types::CellType;
use ironcalc::base::{Model, UserModel};

/// Excel's last row and column.
pub const MAX_ROW: i32 = 1_048_576;
pub const MAX_COL: i32 = 16_384;
/// Beyond the last cell used, this many more rows and columns are shown (and grown as cells fill).
const MORE_ROWS: i32 = 100;
const MORE_COLS: i32 = 10;
/// At least this many, even for an empty sheet.
const MIN_ROWS: i32 = 200;
const MIN_COLS: i32 = 26;
/// The row-number column.
const ROW_HEADER_W: f32 = 52.0;
/// Statistics of the selection stop counting after this many cells (a whole column is a million).
const MAX_STATS_CELLS: usize = 200_000;

/// What happened in one frame: for the host (to save, to tell Java).
#[derive(Debug, Default, Clone, PartialEq)]
pub struct Output {
    /// Cells typed into, cleared or pasted: `(sheet, row, column)`, 1-based row and column.
    pub changed: Vec<(u32, i32, i32)>,
    /// The chosen rectangle `(sheet, first row, first column, last row, last column)`, when it changed.
    pub selected: Option<(u32, i32, i32, i32, i32)>,
}

/// One visible cell, formatted once.
#[derive(Clone)]
struct Shown {
    text: String,
    align: Align2,
    error: bool,
}

/// What a copy left: its text (to know a paste is ours) and IronCalc's clipboard (to paste formulas moved).
struct Copied {
    text: String,
    sheet: u32,
    range: (i32, i32, i32, i32),
    data: ironcalc::base::ClipboardData,
    cut: bool,
}

/// Where the grid was drawn last: for hit tests and for the tests.
#[derive(Clone, Copy)]
struct Placed {
    origin: Pos2,
    row_h: f32,
    header_h: f32,
}

pub struct Spreadsheet {
    id: egui::Id,
    model: UserModel<'static>,
    /// Bumped at every change: the shown values and the columns' places are worked out again.
    version: u64,
    shown: HashMap<(u32, i32, i32), Shown>,
    shown_version: u64,
    /// Each column's left edge from the first, for the sheet shown (index 0 is column 1's).
    col_x: Vec<f32>,
    col_version: Option<(u64, u32)>,
    anchor: (i32, i32),
    cursor: (i32, i32),
    editing: Option<String>,
    focus_editor: bool,
    active: bool,
    scroll_to: bool,
    copied: Option<Copied>,
    placed: Option<Placed>,
    /// Nothing can be changed: no editing, clearing, pasting or undo (choosing and copying still work).
    pub read_only: bool,
}

impl Default for Spreadsheet {
    fn default() -> Self {
        Self::new(UserModel::new_empty("Book1", "en", "UTC", "en").expect("an empty workbook"))
    }
}

/// A column's letters: 1 is A, 27 is AA, 16384 is XFD.
pub fn column_name(mut col: i32) -> String {
    let mut name = Vec::new();
    while col > 0 {
        let rem = ((col - 1) % 26) as u8;
        name.push(b'A' + rem);
        col = (col - 1) / 26;
    }
    name.reverse();
    String::from_utf8(name).unwrap_or_default()
}

/// A cell's name: (2, 3) is C2.
pub fn cell_name(row: i32, col: i32) -> String {
    format!("{}{row}", column_name(col))
}

impl Spreadsheet {
    pub fn new(model: UserModel<'static>) -> Self {
        static NEXT: AtomicU64 = AtomicU64::new(0);
        Spreadsheet {
            id: egui::Id::unique(("egui-spreadsheet", NEXT.fetch_add(1, Ordering::Relaxed))),
            model,
            version: 1,
            shown: HashMap::new(),
            shown_version: 0,
            col_x: Vec::new(),
            col_version: None,
            anchor: (1, 1),
            cursor: (1, 1),
            editing: None,
            focus_editor: false,
            active: false,
            scroll_to: false,
            copied: None,
            placed: None,
            read_only: false,
        }
    }

    /// A workbook from an .xlsx file's bytes.
    pub fn from_xlsx(bytes: &[u8], name: &str) -> Result<Self, String> {
        let workbook = ironcalc::import::load_from_xlsx_bytes(bytes, name, "en", "UTC").map_err(|e| format!("not a workbook: {e:?}"))?;
        let model = Model::from_workbook(workbook, "en")?;
        Ok(Self::new(UserModel::from_model(model)))
    }

    /// The workbook as an .xlsx file's bytes.
    pub fn to_xlsx(&self) -> Result<Vec<u8>, String> {
        ironcalc::export::save_xlsx_to_writer(self.model.get_model(), std::io::Cursor::new(Vec::new()))
            .map(std::io::Cursor::into_inner)
            .map_err(|e| format!("could not write the workbook: {e:?}"))
    }

    /// IronCalc's model, to read and change anything it offers (styles, names, sheets...). Call [`Self::changed`] after.
    pub fn model(&mut self) -> &mut UserModel<'static> {
        &mut self.model
    }

    /// The workbook changed from outside (through [`Self::model`]): what is shown is worked out again.
    pub fn changed(&mut self) {
        self.version += 1;
    }

    pub fn sheet(&self) -> u32 {
        self.model.get_selected_sheet()
    }

    pub fn sheet_names(&self) -> Vec<String> {
        self.model.get_worksheets_properties().into_iter().map(|s| s.name).collect()
    }

    pub fn select_sheet(&mut self, sheet: u32) {
        if self.model.set_selected_sheet(sheet).is_ok() {
            self.editing = None;
            self.anchor = (1, 1);
            self.cursor = (1, 1);
            self.version += 1;
        }
    }

    /// Writes into a cell as if typed: a value, or a formula (`=SUM(A1:A3)`). 1-based row and column.
    pub fn set(&mut self, sheet: u32, row: i32, col: i32, text: &str) -> Result<(), String> {
        self.model.set_user_input(sheet, row, col, text)?;
        self.version += 1;
        Ok(())
    }

    /// What a cell shows (formatted as Excel would).
    pub fn text(&self, sheet: u32, row: i32, col: i32) -> String {
        self.model.get_formatted_cell_value(sheet, row, col).unwrap_or_default()
    }

    /// What was typed into a cell (its formula, or its value).
    pub fn content(&self, sheet: u32, row: i32, col: i32) -> String {
        self.model.get_cell_content(sheet, row, col).unwrap_or_default()
    }

    /// The chosen rectangle: first row, first column, last row, last column.
    pub fn selection(&self) -> (i32, i32, i32, i32) {
        (self.anchor.0.min(self.cursor.0), self.anchor.1.min(self.cursor.1), self.anchor.0.max(self.cursor.0), self.anchor.1.max(self.cursor.1))
    }

    /// The cell with the cursor.
    pub fn cursor(&self) -> (i32, i32) {
        self.cursor
    }

    /// Chooses a cell, and scrolls to it.
    pub fn select(&mut self, row: i32, col: i32) {
        self.move_to(row, col, false);
    }

    /// Where a cell's middle was drawn on screen (None before the first frame): for the host's tests.
    pub fn cell_center(&self, row: i32, col: i32) -> Option<Pos2> {
        let placed = self.placed?;
        Some(self.cell_rect(placed, row, col).center())
    }

    // ---- inside ----

    fn rows_cols(&self) -> (i32, i32) {
        let used = self.model.get_model().workbook.worksheet(self.sheet()).map(|s| s.dimension()).ok();
        let (max_row, max_col) = used.map_or((1, 1), |d| (d.max_row, d.max_column));
        let rows = (max_row + MORE_ROWS).max(MIN_ROWS).max(self.cursor.0 + 1).min(MAX_ROW);
        let cols = (max_col + MORE_COLS).max(MIN_COLS).max(self.cursor.1 + 1).min(MAX_COL);
        (rows, cols)
    }

    fn columns(&mut self, cols: i32) {
        let key = (self.version, self.sheet());
        if self.col_version == Some(key) && self.col_x.len() == cols as usize + 1 {
            return;
        }
        let sheet = self.sheet();
        let mut x = 0.0;
        self.col_x.clear();
        self.col_x.push(0.0);
        for col in 1..=cols {
            x += self.model.get_column_width(sheet, col).map_or(90.0, |w| w as f32).max(4.0);
            self.col_x.push(x);
        }
        self.col_version = Some(key);
    }

    fn cell_rect(&self, placed: Placed, row: i32, col: i32) -> Rect {
        let x0 = self.col_x.get(col as usize - 1).copied().unwrap_or(0.0);
        let x1 = self.col_x.get(col as usize).copied().unwrap_or(x0 + 90.0);
        let y0 = placed.header_h + (row - 1) as f32 * placed.row_h;
        Rect::from_min_max(placed.origin + vec2(ROW_HEADER_W + x0, y0), placed.origin + vec2(ROW_HEADER_W + x1, y0 + placed.row_h))
    }

    /// The cell at a point on screen.
    fn cell_at(&self, placed: Placed, pos: Pos2, rows: i32) -> Option<(i32, i32)> {
        let local = pos - placed.origin;
        if local.x < ROW_HEADER_W || local.y < placed.header_h {
            return None;
        }
        let x = local.x - ROW_HEADER_W;
        let col = self.col_x.partition_point(|edge| *edge <= x) as i32;
        let row = ((local.y - placed.header_h) / placed.row_h).floor() as i32 + 1;
        (col >= 1 && col < self.col_x.len() as i32 && row >= 1 && row <= rows).then_some((row, col))
    }

    fn shown(&mut self, sheet: u32, row: i32, col: i32) -> Shown {
        if self.shown_version != self.version {
            self.shown.clear();
            self.shown_version = self.version;
        }
        if let Some(s) = self.shown.get(&(sheet, row, col)) {
            return s.clone();
        }
        let text = self.model.get_formatted_cell_value(sheet, row, col).unwrap_or_default();
        let kind = if text.is_empty() { None } else { self.model.get_cell_type(sheet, row, col).ok() };
        let (align, error) = match kind {
            Some(CellType::Number) => (Align2::RIGHT_CENTER, false),
            Some(CellType::ErrorValue) => (Align2::CENTER_CENTER, true),
            Some(CellType::LogicalValue) => (Align2::CENTER_CENTER, false),
            _ => (Align2::LEFT_CENTER, false),
        };
        let s = Shown { text, align, error };
        self.shown.insert((sheet, row, col), s.clone());
        s
    }

    fn move_to(&mut self, row: i32, col: i32, extend: bool) {
        self.cursor = (row.clamp(1, MAX_ROW), col.clamp(1, MAX_COL));
        if !extend {
            self.anchor = self.cursor;
        }
        self.scroll_to = true;
    }

    fn area(&self) -> Area {
        let (r0, c0, r1, c1) = self.selection();
        Area { sheet: self.sheet(), row: r0, column: c0, width: c1 - c0 + 1, height: r1 - r0 + 1 }
    }

    fn start_editing(&mut self, text: String) {
        if !self.read_only {
            self.editing = Some(text);
            self.focus_editor = true;
        }
    }

    fn commit(&mut self, out: &mut Output) {
        if let Some(text) = self.editing.take() {
            let (sheet, (row, col)) = (self.sheet(), self.cursor);
            if !self.read_only && text != self.content(sheet, row, col) {
                match self.set(sheet, row, col, &text) {
                    Ok(()) => out.changed.push((sheet, row, col)),
                    Err(e) => log::warn!("{}: {e}", cell_name(row, col)),
                }
            }
        }
    }

    fn changed_area(&mut self, out: &mut Output) {
        let (r0, c0, r1, c1) = self.selection();
        let sheet = self.sheet();
        for row in r0..=r1.min(r0 + 1000) {
            for col in c0..=c1.min(c0 + 100) {
                out.changed.push((sheet, row, col));
            }
        }
        self.version += 1;
    }

    /// The selection as tab-separated text (what was typed: formulas as formulas).
    fn copy_text(&self) -> String {
        let (r0, c0, r1, c1) = self.selection();
        let sheet = self.sheet();
        (r0..=r1)
            .map(|row| (c0..=c1).map(|col| self.content(sheet, row, col)).collect::<Vec<_>>().join("\t"))
            .collect::<Vec<_>>()
            .join("\n")
    }

    fn copy(&mut self, ui: &Ui, cut: bool, out: &mut Output) {
        let (r0, c0, r1, c1) = self.selection();
        let _ = self.model.set_selected_range(r0, c0, r1, c1);
        let text = self.copy_text();
        ui.ctx().copy_text(text.clone());
        // IronCalc's own clipboard: a paste of it moves formulas' references, and keeps styles
        let ours = self.model.copy_to_clipboard().ok().and_then(|c| serde_json::to_value(&c).ok()).and_then(|v| {
            let data = serde_json::from_value(v.get("data")?.clone()).ok()?;
            Some((data, v.get("sheet")?.as_u64()? as u32))
        });
        self.copied = ours.map(|(data, sheet)| Copied { text, sheet, range: (r0, c0, r1, c1), data, cut });
        if cut && self.copied.is_none() && !self.read_only {
            let _ = self.model.range_clear_contents(&self.area());
            self.changed_area(out);
        }
    }

    fn paste(&mut self, text: &str, out: &mut Output) {
        if self.read_only {
            return;
        }
        let (row, col) = self.cursor;
        let _ = self.model.set_selected_cell(row, col);
        let same = |a: &str, b: &str| a.replace("\r\n", "\n").trim_end() == b.replace("\r\n", "\n").trim_end();
        let pasted = match self.copied.take() {
            Some(c) if same(&c.text, text) => {
                let r = self.model.paste_from_clipboard(c.sheet, c.range, &c.data, c.cut);
                if !c.cut {
                    self.copied = Some(c);
                }
                r
            }
            other => {
                self.copied = other;
                self.model.paste_csv_string(&Area { sheet: self.sheet(), row, column: col, width: 1, height: 1 }, text)
            }
        };
        match pasted {
            Ok(()) => {
                // IronCalc chose what was pasted
                let view = self.model.get_selected_view();
                self.anchor = (view.range[0], view.range[1]);
                self.cursor = (view.range[2], view.range[3]);
                self.changed_area(out);
            }
            Err(e) => log::warn!("paste: {e}"),
        }
    }

    fn keys(&mut self, ui: &Ui, out: &mut Output) {
        let events = ui.input(|i| i.events.clone());
        let (row, col) = self.cursor;
        for event in events {
            match event {
                egui::Event::Key { key, pressed: true, modifiers, .. } => {
                    let shift = modifiers.shift;
                    match key {
                        Key::ArrowUp => self.move_to(row - 1, col, shift),
                        Key::ArrowDown => self.move_to(row + 1, col, shift),
                        Key::ArrowLeft => self.move_to(row, col - 1, shift),
                        Key::ArrowRight => self.move_to(row, col + 1, shift),
                        Key::PageDown => self.move_to(row + 30, col, shift),
                        Key::PageUp => self.move_to(row - 30, col, shift),
                        Key::Home if modifiers.command => self.move_to(1, 1, shift),
                        Key::Home => self.move_to(row, 1, shift),
                        Key::Tab => self.move_to(row, col + if shift { -1 } else { 1 }, false),
                        Key::Enter | Key::F2 => {
                            let content = self.content(self.sheet(), row, col);
                            self.start_editing(content);
                        }
                        Key::Delete | Key::Backspace if !self.read_only => {
                            if self.model.range_clear_contents(&self.area()).is_ok() {
                                self.changed_area(out);
                            }
                        }
                        Key::Z | Key::Y if modifiers.command && !self.read_only => {
                            let done = if key == Key::Z { self.model.undo() } else { self.model.redo() };
                            if done.is_ok() {
                                self.version += 1;
                                out.changed.push((self.sheet(), row, col));
                            }
                        }
                        _ => {}
                    }
                }
                egui::Event::Text(text) if !text.is_empty() => self.start_editing(text),
                egui::Event::Copy => self.copy(ui, false, out),
                egui::Event::Cut => self.copy(ui, true, out),
                egui::Event::Paste(text) => self.paste(&text, out),
                _ => {}
            }
        }
    }

    pub fn show(&mut self, ui: &mut Ui) -> Output {
        let mut out = Output::default();
        let before = self.selection();
        let whole = ui.max_rect();

        self.formula_bar(ui, &mut out);
        ui.separator();
        let bottom_h = ui.spacing().interact_size.y + 12.0;
        let grid_h = (ui.available_height() - bottom_h).max(0.0);
        ui.allocate_ui(vec2(ui.available_width(), grid_h), |ui| self.grid(ui, &mut out));
        self.sheets_and_status(ui);

        if ui.input(|i| i.pointer.any_pressed())
            && let Some(at) = ui.input(|i| i.pointer.interact_pos())
            && !whole.contains(at)
        {
            self.active = false;
        }
        if self.active && self.editing.is_none() {
            self.keys(ui, &mut out);
        }
        if self.selection() != before {
            let (r0, c0, r1, c1) = self.selection();
            out.selected = Some((self.sheet(), r0, c0, r1, c1));
        }
        out
    }

    fn formula_bar(&mut self, ui: &mut Ui, out: &mut Output) {
        ui.horizontal(|ui| {
            let (r0, c0, r1, c1) = self.selection();
            let name = if (r0, c0) == (r1, c1) { cell_name(r0, c0) } else { format!("{}:{}", cell_name(r0, c0), cell_name(r1, c1)) };
            ui.add_sized([88.0, ui.spacing().interact_size.y], egui::Label::new(RichText::new(name).monospace().strong()));
            let mut text = self.editing.clone().unwrap_or_else(|| self.content(self.sheet(), self.cursor.0, self.cursor.1));
            let response = ui.add(
                egui::TextEdit::singleline(&mut text)
                    .id(self.id.with("bar"))
                    .hint_text("Value or =formula")
                    .desired_width(f32::INFINITY)
                    .interactive(!self.read_only),
            );
            if response.changed() {
                self.editing = Some(text);
            }
            if response.lost_focus() && self.editing.is_some() {
                if ui.input(|i| i.key_pressed(Key::Escape)) {
                    self.editing = None;
                } else {
                    self.commit(out);
                }
                self.active = true;
            }
        });
    }

    fn grid(&mut self, ui: &mut Ui, out: &mut Output) {
        let (rows, cols) = self.rows_cols();
        self.columns(cols);
        let row_h = (ui.text_style_height(&egui::TextStyle::Body) + 8.0).round();
        let header_h = row_h;
        let total = vec2(ROW_HEADER_W + self.col_x.last().copied().unwrap_or(0.0), header_h + rows as f32 * row_h);
        let visuals = ui.visuals().clone();
        let line = Stroke::new(1.0, visuals.widgets.noninteractive.bg_stroke.color);
        let header_fill = visuals.widgets.noninteractive.weak_bg_fill;
        let font = egui::TextStyle::Body.resolve(ui.style());
        let sheet = self.sheet();
        let mut clicked: Option<((i32, i32), bool, bool)> = None;
        let mut dragged_to: Option<(i32, i32)> = None;
        let mut edit_done: Option<Key> = None;

        egui::ScrollArea::both().id_salt(self.id.with("scroll")).auto_shrink([false, false]).show_viewport(ui, |ui, view| {
            let (rect, response) = ui.allocate_exact_size(total, Sense::click_and_drag());
            let placed = Placed { origin: rect.min, row_h, header_h };
            self.placed = Some(placed);
            let painter = ui.painter_at(ui.clip_rect());

            // what is in view: rows by arithmetic, columns by a binary search of their edges
            let first_row = (((view.min.y - header_h) / row_h).floor() as i32 + 1).clamp(1, rows);
            let last_row = (((view.max.y - header_h) / row_h).ceil() as i32 + 1).clamp(1, rows);
            let x_from = (view.min.x - ROW_HEADER_W).max(0.0);
            let x_to = (view.max.x - ROW_HEADER_W).max(0.0);
            let first_col = (self.col_x.partition_point(|e| *e <= x_from) as i32).clamp(1, cols);
            let last_col = (self.col_x.partition_point(|e| *e < x_to) as i32).clamp(1, cols);

            // cells
            let (s_r0, s_c0, s_r1, s_c1) = self.selection();
            for row in first_row..=last_row {
                for col in first_col..=last_col {
                    let cell = self.cell_rect(placed, row, col);
                    let in_selection = row >= s_r0 && row <= s_r1 && col >= s_c0 && col <= s_c1;
                    if in_selection && (s_r0, s_c0) != (s_r1, s_c1) {
                        painter.rect_filled(cell, 0.0, visuals.selection.bg_fill.gamma_multiply(0.2));
                    }
                    if self.editing.is_some() && (row, col) == self.cursor {
                        continue;
                    }
                    let shown = self.shown(sheet, row, col);
                    if shown.text.is_empty() {
                        continue;
                    }
                    let inner = cell.shrink2(vec2(4.0, 0.0));
                    let pos = match shown.align {
                        Align2::RIGHT_CENTER => inner.right_center(),
                        Align2::CENTER_CENTER => inner.center(),
                        _ => inner.left_center(),
                    };
                    let color = if shown.error { visuals.error_fg_color } else { visuals.text_color() };
                    painter.with_clip_rect(cell.intersect(painter.clip_rect())).text(pos, shown.align, &shown.text, font.clone(), color);
                }
            }

            // the lines: once per visible row and column
            let left = rect.min.x + ROW_HEADER_W;
            let right = rect.min.x + total.x;
            for row in first_row..=last_row {
                let y = rect.min.y + header_h + row as f32 * row_h;
                painter.hline(left..=right, y, line);
            }
            for col in first_col..=last_col {
                let x = left + self.col_x[col as usize];
                painter.vline(x, rect.min.y + header_h..=rect.min.y + total.y, line);
            }

            // the cursor
            let cursor = self.cell_rect(placed, self.cursor.0, self.cursor.1);
            painter.rect_stroke(cursor.shrink(0.5), 0.0, Stroke::new(2.0, visuals.selection.bg_fill), egui::StrokeKind::Inside);

            // the headers, where the view is: they stay while the sheet scrolls
            let top = rect.min.y + view.min.y;
            let side = rect.min.x + view.min.x;
            painter.rect_filled(Rect::from_min_max(pos2(side, top), pos2(rect.min.x + view.max.x, top + header_h)), 0.0, header_fill);
            painter.rect_filled(Rect::from_min_max(pos2(side, top), pos2(side + ROW_HEADER_W, rect.min.y + view.max.y)), 0.0, header_fill);
            for col in first_col..=last_col {
                let x0 = left + self.col_x[col as usize - 1];
                let x1 = left + self.col_x[col as usize];
                let color = if col >= s_c0 && col <= s_c1 { visuals.selection.bg_fill } else { visuals.text_color() };
                painter.text(pos2((x0 + x1) / 2.0, top + header_h / 2.0), Align2::CENTER_CENTER, column_name(col), font.clone(), color);
                painter.vline(x1, top..=top + header_h, line);
            }
            for row in first_row..=last_row {
                let y0 = rect.min.y + header_h + (row - 1) as f32 * row_h;
                let color = if row >= s_r0 && row <= s_r1 { visuals.selection.bg_fill } else { visuals.weak_text_color() };
                painter.text(pos2(side + ROW_HEADER_W - 6.0, y0 + row_h / 2.0), Align2::RIGHT_CENTER, row.to_string(), font.clone(), color);
                painter.hline(side..=side + ROW_HEADER_W, y0 + row_h, line);
            }
            painter.rect_filled(Rect::from_min_size(pos2(side, top), vec2(ROW_HEADER_W, header_h)), 0.0, header_fill);
            painter.hline(side..=rect.min.x + view.max.x, top + header_h, line);
            painter.vline(side + ROW_HEADER_W, top..=rect.min.y + view.max.y, line);

            // the editor, over the cell
            let editor_at = self.cell_rect(placed, self.cursor.0, self.cursor.1);
            let editor_id = self.id.with("cell");
            if let Some(text) = self.editing.as_mut() {
                let at = editor_at;
                let edit = egui::TextEdit::singleline(text).id(editor_id).frame(egui::Frame::NONE).desired_width(at.width() - 8.0);
                let r = ui.put(at.shrink2(vec2(4.0, 2.0)), edit);
                if std::mem::take(&mut self.focus_editor) {
                    r.request_focus();
                }
                if r.lost_focus() {
                    edit_done = Some(ui.input(|i| {
                        if i.key_pressed(Key::Escape) {
                            Key::Escape
                        } else if i.key_pressed(Key::Tab) {
                            Key::Tab
                        } else {
                            Key::Enter
                        }
                    }));
                }
            }

            // the pointer
            if let Some(pos) = response.interact_pointer_pos() {
                if response.drag_started() || response.clicked() || response.double_clicked() {
                    if let Some(at) = self.cell_at(placed, pos, rows) {
                        let shift = ui.input(|i| i.modifiers.shift);
                        clicked = Some((at, shift, response.double_clicked()));
                    }
                } else if response.dragged()
                    && let Some(at) = self.cell_at(placed, pos, rows)
                {
                    dragged_to = Some(at);
                }
            }
            if std::mem::take(&mut self.scroll_to) {
                let at = self.cell_rect(placed, self.cursor.0, self.cursor.1);
                // the headers cover the top and left: keep the cell clear of them
                ui.scroll_to_rect(at.expand2(vec2(ROW_HEADER_W, header_h)), None);
            }
        });

        if let Some(key) = edit_done {
            let (row, col) = self.cursor;
            match key {
                Key::Escape => self.editing = None,
                Key::Tab => {
                    self.commit(out);
                    self.move_to(row, col + 1, false);
                }
                _ => {
                    self.commit(out);
                    self.move_to(row + 1, col, false);
                }
            }
            self.active = true;
        }
        if let Some((at, extend, double)) = clicked {
            self.commit(out);
            self.active = true;
            self.cursor = at;
            if !extend {
                self.anchor = at;
            }
            if double {
                let content = self.content(sheet, at.0, at.1);
                self.start_editing(content);
            }
        }
        if let Some(at) = dragged_to {
            self.cursor = at;
        }
    }

    fn sheets_and_status(&mut self, ui: &mut Ui) {
        ui.separator();
        ui.horizontal(|ui| {
            let current = self.sheet();
            for (i, name) in self.sheet_names().into_iter().enumerate() {
                if ui.selectable_label(i as u32 == current, name).clicked() && i as u32 != current {
                    self.select_sheet(i as u32);
                }
            }
            ui.with_layout(egui::Layout::right_to_left(egui::Align::Center), |ui| {
                ui.weak(self.stats());
            });
        });
    }

    /// The selection's count, sum and average, as Excel's status bar shows them.
    pub fn stats(&self) -> String {
        let (r0, c0, r1, c1) = self.selection();
        if (r0, c0) == (r1, c1) {
            return String::new();
        }
        let sheet = self.sheet();
        let model = self.model.get_model();
        let (mut count, mut numbers, mut sum, mut seen) = (0usize, 0usize, 0.0, 0usize);
        'all: for row in r0..=r1 {
            for col in c0..=c1 {
                seen += 1;
                if seen > MAX_STATS_CELLS {
                    break 'all;
                }
                match model.get_cell_value_by_index(sheet, row, col) {
                    Ok(CellValue::Number(n)) => {
                        count += 1;
                        numbers += 1;
                        sum += n;
                    }
                    Ok(CellValue::None) | Err(_) => {}
                    Ok(_) => count += 1,
                }
            }
        }
        if numbers == 0 {
            return format!("Count {count}");
        }
        let fmt = |n: f64| {
            if n.fract() == 0.0 && n.abs() < 1e15 {
                format!("{n:.0}")
            } else {
                format!("{n:.4}").trim_end_matches('0').trim_end_matches('.').to_owned()
            }
        };
        format!("Average {}   Count {count}   Sum {}", fmt(sum / numbers as f64), fmt(sum))
    }
}

/// A workbook being read in the background (native: on a thread of its own; in the page there are none, so at once).
pub struct Loading {
    #[cfg(not(target_arch = "wasm32"))]
    receiver: std::sync::mpsc::Receiver<Result<ironcalc::base::types::Workbook, String>>,
    #[cfg(target_arch = "wasm32")]
    done: Option<Result<ironcalc::base::types::Workbook, String>>,
    pub name: String,
}

impl Loading {
    /// Starts reading an .xlsx file's bytes.
    pub fn start(bytes: Vec<u8>, name: &str) -> Loading {
        let read = {
            let name = name.to_owned();
            move || ironcalc::import::load_from_xlsx_bytes(&bytes, &name, "en", "UTC").map_err(|e| format!("not a workbook: {e:?}"))
        };
        #[cfg(not(target_arch = "wasm32"))]
        {
            let (sender, receiver) = std::sync::mpsc::channel();
            std::thread::spawn(move || {
                let _ = sender.send(read());
            });
            Loading { receiver, name: name.to_owned() }
        }
        #[cfg(target_arch = "wasm32")]
        {
            Loading { done: Some(read()), name: name.to_owned() }
        }
    }

    /// The workbook, once read (None while it is not yet).
    pub fn poll(&mut self) -> Option<Result<Spreadsheet, String>> {
        #[cfg(not(target_arch = "wasm32"))]
        let read = match self.receiver.try_recv() {
            Ok(r) => r,
            Err(std::sync::mpsc::TryRecvError::Empty) => return None,
            Err(std::sync::mpsc::TryRecvError::Disconnected) => Err("the reading stopped".to_owned()),
        };
        #[cfg(target_arch = "wasm32")]
        let read = self.done.take()?;
        Some(read.and_then(|workbook| Model::from_workbook(workbook, "en")).map(|m| Spreadsheet::new(UserModel::from_model(m))))
    }

    /// What shows while it reads: a spinner and the file's name.
    pub fn show(&self, ui: &mut Ui) {
        ui.centered_and_justified(|ui| {
            ui.horizontal(|ui| {
                ui.spinner();
                ui.label(format!("Opening {}…", self.name));
            });
        });
    }
}
