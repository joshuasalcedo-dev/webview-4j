//! A CSV file in the viewer.
//!
//! cargo run -p egui-spreadsheet --release --example viewer -- data.csv

use egui_spreadsheet::Viewer;

fn main() -> eframe::Result {
    let path = std::env::args().nth(1).expect("a .csv file");
    let mut viewer = Viewer::open(&path).expect("a readable file");
    eframe::run_ui_native(&path, eframe::NativeOptions::default(), move |ui, _| {
        egui::CentralPanel::default().show(ui, |ui| viewer.show(ui));
    })
}
