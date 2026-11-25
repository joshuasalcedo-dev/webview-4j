//! Columns: width, hidden or shown, insert, delete, and autofit (as wide as their text). Saved with the workbook.
//! Columns are 1-based (1 is A).

use crate::Spreadsheet;

/// The narrowest and widest an autofit makes a column, in points.
const AUTOFIT_MIN: f64 = 40.0;
const AUTOFIT_MAX: f64 = 480.0;
/// An autofit reads at most this many rows (a column of a million is sized by its first ones).
const AUTOFIT_ROWS: i32 = 5000;

impl Spreadsheet {
    /// A column's width, in points.
    pub fn column_width(&self, sheet: u32, col: i32) -> f64 {
        self.model.get_column_width(sheet, col).unwrap_or(90.0)
    }

    /// Sets the width of columns `from..=to`, in points.
    pub fn set_column_width(&mut self, sheet: u32, from: i32, to: i32, width: f64) -> Result<(), String> {
        if !(width.is_finite() && width > 0.0) {
            return Err(format!("a width must be more than 0, not {width}"));
        }
        self.model.set_columns_width(sheet, from.min(to), from.max(to), width)?;
        self.changed();
        Ok(())
    }

    /// Hides (or shows again) columns `from..=to`.
    pub fn hide_columns(&mut self, sheet: u32, from: i32, to: i32, hidden: bool) -> Result<(), String> {
        self.model.set_columns_hidden(sheet, from.min(to), from.max(to), hidden)?;
        self.changed();
        Ok(())
    }

    pub fn is_column_hidden(&self, sheet: u32, col: i32) -> bool {
        self.model.get_model().workbook.worksheet(sheet).ok().and_then(|w| w.is_column_hidden(col).ok()).unwrap_or(false)
    }

    /// Inserts `count` empty columns before `at` (formulas that read past it are moved, as in Excel).
    pub fn insert_columns(&mut self, sheet: u32, at: i32, count: i32) -> Result<(), String> {
        self.model.insert_columns(sheet, at, count)?;
        self.changed();
        Ok(())
    }

    /// Deletes `count` columns from `at`.
    pub fn delete_columns(&mut self, sheet: u32, at: i32, count: i32) -> Result<(), String> {
        self.model.delete_columns(sheet, at, count)?;
        self.changed();
        Ok(())
    }

    /// Makes a column as wide as its widest text (of its first 5000 rows), within limits.
    pub fn autofit_column(&mut self, sheet: u32, col: i32) -> Result<(), String> {
        let last = self.model.get_model().workbook.worksheet(sheet).map(|w| w.dimension().max_row).unwrap_or(1).min(AUTOFIT_ROWS);
        let widest = (1..=last).map(|row| self.text(sheet, row, col).chars().count()).max().unwrap_or(0);
        let width = (widest as f64 * 7.5 + 18.0).clamp(AUTOFIT_MIN, AUTOFIT_MAX);
        self.set_column_width(sheet, col, col, width)
    }
}
