//! Filters, as Excel's AutoFilter: a range with a header row (its columns get a filter button in the grid), and per
//! column a condition. A row whose cells do not all meet their column's condition is hidden (hiding is saved with
//! the workbook, as Excel saves it); clearing a filter shows the rows again.
//!
//! Sorting too, as Excel's Sort & Filter: a range's rows ordered by one of its columns (numbers, then text, then
//! true/false; blanks last both ways). Rows move whole within the range: their values, formulas and
//! styles. As one paste, so one undo; a range with formulas is pasted row by row (each formula's references move
//! with its row), one undo each.

use std::cmp::Ordering;
use std::collections::{BTreeMap, BTreeSet};

use ironcalc::base::cell::CellValue;

use crate::Spreadsheet;

/// What a column's cells must be for their row to show.
#[derive(Debug, Clone, PartialEq)]
pub enum Condition {
    /// One of these (as shown in the cell, e.g. `paid`; `""` is an empty cell).
    Values(BTreeSet<String>),
    /// Contains this text, ignoring case.
    Contains(String),
    /// Exactly this text, ignoring case.
    Equals(String),
    GreaterThan(f64),
    LessThan(f64),
    /// Between the two, both included.
    Between(f64, f64),
    Blank,
    NotBlank,
}

impl Condition {
    /// Whether a cell (its shown text, and its number when it is one) meets it.
    pub fn matches(&self, text: &str, number: Option<f64>) -> bool {
        match self {
            Condition::Values(shown) => shown.contains(text),
            Condition::Contains(t) => text.to_lowercase().contains(&t.to_lowercase()),
            Condition::Equals(t) => text.eq_ignore_ascii_case(t),
            Condition::GreaterThan(n) => number.is_some_and(|v| v > *n),
            Condition::LessThan(n) => number.is_some_and(|v| v < *n),
            Condition::Between(a, b) => number.is_some_and(|v| v >= a.min(*b) && v <= a.max(*b)),
            Condition::Blank => text.is_empty(),
            Condition::NotBlank => !text.is_empty(),
        }
    }
}

/// A filtered range of one sheet: its header row, its columns, and the conditions on them. Its data are the rows
/// under the header down to `last_row` (a table's last); without one, down to the first row empty in all its
/// columns (as Excel's AutoFilter takes the data under a header: rows added to it are in it next time).
#[derive(Debug, Clone, PartialEq)]
pub struct AutoFilter {
    pub header_row: i32,
    pub first_col: i32,
    pub last_col: i32,
    pub last_row: Option<i32>,
    pub conditions: BTreeMap<i32, Condition>,
}

impl AutoFilter {
    pub fn covers(&self, col: i32) -> bool {
        col >= self.first_col && col <= self.last_col
    }
}

impl Spreadsheet {
    /// Makes `header_row`, from `first_col` to `last_col`, a filter's header (the sheet's one: as in Excel, one per
    /// sheet besides its tables). An earlier one goes, its rows shown again.
    pub fn set_filter_range(&mut self, sheet: u32, header_row: i32, first_col: i32, last_col: i32) -> Result<(), String> {
        if header_row < 1 || first_col < 1 || last_col < first_col {
            return Err(format!("not a range: row {header_row}, columns {first_col} to {last_col}"));
        }
        self.clear_filters(sheet)?;
        self.filters.insert(sheet, AutoFilter { header_row, first_col, last_col, last_row: None, conditions: BTreeMap::new() });
        self.changed();
        Ok(())
    }

    /// The sheet's filter, if it has one.
    pub fn filter_range(&self, sheet: u32) -> Option<&AutoFilter> {
        self.filters.get(&sheet)
    }

    /// Filters a column: only the rows whose cell meets `condition` show. With no filter range yet, the first row
    /// over the used columns becomes one.
    pub fn filter(&mut self, sheet: u32, col: i32, condition: Condition) -> Result<(), String> {
        if !self.filters.contains_key(&sheet) {
            let last = self.used_area(sheet).1.max(col);
            self.set_filter_range(sheet, 1, 1, last)?;
        }
        let range = self.filters.get_mut(&sheet).expect("just made");
        if !range.covers(col) {
            return Err(format!("column {col} is not in the filter's columns {} to {}", range.first_col, range.last_col));
        }
        range.conditions.insert(col, condition);
        self.apply_filter(sheet)
    }

    /// Takes a column's filter off.
    pub fn clear_filter(&mut self, sheet: u32, col: i32) -> Result<(), String> {
        if let Some(range) = self.filters.get_mut(&sheet) {
            range.conditions.remove(&col);
            return self.apply_filter(sheet);
        }
        Ok(())
    }

    /// Takes every filter of the sheet off (its rows shown again); the filter range stays.
    pub fn clear_filters(&mut self, sheet: u32) -> Result<(), String> {
        if let Some(range) = self.filters.get_mut(&sheet) {
            range.conditions.clear();
            return self.apply_filter(sheet);
        }
        Ok(())
    }

