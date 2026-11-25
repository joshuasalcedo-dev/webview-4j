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

use egui::{Align2, Color32, Key, Pos2, Rect, RichText, Sense, Stroke, Ui, pos2, vec2};
use ironcalc::base::expressions::types::Area;
use ironcalc::base::types::CellType;
use ironcalc::base::{Model, UserModel};

use crate::style::{CellStyle, Look};

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
/// What happened in one frame: for the host (to save, to tell Java).
#[derive(Debug, Default, Clone, PartialEq)]
pub struct Output {
    /// Cells typed into, cleared or pasted: `(sheet, row, column)`, 1-based row and column.
    pub changed: Vec<(u32, i32, i32)>,
    /// The chosen rectangle `(sheet, first row, first column, last row, last column)`, when it changed.
    pub selected: Option<(u32, i32, i32, i32, i32)>,
    /// A value refused by a cell's validation, and why.
    pub refused: Option<String>,
}

/// A menu open over the grid.
enum Menu {
    /// A filter button's: the column's values, the ones ticked, what is typed in its search.
    Filter { col: i32, at: Pos2, values: Vec<String>, ticked: std::collections::BTreeSet<String>, search: String },
    /// A list validation's choices, for the cell with the cursor.
    List { at: Pos2, choices: Vec<String> },
}

/// One visible cell, formatted once.
#[derive(Clone)]
struct Shown {
    text: String,
    align: Align2,
    error: bool,
    /// Its style, as the file has it (fill, font, alignment, borders).
    look: Look,
    /// A number as fitted to its cell's width (measured once, not every frame).
    fitted: Option<(f32, String)>,
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
    pub(crate) model: UserModel<'static>,
    /// Bumped at every change: the shown values and the columns' places are worked out again.
    version: u64,
    /// Each sheet's filter (see filter.rs), the tables (table.rs), the validation rules (validation.rs).
    pub(crate) filters: HashMap<u32, crate::filter::AutoFilter>,
    pub(crate) tables: Vec<crate::table::Table>,
    pub(crate) validations: Vec<crate::validation::Validation>,
    menu: Option<Menu>,
    /// Why the last value typed (or the last tool used) was refused (shown until the next one is taken).
    pub(crate) refused: Option<String>,
    /// The toolbar's colours, as last chosen.
    pub(crate) tool_font: [u8; 3],
    pub(crate) tool_fill: [u8; 3],
    /// The toolbar at the top (on at first).
    pub toolbar: bool,
    shown: HashMap<(u32, i32, i32), Shown>,
    shown_version: u64,
    /// Each column's left edge from the first, for the sheet shown (index 0 is column 1's).
    col_x: Vec<f32>,
    /// Each row's top from the first (index 0 is row 1's; a hidden row is as high as nothing).
    row_y: Vec<f32>,
    layout_key: Option<(u64, u32, i32, i32, u32)>,
    /// The sheet's last used row and column, per version: IronCalc's `dimension()` reads every cell, too slow for
    /// every frame on a big sheet.
    used: Option<((u64, u32), (i32, i32))>,
    anchor: (i32, i32),
    cursor: (i32, i32),
    editing: Option<String>,
    focus_editor: bool,
    /// An edit ended this frame (Enter, Tab, Escape): that key was the editor's, not the grid's.
    edit_ended: bool,
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
        let mut sheet = Spreadsheet {
            id: egui::Id::unique(("egui-spreadsheet", NEXT.fetch_add(1, Ordering::Relaxed))),
            model,
            version: 1,
            filters: HashMap::new(),
            tables: Vec::new(),
            validations: Vec::new(),
            menu: None,
            refused: None,
            tool_font: crate::toolbar::START_FONT,
            tool_fill: crate::toolbar::START_FILL,
            toolbar: true,
            shown: HashMap::new(),
            shown_version: 0,
            col_x: Vec::new(),
            row_y: Vec::new(),
            layout_key: None,
            used: None,
            anchor: (1, 1),
            cursor: (1, 1),
            editing: None,
            focus_editor: false,
            edit_ended: false,
            active: false,
            scroll_to: false,
            copied: None,
            placed: None,
            read_only: false,
        };
        // the tables an .xlsx has, with their filter buttons
        sheet.tables_from_file();
        sheet
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

