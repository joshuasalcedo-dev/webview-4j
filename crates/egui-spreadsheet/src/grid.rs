//! The spreadsheet on screen: a formula bar, the grid (only the rows in view are drawn), and a status line.
//!
//! Click a cell to choose it, Shift (click or arrows) to choose a rectangle. Type, Enter, F2 or a double-click
//! edits; Enter commits and goes down, Tab right, Escape gives up. Delete clears. Ctrl+C, Ctrl+X, Ctrl+V copy, cut
//! and paste, as tab-separated text (what other spreadsheets read); a formula pasted elsewhere has its references
//! moved by the same distance, as in every spreadsheet.

use std::sync::atomic::{AtomicU64, Ordering};

use egui::{Align, Key, Layout, RichText, Sense, Ui, Vec2};
use egui_extras::{Column, TableBuilder};

use crate::eval::{self, Compiled, Values};
use crate::formula::{self, Value};
use crate::sheet::{COLS, CellRef, Msg, ROWS, Range, Sheet, reduce};

/// What happened in one frame: for the host (to save, to tell Java).
#[derive(Debug, Default, Clone, PartialEq)]
pub struct Output {
    /// Cells whose text changed, with their new text ("" for a cleared cell), in one step.
    pub changed: Vec<(CellRef, String)>,
    /// The chosen rectangle, when it changed.
    pub selected: Option<Range>,
}

/// What a copy left: where it came from, so a paste can move its formulas' references by the same distance.
#[derive(Clone)]
struct Copied {
    from: CellRef,
    text: String,
}

pub struct Spreadsheet {
    id: egui::Id,
    sheet: Sheet,
    compiled: Compiled,
    compiled_rev: Option<u64>,
    values: Values,
    values_rev: Option<u64>,
    anchor: CellRef,
    cursor: CellRef,
    /// The text being typed into the chosen cell (in it, or in the formula bar), until committed.
    editing: Option<String>,
    /// The editor has just opened: it takes the keyboard on its first frame.
    focus_editor: bool,
    /// The grid has the keyboard (a cell was clicked, and nothing outside since).
    active: bool,
    scroll_to: Option<u32>,
    copied: Option<Copied>,
    /// Nothing can be changed: no editing, no clearing, no paste (choosing and copying still work).
    pub read_only: bool,
}

impl Default for Spreadsheet {
    fn default() -> Self {
        Self::new(Sheet::empty())
    }
}

/// How a number is shown: whole numbers as they are, others with up to 10 significant digits, no trailing zeros.
pub fn show_number(n: f64) -> String {
    if n.fract() == 0.0 && n.abs() < 1e15 {
        return format!("{n:.0}");
    }
    let digits = (10 - n.abs().log10().floor() as i32 - 1).clamp(0, 10) as usize;
    let s = format!("{n:.digits$}");
    if s.contains('.') { s.trim_end_matches('0').trim_end_matches('.').to_owned() } else { s }
}

/// What a cell shows for its value.
pub fn show_value(value: &Value) -> String {
    match value {
        Value::Empty => String::new(),
        Value::Num(n) => show_number(*n),
        Value::Text(t) => t.clone(),
        Value::Err(e) => e.label().to_owned(),
    }
}

fn col_name(col: u16) -> String {
    char::from(b'A' + col as u8).to_string()
}

impl Spreadsheet {
    pub fn new(sheet: Sheet) -> Self {
        static NEXT: AtomicU64 = AtomicU64::new(0);
        let a1 = CellRef::new(0, 0);
        Spreadsheet {
            id: egui::Id::unique(("egui-spreadsheet", NEXT.fetch_add(1, Ordering::Relaxed))),
            sheet,
            compiled: Compiled::default(),
            compiled_rev: None,
            values: Values::default(),
            values_rev: None,
            anchor: a1,
            cursor: a1,
            editing: None,
            focus_editor: false,
            active: false,
            scroll_to: None,
            copied: None,
            read_only: false,
        }
    }

    pub fn sheet(&self) -> &Sheet {
        &self.sheet
    }

    /// Changes the sheet (the engine's messages: set cells, clear, widths, load another).
    pub fn apply(&mut self, msg: Msg) {
        reduce(&mut self.sheet, msg);
    }

