//! The spreadsheet in a native window: `cargo run -p egui-spreadsheet --example spreadsheet`.
//!
//! A small budget to start from: sums, an average, an IF, a division by zero and a cycle (so every kind of cell
//! shows). Type in a cell, or in the formula bar; Enter, Tab, Escape, arrows with Shift, Delete, Ctrl+C/X/V.
//! What changed is printed, as a host (egui-4j, then Java) would get it.

use egui_spreadsheet::Spreadsheet;
use egui_spreadsheet::sheet::{CellRef, Sheet};

fn budget() -> Sheet {
    let cells = [
        ("A1", "Item"), ("B1", "Jan"), ("C1", "Feb"), ("D1", "Mar"), ("E1", "Total"),
        ("A2", "Rent"), ("B2", "1200"), ("C2", "1200"), ("D2", "1250"), ("E2", "=SUM(B2:D2)"),
        ("A3", "Food"), ("B3", "430"), ("C3", "385.5"), ("D3", "410"), ("E3", "=SUM(B3:D3)"),
        ("A4", "Transport"), ("B4", "95"), ("C4", "120"), ("D4", "88"), ("E4", "=SUM(B4:D4)"),
        ("A5", "Total"), ("B5", "=SUM(B2:B4)"), ("C5", "=SUM(C2:C4)"), ("D5", "=SUM(D2:D4)"), ("E5", "=SUM(E2:E4)"),
        ("A6", "Average"), ("E6", "=AVG(B5:D5)"),
        ("A7", "Over 2000?"), ("E7", "=IF(E6>2000, \"yes\", \"no\")"),
        ("A9", "Errors, on purpose"), ("B9", "=1/0"), ("C9", "=D9"), ("D9", "=C9"), ("E9", "=NOPE(1)"),
    ];
    Sheet::from_cells(cells.into_iter().map(|(at, text)| (CellRef::parse(at).expect("a reference"), text)))
}

fn main() -> eframe::Result {
    env_logger::init();
    let mut sheet = Spreadsheet::new(budget());
    eframe::run_ui_native("egui-spreadsheet", eframe::NativeOptions::default(), move |ui, _| {
        egui::CentralPanel::default().show(ui, |ui| {
            let out = sheet.show(ui);
            for (at, text) in &out.changed {
                println!("changed {} = {text:?}", at.name());
            }
            if let Some(range) = out.selected {
                println!("selected {}:{}", range.from.name(), range.to.name());
            }
        });
    })
}
