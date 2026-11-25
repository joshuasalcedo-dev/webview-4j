//! THE toolbar: where all the tools are, at the top. It acts on the selection: styles (font, colours, alignment,
//! wrap, number formats), rows and columns, sorting, filters and tables, undo and redo. Its toggles show the chosen cell's
//! style. Labels are words, not icons (egui's own font has no icons, and a box for a missing one helps nobody).

use egui::{RichText, Ui};

use crate::Output;
use crate::Spreadsheet;
use crate::style::{Align, CellStyle};
use crate::table::Table;

/// A font toggle: its label, its name, whether the chosen cell has it, and the style that sets it on or off.
type Toggle = (&'static str, &'static str, bool, fn(CellStyle, bool) -> CellStyle);

/// Number formats the toolbar offers: its label, Excel's format.
const FORMATS: [(&str, &str); 5] = [("General", "general"), ("0.00", "0.00"), ("Money", "$#,##0.00"), ("%", "0%"), ("Date", "yyyy-mm-dd")];

impl Spreadsheet {
    /// The toolbar (shown by [`Spreadsheet::show`] when [`Spreadsheet::toolbar`] is on).
    pub(crate) fn tools(&mut self, ui: &mut Ui, out: &mut Output) {
        let sheet = self.sheet();
        let (r0, c0, r1, c1) = self.selection();
        let range = (r0, c0, r1, c1);
        let (row, col) = self.cursor();
        let style = self.model.get_cell_style(sheet, row, col).ok();
        let editable = !self.read_only;
        let mut apply: Option<CellStyle> = None;
        let mut did: Result<(), String> = Ok(());

        ui.horizontal_wrapped(|ui| {
            ui.add_enabled_ui(editable, |ui| {
                if ui.add_enabled(self.model.can_undo(), egui::Button::new("Undo")).on_hover_text("Ctrl+Z").clicked() {
                    self.undo();
                }
                if ui.add_enabled(self.model.can_redo(), egui::Button::new("Redo")).on_hover_text("Ctrl+Y").clicked() {
                    self.redo();
                }
                ui.separator();

                // font: each toggle turns its style on, or off when the chosen cell has it
                let font = style.as_ref().map(|s| (s.font.b, s.font.i, s.font.u, s.font.strike)).unwrap_or_default();
                let toggles: [Toggle; 4] = [
                    ("B", "Bold", font.0, |s, on| CellStyle { bold: Some(on), ..s }),
                    ("I", "Italic", font.1, |s, on| CellStyle { italic: Some(on), ..s }),
                    ("U", "Underline", font.2, |s, on| CellStyle { underline: Some(on), ..s }),
                    ("S", "Strikethrough", font.3, |s, on| CellStyle { strike: Some(on), ..s }),
                ];
                for (label, name, on, set) in toggles {
                    let text = match label {
                        "B" => RichText::new(label).strong(),
                        "I" => RichText::new(label).italics(),
                        "U" => RichText::new(label).underline(),
                        _ => RichText::new(label).strikethrough(),
                    };
                    if ui.selectable_label(on, text).on_hover_text(name).clicked() {
                        apply = Some(set(CellStyle::new(), !on));
                    }
                }
                ui.label("Text");
                if ui.color_edit_button_srgb(&mut self.tool_font).changed() {
                    apply = Some(CellStyle::new().font_color(&hex(self.tool_font)));
                }
                ui.label("Fill");
                if ui.color_edit_button_srgb(&mut self.tool_fill).changed() {
                    apply = Some(CellStyle::new().fill(&hex(self.tool_fill)));
                }
                ui.separator();

                for (label, align) in [("Left", Align::Left), ("Center", Align::Center), ("Right", Align::Right)] {
                    if ui.button(label).on_hover_text(format!("Align {}", label.to_lowercase())).clicked() {
                        apply = Some(CellStyle::new().align(align));
                    }
                }
                let wrapped = style.as_ref().and_then(|s| s.alignment.as_ref()).is_some_and(|a| a.wrap_text);
                if ui.selectable_label(wrapped, "Wrap").on_hover_text("Wrap the text in its cells").clicked() {
                    apply = Some(CellStyle { wrap: Some(!wrapped), ..CellStyle::new() });
                }
                ui.separator();

                let current = style.as_ref().map(|s| s.num_fmt.clone()).unwrap_or_default();
                for (label, format) in FORMATS {
                    let on = current.eq_ignore_ascii_case(format);
                    if ui.selectable_label(on, label).on_hover_text(format!("Number format: {format}")).clicked() {
                        apply = Some(CellStyle::new().number_format(format));
                    }
                }
                ui.separator();

                if ui.button("+ Row").on_hover_text("Insert a row above").clicked() {
                    did = self.insert_rows(sheet, r0, 1);
                }
                if ui.button("- Row").on_hover_text("Delete the selected rows").clicked() {
                    did = self.delete_rows(sheet, r0, r1 - r0 + 1);
                }
                if ui.button("+ Col").on_hover_text("Insert a column to the left").clicked() {
                    did = self.insert_columns(sheet, c0, 1);
                }
                if ui.button("- Col").on_hover_text("Delete the selected columns").clicked() {
                    did = self.delete_columns(sheet, c0, c1 - c0 + 1);
                }
                if ui.button("Hide rows").on_hover_text("Hide the selected rows").clicked() {
                    did = self.hide_rows(sheet, r0, r1, true);
                }
                if ui.button("Fit").on_hover_text("Size the selected columns to their text").clicked() {
                    did = (c0..=c1).try_for_each(|c| self.autofit_column(sheet, c));
                }
                ui.separator();
            });

            // sorting: by the cursor's column, over its table, its filter's data, or the selected rows
            let sortable = editable && self.sort_range(sheet, row, col).is_some();
            let tip = "Sort by this column: its table, its filter's rows, or the selected rows";
            if ui.add_enabled(sortable, egui::Button::new("Sort A-Z")).on_hover_text(tip).on_disabled_hover_text(tip).clicked() {
                did = self.sort_by(sheet, row, col, false);
            }
            if ui.add_enabled(sortable, egui::Button::new("Sort Z-A")).on_hover_text(tip).on_disabled_hover_text(tip).clicked() {
                did = self.sort_by(sheet, row, col, true);
            }
            ui.separator();

            // filters and tables (choosing what to see is fine when read-only too)
            let filtered = self.filter_range(sheet).is_some_and(|f| !f.conditions.is_empty());
            if ui.button("Filter").on_hover_text("The selection's first row becomes a header with filter buttons").clicked() {
                did = self.set_filter_range(sheet, r0, c0, c1.max(c0));
            }
            if ui.add_enabled(filtered, egui::Button::new("Clear filters")).clicked() {
                did = self.clear_filters(sheet);
            }
            if ui.add_enabled(editable, egui::Button::new("Table")).on_hover_text("Format the selection as a table").clicked() {
                let name = (1..).map(|n| format!("Table{n}")).find(|n| self.table(n).is_none()).expect("a free name");
                did = self.add_table(Table::new(&name, sheet, range));
            }
        });

        if let Some(style) = apply {
            did = self.set_style(sheet, range, &style);
            if did.is_ok() {
                self.changed_cells(range, out);
            }
        }
        if let Err(why) = did {
            self.refused = Some(why);
        }
    }
}

fn hex(c: [u8; 3]) -> String {
    format!("#{:02X}{:02X}{:02X}", c[0], c[1], c[2])
}

/// The toolbar's starting colours: dark text, a pale yellow fill (Excel's highlight).
pub(crate) const START_FONT: [u8; 3] = [0x1F, 0x1F, 0x1F];
pub(crate) const START_FILL: [u8; 3] = [0xFF, 0xF2, 0xCC];