    /// What a cell says now.
    pub fn value(&mut self, at: CellRef) -> Value {
        self.refresh();
        self.values.get(at).clone()
    }

    /// The chosen rectangle.
    pub fn selection(&self) -> Range {
        Range::new(self.anchor, self.cursor)
    }

    /// Chooses a cell (and shows it).
    pub fn select(&mut self, at: CellRef) {
        self.anchor = at;
        self.cursor = at;
        self.editing = None;
        self.scroll_to = Some(at.row);
    }

    /// Parses and evaluates again, only what changed: a formula typed parses the sheet again, a number only
    /// evaluates it again (the engine's two revisions).
    fn refresh(&mut self) {
        if self.compiled_rev != Some(self.sheet.structure_rev) {
            self.compiled = eval::compile(&self.sheet);
            self.compiled_rev = Some(self.sheet.structure_rev);
            self.values_rev = None;
        }
        if self.values_rev != Some(self.sheet.value_rev) {
            self.values = eval::evaluate(&self.sheet, &self.compiled);
            self.values_rev = Some(self.sheet.value_rev);
        }
    }

    fn set(&mut self, changes: Vec<(CellRef, String)>, out: &mut Output) {
        let before = self.sheet.value_rev;
        let changed: Vec<(CellRef, String)> = changes.into_iter().filter(|(at, text)| self.sheet.text(*at) != text).collect();
        self.apply(Msg::SetMany(changed.clone()));
        if self.sheet.value_rev != before {
            out.changed.extend(changed);
        }
    }

    fn commit(&mut self, out: &mut Output) {
        if let Some(text) = self.editing.take()
            && !self.read_only
        {
            self.set(vec![(self.cursor, text)], out);
        }
    }

    fn start_editing(&mut self, text: String) {
        if self.read_only {
            return;
        }
        self.editing = Some(text);
        self.focus_editor = true;
    }

    fn move_to(&mut self, col: i32, row: i32, extend: bool) {
        let at = CellRef::new(col.clamp(0, i32::from(COLS) - 1) as u16, row.clamp(0, ROWS as i32 - 1) as u32);
        self.cursor = at;
        if !extend {
            self.anchor = at;
        }
        self.scroll_to = Some(at.row);
    }

    /// The chosen rectangle as tab-separated text: what every spreadsheet copies and reads.
    fn copy_text(&self) -> String {
        let range = self.selection();
        (range.from.row..=range.to.row)
            .map(|row| (range.from.col..=range.to.col).map(|col| self.sheet.text(CellRef::new(col, row))).collect::<Vec<_>>().join("\t"))
            .collect::<Vec<_>>()
            .join("\n")
    }

    fn paste(&mut self, text: &str, out: &mut Output) {
        if self.read_only {
            return;
        }
        // copied here: formulas move with the paste; from elsewhere: as it is
        let shift = self
            .copied
            .as_ref()
            .filter(|c| c.text == text)
            .map(|c| (i32::from(self.cursor.col) - i32::from(c.from.col), self.cursor.row as i32 - c.from.row as i32));
        let mut changes = Vec::new();
        for (dr, line) in text.lines().enumerate() {
            for (dc, cell) in line.split('\t').enumerate() {
                let (col, row) = (i32::from(self.cursor.col) + dc as i32, self.cursor.row as i32 + dr as i32);
                if col >= i32::from(COLS) || row >= ROWS as i32 {
                    continue;
                }
                let moved = match shift {
                    Some((x, y)) => formula::shift(cell, x, y),
                    None => cell.to_owned(),
                };
                changes.push((CellRef::new(col as u16, row as u32), moved));
            }
        }
        self.set(changes, out);
    }

