//! A spreadsheet for egui: formulas over 26 x 10,000 cells.
//!
//! The engine (the formula language, the dependency order, the sheet and its edits) is egui-reactor's, taken
//! unchanged with its tests: [`formula`], [`eval`], [`sheet`]. Nothing in it knows about egui. The grid on screen
//! is ours: [`Spreadsheet`].

pub mod eval;
pub mod grid;
pub mod formula;
pub mod sheet;

pub use grid::{Output, Spreadsheet};