    /// Styles a rectangle (first row, first column, last row, last column; 1-based): only what `style` sets changes.
    /// Saved with the workbook.
    pub fn set_style(&mut self, sheet: u32, range: (i32, i32, i32, i32), style: &CellStyle) -> Result<(), String> {
        let (r0, c0, r1, c1) = range;
        let area = Area { sheet, row: r0.min(r1), column: c0.min(c1), width: (c1 - c0).abs() + 1, height: (r1 - r0).abs() + 1 };
        style.apply(&mut self.model, &area)?;
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

    /// Takes back the last change (false: nothing to take back).
    pub fn undo(&mut self) -> bool {
        let done = !self.read_only && self.model.can_undo() && self.model.undo().is_ok();
        if done {
            self.after_undo();
        }
        done
    }

    /// Does again what was taken back (false: nothing to do again).
    pub fn redo(&mut self) -> bool {
        let done = !self.read_only && self.model.can_redo() && self.model.redo().is_ok();
        if done {
            self.after_undo();
        }
        done
    }

    /// After an undo or redo: hidden rows came back as they were, so the filter is applied again.
    fn after_undo(&mut self) {
        self.changed();
        let sheet = self.sheet();
        if self.filters.get(&sheet).is_some_and(|f| !f.conditions.is_empty()) {
            let _ = self.apply_filter(sheet);
        }
    }

    /// Where a cell was drawn on screen (None before the first frame): for the host's tests.
    pub fn cell_screen_rect(&self, row: i32, col: i32) -> Option<Rect> {
        let placed = self.placed?;
        Some(self.cell_rect(placed, row, col))
    }

    /// Where a cell's middle was drawn on screen (None before the first frame): for the host's tests.
    pub fn cell_center(&self, row: i32, col: i32) -> Option<Pos2> {
        let placed = self.placed?;
        Some(self.cell_rect(placed, row, col).center())
    }

    // ---- inside ----

    fn rows_cols(&mut self) -> (i32, i32) {
        let key = (self.version, self.sheet());
        let (max_row, max_col) = match self.used {
            Some((k, used)) if k == key => used,
            _ => {
                let d = self.model.get_model().workbook.worksheet(self.sheet()).map(|s| s.dimension()).ok();
                let used = d.map_or((1, 1), |d| (d.max_row, d.max_column));
                self.used = Some((key, used));
                used
            }
        };
        let rows = (max_row + MORE_ROWS).max(MIN_ROWS).max(self.cursor.0 + 1).min(MAX_ROW);
        let cols = (max_col + MORE_COLS).max(MIN_COLS).max(self.cursor.1 + 1).min(MAX_COL);
        (rows, cols)
    }

    /// Where each column and row starts, worked out once per change: widths and heights from the sheet (its
    /// sparse lists of the rows and columns that differ, so a big sheet costs no more), hidden ones as nothing.
    fn layout(&mut self, rows: i32, cols: i32, row_h: f32) {
        let sheet = self.sheet();
        let key = (self.version, sheet, rows, cols, row_h.to_bits());
        if self.layout_key == Some(key) {
            return;
        }
        let Ok(worksheet) = self.model.get_model().workbook.worksheet(sheet) else { return };
        let mut widths = Vec::with_capacity(cols as usize);
        for col in 1..=cols {
            let hidden = worksheet.is_column_hidden(col).unwrap_or(false);
            widths.push(if hidden { 0.0 } else { self.model.get_column_width(sheet, col).map_or(90.0, |w| w as f32).max(4.0) });
        }
        let mut heights = vec![row_h; rows as usize];
        for r in &worksheet.rows {
            if r.r >= 1 && r.r <= rows {
                heights[r.r as usize - 1] = if r.hidden {
                    0.0
                } else if r.custom_height {
                    self.model.get_row_height(sheet, r.r).map_or(row_h, |h| h as f32).max(4.0)
                } else {
                    row_h
                };
            }
        }
        let prefix = |sizes: Vec<f32>| {
            let mut edges = Vec::with_capacity(sizes.len() + 1);
            edges.push(0.0);
            let mut at = 0.0;
            for size in sizes {
                at += size;
                edges.push(at);
            }
            edges
        };
        self.col_x = prefix(widths);
        self.row_y = prefix(heights);
        self.layout_key = Some(key);
    }

    fn cell_rect(&self, placed: Placed, row: i32, col: i32) -> Rect {
        let x0 = self.col_x.get(col as usize - 1).copied().unwrap_or(0.0);
        let x1 = self.col_x.get(col as usize).copied().unwrap_or(x0 + 90.0);
        let y0 = self.row_y.get(row as usize - 1).copied().unwrap_or(0.0);
        let y1 = self.row_y.get(row as usize).copied().unwrap_or(y0 + placed.row_h);
        Rect::from_min_max(
            placed.origin + vec2(ROW_HEADER_W + x0, placed.header_h + y0),
            placed.origin + vec2(ROW_HEADER_W + x1, placed.header_h + y1),
        )
    }

    /// The cell at a point on screen.
    fn cell_at(&self, placed: Placed, pos: Pos2, rows: i32) -> Option<(i32, i32)> {
        let local = pos - placed.origin;
        if local.x < ROW_HEADER_W || local.y < placed.header_h {
            return None;
        }
        let col = self.col_x.partition_point(|edge| *edge <= local.x - ROW_HEADER_W) as i32;
        let row = self.row_y.partition_point(|edge| *edge <= local.y - placed.header_h) as i32;
        (col >= 1 && col < self.col_x.len() as i32 && row >= 1 && row <= rows).then_some((row, col))
    }

    /// Whether a row is shown (not hidden, e.g. by a filter).
    pub(crate) fn row_shown(&self, row: i32) -> bool {
        let i = row as usize;
        i == 0 || i >= self.row_y.len() || self.row_y[i] > self.row_y[i - 1]
    }

    /// From `row`, the nearest shown row going `step` (+1 down, -1 up); `row` itself when none is.
    fn shown_row(&self, row: i32, step: i32) -> i32 {
        let mut r = row;
        while r >= 1 && (r as usize) < self.row_y.len() {
            if self.row_shown(r) {
                return r;
            }
            r += step;
        }
        row
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
        let (by_value, error) = match kind {
            Some(CellType::Number) => (Align2::RIGHT_CENTER, false),
            Some(CellType::ErrorValue) => (Align2::CENTER_CENTER, true),
            Some(CellType::LogicalValue) => (Align2::CENTER_CENTER, false),
            _ => (Align2::LEFT_CENTER, false),
        };
        let look = self.model.get_cell_style(sheet, row, col).map(|style| Look::of(&self.model, &style)).unwrap_or_default();
        let align = look.align.unwrap_or(by_value);
        let s = Shown { text, align, error, look, fitted: None };
        self.shown.insert((sheet, row, col), s.clone());
        s
    }

    fn move_to(&mut self, row: i32, col: i32, extend: bool) {
        // over hidden rows, in the direction of the move
        let row = if row != self.cursor.0 { self.shown_row(row, (row - self.cursor.0).signum()) } else { row };
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

    /// Writes what is being typed. False when the cell's validation refuses it: the editor stays open on the cell.
    fn commit(&mut self, out: &mut Output) -> bool {
        let Some(text) = self.editing.take() else { return true };
        let (sheet, (row, col)) = (self.sheet(), self.cursor);
        if self.read_only || text == self.content(sheet, row, col) {
            return true;
        }
        if let Err(why) = self.check(sheet, row, col, &text) {
            let said = format!("{}: {why}", cell_name(row, col));
            out.refused = Some(said.clone());
            self.refused = Some(said);
            self.editing = Some(text);
            self.focus_editor = true;
            return false;
        }
        self.refused = None;
        match self.set(sheet, row, col, &text) {
            Ok(()) => out.changed.push((sheet, row, col)),
            Err(e) => log::warn!("{}: {e}", cell_name(row, col)),
        }
        true
    }

    /// Every cell of a rectangle changed (up to 1000 rows by 100 columns are named).
    pub(crate) fn changed_cells(&mut self, range: (i32, i32, i32, i32), out: &mut Output) {
        let (r0, c0, r1, c1) = range;
        let sheet = self.sheet();
        for row in r0..=r1.min(r0 + 1000) {
            for col in c0..=c1.min(c0 + 100) {
                out.changed.push((sheet, row, col));
            }
        }
        self.version += 1;
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
        // IronCalc takes a range only around its selected cell: that first
        let _ = self.model.set_selected_cell(self.cursor.0, self.cursor.1);
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
                            let done = if key == Key::Z { self.undo() } else { self.redo() };
                            if done {
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

        if self.toolbar {
            self.tools(ui, &mut out);
            ui.separator();
        }
        self.formula_bar(ui, &mut out);
        ui.separator();
        let bottom_h = ui.spacing().interact_size.y + 12.0;
        let grid_h = (ui.available_height() - bottom_h).max(0.0);
        // a menu opened by an earlier click: shown before the grid takes this frame's clicks
        let menu_was_open = self.menu.is_some();
        ui.allocate_ui(vec2(ui.available_width(), grid_h), |ui| self.grid(ui, &mut out));
        self.sheets_and_status(ui);
        if menu_was_open {
            self.menu(ui, &mut out);
        }

        if ui.input(|i| i.pointer.any_pressed())
            && let Some(at) = ui.input(|i| i.pointer.interact_pos())
            && !whole.contains(at)
        {
            self.active = false;
        }
        if self.active && self.editing.is_none() && self.menu.is_none() && !std::mem::take(&mut self.edit_ended) {
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
                self.edit_ended = true;
            }
        });
    }

    fn grid(&mut self, ui: &mut Ui, out: &mut Output) {
        let (rows, cols) = self.rows_cols();
        let row_h = (ui.text_style_height(&egui::TextStyle::Body) + 8.0).round();
        self.layout(rows, cols, row_h);
        let header_h = row_h;
        let total = vec2(ROW_HEADER_W + self.col_x.last().copied().unwrap_or(0.0), header_h + self.row_y.last().copied().unwrap_or(0.0));
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
            let first_row = (self.row_y.partition_point(|e| *e <= view.min.y - header_h) as i32).clamp(1, rows);
            let last_row = (self.row_y.partition_point(|e| *e < view.max.y - header_h) as i32).clamp(1, rows);
            let x_from = (view.min.x - ROW_HEADER_W).max(0.0);
            let x_to = (view.max.x - ROW_HEADER_W).max(0.0);
            let first_col = (self.col_x.partition_point(|e| *e <= x_from) as i32).clamp(1, cols);
            let last_col = (self.col_x.partition_point(|e| *e < x_to) as i32).clamp(1, cols);

            // cells
            let (s_r0, s_c0, s_r1, s_c1) = self.selection();
            for row in first_row..=last_row {
                if !self.row_shown(row) {
                    continue;
                }
                for col in first_col..=last_col {
                    let cell = self.cell_rect(placed, row, col);
                    if cell.width() <= 0.0 {
                        continue;
                    }
                    let in_selection = row >= s_r0 && row <= s_r1 && col >= s_c0 && col <= s_c1;
                    if in_selection && (s_r0, s_c0) != (s_r1, s_c1) {
                        painter.rect_filled(cell, 0.0, visuals.selection.bg_fill.gamma_multiply(0.2));
                    }
                    let mut shown = self.shown(sheet, row, col);
                    // a table's look, under the cell's own (as in Excel, a cell's formatting wins)
                    if let Some((fill, text, bold)) = self.table_at(sheet, row, col).and_then(|t| t.look_at(row)) {
                        shown.look.fill = shown.look.fill.or(Some(fill));
                        shown.look.color = shown.look.color.or(text);
                        shown.look.bold |= bold;
                    }
                    if let Some(fill) = shown.look.fill {
                        painter.rect_filled(cell, 0.0, fill);
                    }
                    for (i, edge) in shown.look.borders.iter().enumerate() {
                        if let Some(c) = edge {
                            let (a, b) = match i {
                                0 => (cell.left_top(), cell.left_bottom()),
                                1 => (cell.left_top(), cell.right_top()),
                                2 => (cell.right_top(), cell.right_bottom()),
                                _ => (cell.left_bottom(), cell.right_bottom()),
                            };
                            painter.line_segment([a, b], Stroke::new(1.0, *c));
                        }
                    }
                    if (self.editing.is_some() && (row, col) == self.cursor) || shown.text.is_empty() {
                        continue;
                    }
                    let mut inner = cell.shrink2(vec2(4.0, 1.0));
                    // a filter's header cell keeps room for its button
                    if self.filters.get(&sheet).is_some_and(|f| f.header_row == row && f.covers(col)) {
                        inner.max.x -= 18.0;
                    }
                    let pos = shown.align.pos_in_rect(&inner);
                    let look = &shown.look;
                    let color = if shown.error {
                        visuals.error_fg_color
                    } else {
                        // the file's font colour; black text on a dark theme's background would not be seen
                        look.color.unwrap_or_else(|| if look.fill.is_some() { Color32::BLACK } else { visuals.text_color() })
                    };
                    let font = egui::FontId::new(font.size * look.scale, font.family.clone());
                    let text = match &shown.fitted {
                        Some((w, fitted)) if *w == inner.width() => fitted.clone(),
                        _ if shown.align == Align2::RIGHT_CENTER => {
                            let fitted = fit_number(&shown.text, inner.width(), |t| {
                                ui.fonts_mut(|f| f.layout_no_wrap(t.to_owned(), font.clone(), color).size().x)
                            });
                            if let Some(kept) = self.shown.get_mut(&(sheet, row, col)) {
                                kept.fitted = Some((inner.width(), fitted.clone()));
                            }
                            fitted
                        }
                        _ => shown.text.clone(),
                    };
                    let format = egui::TextFormat {
                        font_id: font.clone(),
                        color,
                        italics: look.italic,
                        underline: if look.underline { Stroke::new(1.0, color) } else { Stroke::NONE },
                        strikethrough: if look.strike { Stroke::new(1.0, color) } else { Stroke::NONE },
                        ..Default::default()
                    };
                    let mut job = egui::text::LayoutJob::single_section(text, format);
                    if look.wrap {
                        job.wrap.max_width = inner.width();
                    }
                    let galley = ui.fonts_mut(|f| f.layout_job(job));
                    let at = shown.align.anchor_size(pos, galley.size()).min;
                    let clipped = painter.with_clip_rect(cell.intersect(painter.clip_rect()));
                    // egui's fonts have one weight: bold is the text twice, half a point apart
                    if look.bold {
                        clipped.galley(at + vec2(0.6, 0.0), galley.clone(), color);
                    }
                    clipped.galley(at, galley, color);
                }
            }

            // the lines: once per visible row and column
            let left = rect.min.x + ROW_HEADER_W;
            let right = rect.min.x + total.x;
            for row in first_row..=last_row {
                if self.row_shown(row) {
                    let y = rect.min.y + header_h + self.row_y[row as usize];
                    painter.hline(left..=right, y, line);
                }
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
                if !self.row_shown(row) {
                    continue;
                }
                let y0 = rect.min.y + header_h + self.row_y[row as usize - 1];
                let row_h = self.row_y[row as usize] - self.row_y[row as usize - 1];
                let color = if row >= s_r0 && row <= s_r1 { visuals.selection.bg_fill } else { visuals.weak_text_color() };
                painter.text(pos2(side + ROW_HEADER_W - 6.0, y0 + row_h / 2.0), Align2::RIGHT_CENTER, row.to_string(), font.clone(), color);
                painter.hline(side..=side + ROW_HEADER_W, y0 + row_h, line);
            }
            painter.rect_filled(Rect::from_min_size(pos2(side, top), vec2(ROW_HEADER_W, header_h)), 0.0, header_fill);
            painter.hline(side..=rect.min.x + view.max.x, top + header_h, line);
            painter.vline(side + ROW_HEADER_W, top..=rect.min.y + view.max.y, line);

            // the filter's buttons, in its header row's cells
            if let Some(filter) = self.filters.get(&sheet).cloned()
                && filter.header_row >= first_row
                && filter.header_row <= last_row
                && self.row_shown(filter.header_row)
            {
                for col in first_col.max(filter.first_col)..=last_col.min(filter.last_col) {
                    let cell = self.cell_rect(placed, filter.header_row, col);
                    let button = Rect::from_min_size(pos2(cell.max.x - 18.0, cell.center().y - 8.0), vec2(16.0, 16.0)).intersect(cell);
                    let on = filter.conditions.contains_key(&col);
                    let hit = ui.interact(button, self.id.with(("filter", col)), Sense::click());
                    drop_button(&painter, button, on || hit.hovered(), &visuals);
                    if hit.clicked() {
                        let values = self.filter_values(sheet, col);
                        let ticked = match filter.conditions.get(&col) {
                            Some(crate::filter::Condition::Values(shown)) => shown.clone(),
                            _ => values.iter().cloned().collect(),
                        };
                        self.menu = Some(Menu::Filter { col, at: button.left_bottom(), values, ticked, search: String::new() });
                    }
                }
            }
            // a list validation's dropdown, on the cell with the cursor
            if self.editing.is_none()
                && !self.read_only
                && let Some(choices) = self.validation_at(sheet, self.cursor.0, self.cursor.1).and_then(|v| v.rule.choices()).map(<[String]>::to_vec)
            {
                let cell = self.cell_rect(placed, self.cursor.0, self.cursor.1);
                let button = Rect::from_min_size(pos2(cell.max.x + 1.0, cell.center().y - 8.0), vec2(16.0, 16.0));
                let hit = ui.interact(button, self.id.with("list"), Sense::click());
                drop_button(&painter, button, true, &visuals);
                if hit.clicked() {
                    self.menu = Some(Menu::List { at: cell.left_bottom(), choices });
                }
            }

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
            self.edit_ended = true;
            let (row, col) = self.cursor;
            match key {
                Key::Escape => self.editing = None,
                Key::Tab => {
                    if self.commit(out) {
                        self.move_to(row, col + 1, false);
                    }
                }
                _ => {
                    if self.commit(out) {
                        self.move_to(row + 1, col, false);
                    }
                }
            }
            self.active = true;
        }
        if let Some((at, extend, double)) = clicked
            && self.commit(out)
        {
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

    /// The menu open over the grid, if any: a filter's, or a list's choices.
    fn menu(&mut self, ui: &mut Ui, out: &mut Output) {
        let Some(menu) = self.menu.as_mut() else { return };
        let sheet = self.model.get_selected_sheet();
        let mut close = false;
        let mut apply: Option<Option<crate::filter::Condition>> = None;
        let mut chosen: Option<String> = None;
        let mut sort: Option<bool> = None;
        let at = match menu {
            Menu::Filter { at, .. } | Menu::List { at, .. } => *at,
        };
        let area = egui::Area::new(self.id.with("menu")).order(egui::Order::Foreground).fixed_pos(at).show(ui.ctx(), |ui| {
            egui::Frame::popup(ui.style()).show(ui, |ui| match menu {
                Menu::Filter { values, ticked, search, .. } => {
                    ui.set_width(220.0);
                    // as Excel's filter menu: sorting first
                    ui.horizontal(|ui| {
                        if ui.button("Sort A-Z").clicked() {
                            sort = Some(false);
                        }
                        if ui.button("Sort Z-A").clicked() {
                            sort = Some(true);
                        }
                    });
                    ui.separator();
                    ui.add(egui::TextEdit::singleline(search).hint_text("Search").desired_width(f32::INFINITY));
                    ui.horizontal(|ui| {
                        if ui.small_button("Select all").clicked() {
                            ticked.extend(values.iter().cloned());
                        }
                        if ui.small_button("None").clicked() {
                            ticked.clear();
                        }
                    });
                    let needle = search.to_lowercase();
                    egui::ScrollArea::vertical().max_height(260.0).show(ui, |ui| {
                        for value in values.iter().filter(|v| v.to_lowercase().contains(&needle)).take(1000) {
                            let mut on = ticked.contains(value);
                            let label = if value.is_empty() { "(blank)" } else { value.as_str() };
                            if ui.checkbox(&mut on, label).changed() {
                                if on {
                                    ticked.insert(value.clone());
                                } else {
                                    ticked.remove(value);
                                }
                            }
                        }
                    });
                    ui.separator();
                    ui.horizontal(|ui| {
                        if ui.button("OK").clicked() {
                            // everything ticked is no filter at all
                            apply = Some((ticked.len() < values.len()).then(|| crate::filter::Condition::Values(ticked.clone())));
                        }
                        if ui.button("Clear filter").clicked() {
                            apply = Some(None);
                        }
                        if ui.button("Cancel").clicked() {
                            close = true;
                        }
                    });
                }
                Menu::List { choices, .. } => {
                    ui.set_min_width(120.0);
                    for choice in choices.iter() {
                        if ui.selectable_label(false, choice).clicked() {
                            chosen = Some(choice.clone());
                        }
                    }
                }
            });
        });
        // a click outside it closes it
        if ui.input(|i| i.pointer.any_pressed())
            && let Some(p) = ui.input(|i| i.pointer.interact_pos())
            && !area.response.rect.contains(p)
            && !ui.ctx().is_being_dragged(self.id.with("menu"))
        {
            close = true;
        }
        if let Some(condition) = apply {
            if let Some(Menu::Filter { col, .. }) = &self.menu {
                let col = *col;
                let done = match condition {
                    Some(c) => self.filter(sheet, col, c),
                    None => self.clear_filter(sheet, col),
                };
                if let Err(e) = done {
                    log::warn!("filter: {e}");
                }
            }
            close = true;
        }
        if let (Some(descending), Some(Menu::Filter { col, .. })) = (sort, &self.menu) {
            let col = *col;
            let header = self.filters.get(&sheet).map_or(1, |f| f.header_row);
            if let Err(e) = self.sort_by(sheet, header, col, descending) {
                self.refused = Some(e);
            }
            close = true;
        }
        if let Some(choice) = chosen {
            let (row, col) = self.cursor;
            if self.set(sheet, row, col, &choice).is_ok() {
                out.changed.push((sheet, row, col));
                self.refused = None;
            }
            close = true;
        }
        if close {
            self.menu = None;
        }
    }
}

/// A number too wide for its cell, as Excel shows it: fewer decimals until it fits, and `###` when even the whole
/// number does not (a number cut short would be a different number).
fn fit_number(text: &str, width: f32, measure: impl Fn(&str) -> f32) -> String {
    if measure(text) <= width {
        return text.to_owned();
    }
    if let Some(dot) = text.find('.') {
        let digits = text.len() - dot - 1;
        let value: Option<f64> = text.parse().ok();
        for keep in (0..digits).rev() {
            let shorter = match value {
                Some(v) => format!("{v:.keep$}"),
                None => text[..dot + if keep == 0 { 0 } else { keep + 1 }].to_owned(),
            };
            if measure(&shorter) <= width {
                return shorter;
            }
        }
    }
    "#".repeat(((width / measure("#").max(1.0)) as usize).max(1))
}

/// A small dropdown button: a box with a triangle pointing down.
pub(crate) fn drop_button(painter: &egui::Painter, rect: Rect, strong: bool, visuals: &egui::Visuals) {
    let fill = if strong { visuals.selection.bg_fill } else { visuals.widgets.inactive.weak_bg_fill };
    let ink = if strong { visuals.selection.stroke.color } else { visuals.text_color() };
    painter.rect_filled(rect, 2.0, fill);
    let c = rect.center();
    painter.add(egui::Shape::convex_polygon(vec![pos2(c.x - 4.0, c.y - 2.0), pos2(c.x + 4.0, c.y - 2.0), pos2(c.x, c.y + 3.0)], ink, Stroke::NONE));
}

#[cfg(test)]
mod tests {
    use super::*;

    /// Each character is 10 wide.
    fn by_chars(t: &str) -> f32 {
        t.chars().count() as f32 * 10.0
    }

    #[test]
    fn a_number_that_fits_is_left_alone() {
        assert_eq!(fit_number("1726.17", 100.0, by_chars), "1726.17");
    }

    #[test]
    fn a_wide_number_loses_decimals_rounded_not_cut() {
        assert_eq!(fit_number("1726.166666667", 80.0, by_chars), "1726.167");
        assert_eq!(fit_number("1726.166666667", 40.0, by_chars), "1726");
        assert_eq!(fit_number("0.999", 30.0, by_chars), "1.0", "rounded, as Excel does");
    }

    #[test]
    fn a_number_that_cannot_fit_is_hashes() {
        assert_eq!(fit_number("123456789", 50.0, by_chars), "#####");
        assert_eq!(fit_number("1234.5", 1.0, by_chars), "#");
    }

    #[test]
    fn columns_are_named_as_excel_names_them() {
        let names: Vec<String> = [1, 2, 26, 27, 52, 53, 702, 703, 16_384].into_iter().map(column_name).collect();
        assert_eq!(names, ["A", "B", "Z", "AA", "AZ", "BA", "ZZ", "AAA", "XFD"]);
        assert_eq!(cell_name(10, 28), "AB10");
    }
}