    /// The keyboard, while the grid has it and no cell is being edited.
    fn keys(&mut self, ui: &Ui, out: &mut Output) {
        let events = ui.input(|i| i.events.clone());
        let shift = ui.input(|i| i.modifiers.shift);
        let (col, row) = (i32::from(self.cursor.col), self.cursor.row as i32);
        for event in events {
            match event {
                egui::Event::Key { key, pressed: true, modifiers, .. } => match key {
                    Key::ArrowUp => self.move_to(col, row - 1, shift),
                    Key::ArrowDown => self.move_to(col, row + 1, shift),
                    Key::ArrowLeft => self.move_to(col - 1, row, shift),
                    Key::ArrowRight => self.move_to(col + 1, row, shift),
                    Key::Tab => self.move_to(col + if modifiers.shift { -1 } else { 1 }, row, false),
                    Key::PageDown => self.move_to(col, row + 20, shift),
                    Key::PageUp => self.move_to(col, row - 20, shift),
                    Key::Enter | Key::F2 => self.start_editing(self.sheet.text(self.cursor).to_owned()),
                    Key::Delete | Key::Backspace if !self.read_only => {
                        let range = self.selection();
                        let cleared: Vec<(CellRef, String)> =
                            self.sheet.cells.keys().filter(|at| range.contains(**at)).map(|at| (*at, String::new())).collect();
                        self.set(cleared, out);
                    }
                    _ => {}
                },
                egui::Event::Text(text) if !text.is_empty() => self.start_editing(text),
                egui::Event::Copy | egui::Event::Cut => {
                    let text = self.copy_text();
                    ui.ctx().copy_text(text.clone());
                    self.copied = Some(Copied { from: self.selection().from, text });
                    if matches!(event, egui::Event::Cut) && !self.read_only {
                        let range = self.selection();
                        let cleared: Vec<(CellRef, String)> =
                            self.sheet.cells.keys().filter(|at| range.contains(**at)).map(|at| (*at, String::new())).collect();
                        self.set(cleared, out);
                    }
                }
                egui::Event::Paste(text) => self.paste(&text, out),
                _ => {}
            }
        }
    }

    pub fn show(&mut self, ui: &mut Ui) -> Output {
        self.refresh();
        let mut out = Output::default();
        let before = self.selection();
        let top = ui.max_rect();

        self.formula_bar(ui, &mut out);
        ui.separator();
        self.status_line(ui);
        self.grid(ui, &mut out);

        // the keyboard is the grid's from a click in a cell until a click outside it
        if ui.input(|i| i.pointer.any_pressed())
            && let Some(at) = ui.input(|i| i.pointer.interact_pos())
            && !top.contains(at)
        {
            self.active = false;
        }
        if self.active && self.editing.is_none() {
            self.keys(ui, &mut out);
            if self.editing.is_some() {
                ui.ctx().request_repaint();
            }
        }
        if self.selection() != before {
            out.selected = Some(self.selection());
        }
        if !out.changed.is_empty() {
            self.refresh();
        }
        out
    }

    fn formula_bar(&mut self, ui: &mut Ui, out: &mut Output) {
        ui.horizontal(|ui| {
            let range = self.selection();
            let name = if range.from == range.to { range.from.name() } else { format!("{}:{}", range.from.name(), range.to.name()) };
            ui.add_sized([72.0, ui.spacing().interact_size.y], egui::Label::new(RichText::new(name).monospace().strong()));
            let mut text = self.editing.clone().unwrap_or_else(|| self.sheet.text(self.cursor).to_owned());
            let edit = egui::TextEdit::singleline(&mut text)
                .id(self.id.with("bar"))
                .hint_text("Value or =formula")
                .desired_width(f32::INFINITY)
                .interactive(!self.read_only);
            let response = ui.add(edit);
            if response.changed() {
                self.editing = Some(text);
            }
            if response.lost_focus() && self.editing.is_some() {
                if ui.input(|i| i.key_pressed(Key::Escape)) {
                    self.editing = None;
                } else {
                    self.commit(out);
                    self.active = true;
                }
            }
        });
    }

    fn status_line(&mut self, ui: &mut Ui) {
        let deps = self.compiled.deps.get(&self.cursor).filter(|d| !d.is_empty());
        let text = match deps {
            Some(d) if d.len() <= 8 => format!("{} reads {}", self.cursor.name(), d.iter().map(|c| c.name()).collect::<Vec<_>>().join(", ")),
            Some(d) => format!("{} reads {} cells", self.cursor.name(), d.len()),
            None => format!("{} cells filled", self.sheet.len()),
        };
        ui.weak(text);
    }

