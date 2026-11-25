//! Rows: height, hidden or shown, insert, delete. Saved with the workbook. Rows are 1-based. (A filter hides rows
//! too: see [`crate::filter`].)

use crate::Spreadsheet;

impl Spreadsheet {
    /// A row's height, in points.
    pub fn row_height(&self, sheet: u32, row: i32) -> f64 {
        self.model.get_row_height(sheet, row).unwrap_or(25.0)
    }

    /// Sets the height of rows `from..=to`, in points.
    pub fn set_row_height(&mut self, sheet: u32, from: i32, to: i32, height: f64) -> Result<(), String> {
        if !(height.is_finite() && height > 0.0) {
            return Err(format!("a height must be more than 0, not {height}"));
        }
        self.model.set_rows_height(sheet, from.min(to), from.max(to), height)?;
        self.changed();
        Ok(())
    }

    /// Hides (or shows again) rows `from..=to`.
    pub fn hide_rows(&mut self, sheet: u32, from: i32, to: i32, hidden: bool) -> Result<(), String> {
        self.model.set_rows_hidden(sheet, from.min(to), from.max(to), hidden)?;
        self.changed();
        Ok(())
    }

    pub fn is_row_hidden(&self, sheet: u32, row: i32) -> bool {
        self.model.get_model().workbook.worksheet(sheet).ok().and_then(|w| w.is_row_hidden(row).ok()).unwrap_or(false)
    }

    /// Inserts `count` empty rows before `at` (formulas that read past it are moved, as in Excel).
    pub fn insert_rows(&mut self, sheet: u32, at: i32, count: i32) -> Result<(), String> {
        self.model.insert_rows(sheet, at, count)?;
        self.changed();
        Ok(())
    }

    /// Deletes `count` rows from `at`.
    pub fn delete_rows(&mut self, sheet: u32, at: i32, count: i32) -> Result<(), String> {
        self.model.delete_rows(sheet, at, count)?;
        self.changed();
        Ok(())
    }
}
