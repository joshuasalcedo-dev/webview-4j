//! A spreadsheet for egui, fast on Excel-sized sheets (1,048,576 rows by 16,384 columns): only what is in view is
//! drawn. The engine is [IronCalc](https://github.com/ironcalc/IronCalc): Excel's formulas and number formats,
//! workbooks of several sheets, undo, and .xlsx files in ([`Spreadsheet::from_xlsx`], [`Loading`] in the background)
//! and out ([`Spreadsheet::to_xlsx`]).

pub mod grid;

pub use grid::{Loading, Output, Spreadsheet, cell_name, column_name};
pub use ironcalc;