    fn grid(&mut self, ui: &mut Ui, out: &mut Output) {
        let row_h = ui.text_style_height(&egui::TextStyle::Body) + 6.0;
        let visuals = ui.visuals().clone();
        let grid_line = egui::Stroke::new(1.0, visuals.widgets.noninteractive.bg_stroke.color);
        let mut table = TableBuilder::new(ui)
            .id_salt(self.id.with("grid"))
            .striped(false)
            .cell_layout(Layout::left_to_right(Align::Center))
            .auto_shrink([false, false])
            .animate_scrolling(false)
            .column(Column::exact(48.0));
        for col in 0..COLS {
            table = table.column(Column::exact(self.sheet.width(col)));
        }
        if let Some(row) = self.scroll_to.take() {
            table = table.scroll_to_row(row as usize, None);
        }
        let selection = self.selection();
        let mut clicked: Option<(CellRef, bool, bool)> = None;
        let mut edit_done: Option<Key> = None;

        table
            .header(row_h, |mut header| {
                header.col(|_| {});
                for col in 0..COLS {
                    header.col(|ui| {
                        ui.centered_and_justified(|ui| {
                            ui.add(egui::Label::new(RichText::new(col_name(col)).strong()).selectable(false));
                        });
                    });
                }
            })
            .body(|body| {
                body.rows(row_h, ROWS as usize, |mut row| {
                    let r = row.index() as u32;
                    row.col(|ui| {
                        ui.with_layout(Layout::right_to_left(Align::Center), |ui| {
                            ui.add(egui::Label::new(RichText::new((r + 1).to_string()).weak()).selectable(false));
                        });
                    });
                    for c in 0..COLS {
                        let at = CellRef::new(c, r);
                        row.col(|ui| {
                            let rect = ui.max_rect();
                            if at == self.cursor
                                && !self.read_only
                                && let Some(text) = self.editing.as_mut()
                            {
                                let edit = egui::TextEdit::singleline(text)
                                    .id(self.id.with("cell"))
                                    .frame(egui::Frame::NONE)
                                    .desired_width(rect.width());
                                let response = ui.add(edit);
                                if std::mem::take(&mut self.focus_editor) {
                                    response.request_focus();
                                }
                                if response.lost_focus() {
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
                                return;
                            }
                            let value = self.values.get(at);
                            let shown = show_value(value);
                            let response = ui.allocate_rect(rect, Sense::click());
                            response.widget_info(|| {
                                egui::WidgetInfo::labeled(egui::Role::Cell, true, format!("{} {}", at.name(), shown).trim_end())
                            });
                            let painter = ui.painter_at(rect);
                            // the grid's lines: each cell draws its right and bottom edge, faintly
                            painter.line_segment([rect.right_top(), rect.right_bottom()], grid_line);
                            painter.line_segment([rect.left_bottom(), rect.right_bottom()], grid_line);
                            if selection.contains(at) && selection.from != selection.to {
                                painter.rect_filled(rect, 0.0, visuals.selection.bg_fill.gamma_multiply(0.25));
                            }
                            if at == self.cursor {
                                painter.rect_stroke(rect.shrink(1.0), 0.0, egui::Stroke::new(2.0, visuals.selection.bg_fill), egui::StrokeKind::Inside);
                            }
                            let (align, color) = match value {
                                Value::Num(_) => (egui::Align2::RIGHT_CENTER, visuals.text_color()),
                                Value::Err(_) => (egui::Align2::CENTER_CENTER, visuals.error_fg_color),
                                _ => (egui::Align2::LEFT_CENTER, visuals.text_color()),
                            };
                            let pos = match align {
                                egui::Align2::RIGHT_CENTER => rect.right_center() - Vec2::new(4.0, 0.0),
                                egui::Align2::CENTER_CENTER => rect.center(),
                                _ => rect.left_center() + Vec2::new(4.0, 0.0),
                            };
                            painter.text(pos, align, shown, egui::TextStyle::Body.resolve(ui.style()), color);
                            if response.clicked() || response.double_clicked() {
                                clicked = Some((at, ui.input(|i| i.modifiers.shift), response.double_clicked()));
                            }
                        });
                    }
                });
            });

        if let Some(key) = edit_done {
            match key {
                Key::Escape => self.editing = None,
                Key::Tab => {
                    self.commit(out);
                    self.move_to(i32::from(self.cursor.col) + 1, self.cursor.row as i32, false);
                }
                _ => {
                    self.commit(out);
                    self.move_to(i32::from(self.cursor.col), self.cursor.row as i32 + 1, false);
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
                self.start_editing(self.sheet.text(at).to_owned());
            }
        }
    }
}
