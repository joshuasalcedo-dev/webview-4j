//! The status bar, at the bottom: the sheets listed (click one to show it, `+` adds one), and the selection's
//! figures as Excel shows them when rows are highlighted: average, count, min, max and sum of its numbers. A value
//! refused by a validation (or a tool) is said here too, in red.

use egui::Ui;
use ironcalc::base::cell::CellValue;

use crate::Spreadsheet;

/// Past this many cells, the figures are of the first ones only (a whole-column selection is a million cells).
const MAX_STATS_CELLS: usize = 200_000;

/// The selection's figures.
#[derive(Debug, Clone, Copy, PartialEq, Default)]
pub struct Stats {
    /// Cells that are not empty.
    pub count: usize,
    /// Cells holding a number (dates are numbers too).
    pub numbers: usize,
    pub sum: f64,
    pub min: f64,
    pub max: f64,
}

impl Stats {
    pub fn average(&self) -> Option<f64> {
        (self.numbers > 0).then(|| self.sum / self.numbers as f64)
    }
}

impl Spreadsheet {
    pub(crate) fn sheets_and_status(&mut self, ui: &mut Ui) {
        ui.separator();
        ui.horizontal(|ui| {
            let current = self.sheet();
            for (i, name) in self.sheet_names().into_iter().enumerate() {
                if ui.selectable_label(i as u32 == current, name).clicked() && i as u32 != current {
                    self.select_sheet(i as u32);
                }
            }
            if !self.read_only
                && ui.small_button("+").on_hover_text("Add a sheet").clicked()
                && let Err(e) = self.add_sheet()
            {
                self.refused = Some(e);
            }
            ui.with_layout(egui::Layout::right_to_left(egui::Align::Center), |ui| {
                ui.weak(self.stats());
                if let Some(why) = &self.refused {
                    ui.colored_label(ui.visuals().error_fg_color, why);
                }
            });
        });
    }

    /// Adds a sheet after the others (Sheet2, Sheet3...) and shows it. Its index is returned.
    pub fn add_sheet(&mut self) -> Result<u32, String> {
        self.model.new_sheet()?;
        let index = self.sheet_names().len() as u32 - 1;
        self.select_sheet(index);
        self.changed();
        Ok(index)
    }

    /// The selection's figures (None for a single cell, as Excel shows none then).
    pub fn selection_stats(&self) -> Option<Stats> {
        let (r0, c0, r1, c1) = self.selection();
        if (r0, c0) == (r1, c1) {
            return None;
        }
        let sheet = self.sheet();
        let model = self.model.get_model();
        let mut stats = Stats { min: f64::INFINITY, max: f64::NEG_INFINITY, ..Stats::default() };
        let mut seen = 0usize;
        'all: for row in r0..=r1 {
            // a hidden row (filtered out) does not count, as in Excel
            if !self.row_shown(row) {
                continue;
            }
            for col in c0..=c1 {
                seen += 1;
                if seen > MAX_STATS_CELLS {
                    break 'all;
                }
                match model.get_cell_value_by_index(sheet, row, col) {
                    Ok(CellValue::Number(n)) => {
                        stats.count += 1;
                        stats.numbers += 1;
                        stats.sum += n;
                        stats.min = stats.min.min(n);
                        stats.max = stats.max.max(n);
                    }
                    Ok(CellValue::None) | Err(_) => {}
                    Ok(_) => stats.count += 1,
                }
            }
        }
        if stats.numbers == 0 {
            (stats.min, stats.max) = (0.0, 0.0);
        }
        Some(stats)
    }

    /// The selection's figures as the status bar says them: `Average 3   Count 5   Min 1   Max 5   Sum 15`.
    pub fn stats(&self) -> String {
        let Some(stats) = self.selection_stats() else { return String::new() };
        let Some(average) = stats.average() else { return format!("Count {}", stats.count) };
        let fmt = |n: f64| {
            if n.fract() == 0.0 && n.abs() < 1e15 {
                format!("{n:.0}")
            } else {
                format!("{n:.4}").trim_end_matches('0').trim_end_matches('.').to_owned()
            }
        };
        format!(
            "Average {}   Count {}   Min {}   Max {}   Sum {}",
            fmt(average),
            stats.count,
            fmt(stats.min),
            fmt(stats.max),
            fmt(stats.sum)
        )
    }
}