    /// The different values of a column's data (as shown), sorted: what a filter by values offers.
    pub fn filter_values(&self, sheet: u32, col: i32) -> Vec<String> {
        let Some(range) = self.filters.get(&sheet) else { return Vec::new() };
        let last = self.data_end(sheet, range);
        let values: BTreeSet<String> = (range.header_row + 1..=last).map(|row| self.text(sheet, row, col)).collect();
        values.into_iter().collect()
    }

    /// A filter's last data row: its own, or the row before the first one empty in all its columns.
    fn data_end(&self, sheet: u32, range: &AutoFilter) -> i32 {
        if let Some(last) = range.last_row {
            return last;
        }
        let used = self.used_area(sheet).0;
        let empty = |row: i32| (range.first_col..=range.last_col).all(|col| self.content(sheet, row, col).is_empty());
        (range.header_row + 1..=used).find(|row| empty(*row)).map_or(used, |blank| blank - 1)
    }

    /// The sheet's last used row and column.
    pub fn used_range(&self, sheet: u32) -> (i32, i32) {
        self.used_area(sheet)
    }

    /// The sheet's last used row and column.
    pub(crate) fn used_area(&self, sheet: u32) -> (i32, i32) {
        self.model.get_model().workbook.worksheet(sheet).map(|w| w.dimension()).map_or((1, 1), |d| (d.max_row, d.max_column))
    }

    /// Hides the data rows that do not meet every condition, shows the rest (in runs: one change per run of rows).
    pub(crate) fn apply_filter(&mut self, sheet: u32) -> Result<(), String> {
        let Some(range) = self.filters.get(&sheet).cloned() else { return Ok(()) };
        let last = self.data_end(sheet, &range);
        let model = self.model.get_model();
        let shows = |row: i32| {
            range.conditions.iter().all(|(col, condition)| {
                let text = model.get_formatted_cell_value(sheet, row, *col).unwrap_or_default();
                let number = match model.get_cell_value_by_index(sheet, row, *col) {
                    Ok(CellValue::Number(n)) => Some(n),
                    _ => None,
                };
                condition.matches(&text, number)
            })
        };
        let wanted: Vec<bool> = (range.header_row + 1..=last).map(|row| !shows(row)).collect();
        let mut start = 0;
        while start < wanted.len() {
            let hidden = wanted[start];
            let mut end = start;
            while end + 1 < wanted.len() && wanted[end + 1] == hidden {
                end += 1;
            }
            let (from, to) = (range.header_row + 1 + start as i32, range.header_row + 1 + end as i32);
            // only rows that change: each change is a step of undo
            if (from..=to).any(|r| self.is_row_hidden(sheet, r) != hidden) {
                self.model.set_rows_hidden(sheet, from, to, hidden)?;
            }
            start = end + 1;
        }
        self.changed();
        Ok(())
    }

    /// The rows a sort by a cell's column takes: the data of its table, or of the filter it is under, or else the
    /// selection when it has more than one row. `(first row, first column, last row, last column)`.
    pub fn sort_range(&self, sheet: u32, row: i32, col: i32) -> Option<(i32, i32, i32, i32)> {
        if let Some(t) = self.table_at(sheet, row, col) {
            let first = t.first_row + i32::from(t.header);
            return (first <= t.last_row).then_some((first, t.first_col, t.last_row, t.last_col));
        }
        if let Some(f) = self.filters.get(&sheet)
            && f.covers(col)
            && row >= f.header_row
        {
            let last = self.data_end(sheet, f);
            if row <= last.max(f.header_row) {
                return (f.header_row < last).then_some((f.header_row + 1, f.first_col, last, f.last_col));
            }
        }
        let (r0, c0, r1, c1) = self.selection();
        (r1 > r0 && sheet == self.sheet()).then_some((r0, c0, r1, c1))
    }

    /// Sorts by a cell's column, over [`Spreadsheet::sort_range`]. Err when there is nothing to sort there.
    pub fn sort_by(&mut self, sheet: u32, row: i32, col: i32, descending: bool) -> Result<(), String> {
        let range = self.sort_range(sheet, row, col).ok_or("nothing to sort here: choose rows, or a cell of a table or a filter")?;
        self.sort(sheet, range, col, descending)
    }

