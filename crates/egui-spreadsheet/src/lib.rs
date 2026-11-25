//! A spreadsheet for egui, fast on Excel-sized sheets (1,048,576 rows by 16,384 columns): only what is in view is
//! drawn. The engine is [IronCalc](https://github.com/ironcalc/IronCalc): Excel's formulas and number formats,
//! workbooks of several sheets, undo. Data comes in from .xlsx and CSV files ([`Spreadsheet::open`], or [`Loading`]
//! in the background) and goes out as .xlsx ([`Spreadsheet::to_xlsx`]).
//!
//! Besides the cells: [`column`]s and [`row`]s (size, hide, insert, delete), [`style`]s, [`filter`]s, [`table`]s,
//! [`validation`], sorting (in [`filter`]), the [`toolbar`] over it all and the [`statusbar`] under it.

pub mod column;
pub mod filter;
pub mod grid;
pub mod load;
pub mod row;
pub mod source;
pub mod statusbar;
pub mod style;
pub mod table;
pub mod toolbar;
pub mod validation;
pub mod viewer;
pub mod viewers;

pub use grid::{Output, Spreadsheet, cell_name, column_name};
pub use ironcalc;
pub use load::{Kind, Loading};
pub use source::{DataSource, Rows, Value};
pub use viewer::Viewer;

/// A cell's A1 name as (row, column), 1-based: `B2` is (2, 2), `XFD1048576` the last. None for anything else.
pub fn parse_cell(name: &str) -> Option<(i32, i32)> {
    let name = name.trim().trim_start_matches('$');
    let letters = name.bytes().take_while(u8::is_ascii_alphabetic).count();
    let (col_part, row_part) = name.split_at(letters);
    let row_part = row_part.trim_start_matches('$');
    if letters == 0 || letters > 3 || row_part.is_empty() || !row_part.bytes().all(|b| b.is_ascii_digit()) {
        return None;
    }
    let col = col_part.bytes().fold(0i32, |n, b| n * 26 + i32::from(b.to_ascii_uppercase() - b'A' + 1));
    let row: i32 = row_part.parse().ok()?;
    ((1..=grid::MAX_ROW).contains(&row) && (1..=grid::MAX_COL).contains(&col)).then_some((row, col))
}

/// A range's A1 reference as (first row, first column, last row, last column): `A1:D10`, or one cell `B2`.
pub fn parse_range(reference: &str) -> Option<(i32, i32, i32, i32)> {
    let reference = reference.rsplit('!').next()?;
    let (a, b) = reference.split_once(':').unwrap_or((reference, reference));
    let (r0, c0) = parse_cell(a)?;
    let (r1, c1) = parse_cell(b)?;
    Some((r0.min(r1), c0.min(c1), r0.max(r1), c0.max(c1)))
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn references() {
        assert_eq!(parse_cell("B2"), Some((2, 2)));
        assert_eq!(parse_cell("$AA$10"), Some((10, 27)));
        assert_eq!(parse_cell("XFD1048576"), Some((1_048_576, 16_384)));
        assert_eq!(parse_cell("XFE1"), None);
        assert_eq!(parse_cell("A0"), None);
        assert_eq!(parse_cell("1A"), None);
        assert_eq!(parse_range("A1:D10"), Some((1, 1, 10, 4)));
        assert_eq!(parse_range("D10:A1"), Some((1, 1, 10, 4)), "either corner first");
        assert_eq!(parse_range("Sheet1!B2"), Some((2, 2, 2, 2)));
        assert_eq!(parse_range("nope"), None);
    }
}
