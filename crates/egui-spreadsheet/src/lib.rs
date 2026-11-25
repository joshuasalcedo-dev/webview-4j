//! A spreadsheet for egui, fast on Excel-sized sheets (1,048,576 rows by 16,384 columns): only what is in view is
//! drawn. The engine is [IronCalc](https://github.com/ironcalc/IronCalc): Excel's formulas and number formats,
//! workbooks of several sheets, undo. Data comes in from .xlsx and CSV files ([`Spreadsheet::open`], or [`Loading`]
//! in the background) and goes out as .xlsx ([`Spreadsheet::to_xlsx`]).

// placeholders, to be filled: columns, filters, rows, tables, validation
pub mod column;
pub mod filter;
pub mod grid;
pub mod load;
pub mod row;
pub mod style;
pub mod table;
pub mod validation;

pub use grid::{Output, Spreadsheet, cell_name, column_name};
pub use ironcalc;
pub use load::{Kind, Loading};