    /// Sorts a range's rows by one of its columns. A stable sort: rows that tie keep their order.
    pub fn sort(&mut self, sheet: u32, range: (i32, i32, i32, i32), by: i32, descending: bool) -> Result<(), String> {
        let (r0, c0, r1, c1) = range;
        if by < c0 || by > c1 {
            return Err(format!("column {} is not in the range", crate::column_name(by)));
        }
        if self.read_only {
            return Err("the sheet is read-only".to_owned());
        }
        // past the last used row there is nothing to move (and IronCalc copies no further)
        let r1 = r1.min(self.used_area(sheet).0);
        if r1 <= r0 {
            return Ok(());
        }
        let model = self.model.get_model();
        let keys: Vec<CellValue> = (r0..=r1).map(|r| model.get_cell_value_by_index(sheet, r, by).unwrap_or(CellValue::None)).collect();
        let mut order: Vec<i32> = (r0..=r1).collect();
        order.sort_by(|a, b| sort_key_cmp(&keys[(a - r0) as usize], &keys[(b - r0) as usize], descending));
        if order.iter().copied().eq(r0..=r1) {
            return Ok(());
        }

        // a copy of the range, in IronCalc's clipboard form
        let selected = self.model.get_selected_sheet();
        let view = self.model.get_selected_view();
        self.model.set_selected_sheet(sheet)?;
        let copied = (|| -> Result<serde_json::Map<String, serde_json::Value>, String> {
            self.model.set_selected_cell(r0, c0)?;
            self.model.set_selected_range(r0, c0, r1, c1)?;
            let clipboard = serde_json::to_value(self.model.copy_to_clipboard()?).map_err(|e| e.to_string())?;
            match clipboard.get("data") {
                Some(serde_json::Value::Object(rows)) => Ok(rows.clone()),
                _ => Err("IronCalc's clipboard has no data".to_owned()),
            }
        })();
        let done = copied.and_then(|rows| {
            let row_of = |r: i32| rows.get(&r.to_string()).cloned().unwrap_or_else(|| serde_json::Value::Object(Default::default()));
            let formulas = rows.values().any(|cells| {
                cells.as_object().is_some_and(|c| c.values().any(|cell| cell.get("text").and_then(|t| t.as_str()).is_some_and(|t| t.starts_with('='))))
            });
            let paste = |me: &mut Self, source: (i32, i32, i32, i32), at: i32, data: serde_json::Map<String, serde_json::Value>| {
                let data = serde_json::from_value(serde_json::Value::Object(data)).map_err(|e| e.to_string())?;
                me.model.set_selected_cell(at, c0)?;
                me.model.paste_from_clipboard(sheet, source, &data, false)
            };
            if formulas {
                // each row from where it was: its formulas' references move with it
                for (i, from) in order.iter().enumerate() {
                    let to = r0 + i as i32;
                    if *from != to {
                        let data = serde_json::Map::from_iter([(from.to_string(), row_of(*from))]);
                        paste(self, (*from, c0, *from, c1), to, data)?;
                    }
                }
                Ok(())
            } else {
                // values only: the rows in their new order, pasted at once
                let data = order.iter().enumerate().map(|(i, from)| ((r0 + i as i32).to_string(), row_of(*from))).collect();
                paste(self, (r0, c0, r1, c1), r0, data)
            }
        });
        // the selection as it was
        let _ = self.model.set_selected_sheet(selected);
        let [a, b, c, d] = view.range;
        let _ = self.model.set_selected_cell(view.row, view.column);
        let _ = self.model.set_selected_range(a, b, c, d);
        self.changed();
        done?;
        // a filter hides rows by where they are: hide again what does not match
        if self.filters.get(&sheet).is_some_and(|f| !f.conditions.is_empty()) {
            self.apply_filter(sheet)?;
        }
        Ok(())
    }
}

/// Excel's order: numbers, text (ignoring case; errors such as `#DIV/0!` are text here), true/false; blanks last
/// whichever way.
fn sort_key_cmp(a: &CellValue, b: &CellValue, descending: bool) -> Ordering {
    let rank = |v: &CellValue| match v {
        CellValue::Number(_) => 0,
        CellValue::String(_) => 1,
        CellValue::Boolean(_) => 2,
        CellValue::None => 3,
    };
    let (ra, rb) = (rank(a), rank(b));
    if ra == 3 || rb == 3 {
        return ra.cmp(&rb);
    }
    let order = match (a, b) {
        (CellValue::Number(x), CellValue::Number(y)) => x.total_cmp(y),
        (CellValue::String(x), CellValue::String(y)) => x.to_lowercase().cmp(&y.to_lowercase()),
        (CellValue::Boolean(x), CellValue::Boolean(y)) => x.cmp(y),
        _ => ra.cmp(&rb),
    };
    if descending { order.reverse() } else { order }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn conditions() {
        let paid: BTreeSet<String> = ["paid".to_owned()].into();
        assert!(Condition::Values(paid.clone()).matches("paid", None));
        assert!(!Condition::Values(paid).matches("pending", None));
        assert!(Condition::Contains("LAMP".into()).matches("Desk lamp", None));
        assert!(Condition::Equals("Paid".into()).matches("paid", None));
        assert!(Condition::GreaterThan(10.0).matches("12", Some(12.0)));
        assert!(!Condition::GreaterThan(10.0).matches("text", None), "text is no number");
        assert!(Condition::Between(5.0, 1.0).matches("3", Some(3.0)), "either order");
        assert!(Condition::Blank.matches("", None));
        assert!(Condition::NotBlank.matches("x", None));
    }
}
