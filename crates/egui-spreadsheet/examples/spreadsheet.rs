//! The spreadsheet in a native window: `cargo run -p egui-spreadsheet --release --example spreadsheet [file.xlsx]`.
//!
//! Opens an .xlsx (given, or dropped on the window: read on a thread of its own while a spinner shows), or starts
//! with a small budget. "1,000,000 cells" fills a sheet with 100,000 rows of numbers and formulas, to see the speed;
//! the frame time is at the top. "Save" writes the workbook back as .xlsx.

use std::time::Instant;

use egui_spreadsheet::{Loading, Spreadsheet};

fn budget() -> Spreadsheet {
    let mut s = Spreadsheet::default();
    let rows: &[&[&str]] = &[
        &["Item", "Jan", "Feb", "Mar", "Total"],
        &["Rent", "1200", "1200", "1250", "=SUM(B2:D2)"],
        &["Food", "430", "385.5", "410", "=SUM(B3:D3)"],
        &["Transport", "95", "120", "88", "=SUM(B4:D4)"],
        &["Total", "=SUM(B2:B4)", "=SUM(C2:C4)", "=SUM(D2:D4)", "=SUM(E2:E4)"],
        &["Average", "", "", "", "=AVERAGE(B5:D5)"],
        &["Over 2000?", "", "", "", "=IF(E6>2000,\"yes\",\"no\")"],
        &["Lookup Food", "", "", "", "=VLOOKUP(\"Food\",A2:E4,5,FALSE)"],
        &["As money", "", "", "", "=TEXT(E5,\"$#,##0.00\")"],
        &["Errors", "=1/0", "=NOPE(1)", "=VLOOKUP(\"x\",A2:E4,2,FALSE)", ""],
    ];
    for (r, cells) in rows.iter().enumerate() {
        for (c, text) in cells.iter().enumerate() {
            if !text.is_empty() {
                s.set(0, r as i32 + 1, c as i32 + 1, text).expect("a cell");
            }
        }
    }
    s
}

/// 100,000 rows: an id, eight numbers, and a formula over them.
fn million() -> Spreadsheet {
    let mut s = Spreadsheet::default();
    let started = Instant::now();
    s.model().pause_evaluation();
    for row in 1..=100_000 {
        s.set(0, row, 1, &format!("row {row}")).expect("a cell");
        for col in 2..=9 {
            s.set(0, row, col, &((row * 31 + col * 7) % 1000).to_string()).expect("a cell");
        }
        s.set(0, row, 10, &format!("=SUM(B{row}:I{row})")).expect("a cell");
    }
    s.model().resume_evaluation();
    s.model().evaluate();
    s.changed();
    println!("filled 1,000,000 cells in {:.1} s", started.elapsed().as_secs_f32());
    s
}

struct Demo {
    sheet: Spreadsheet,
    loading: Option<Loading>,
    message: String,
    path: Option<String>,
}

impl eframe::App for Demo {
    fn ui(&mut self, ui: &mut egui::Ui, _frame: &mut eframe::Frame) {
        let ctx = ui.ctx().clone();
        // a file dropped on the window: read in the background
        if let Some(file) = ctx.input(|i| i.raw.dropped_files.first().cloned()) {
            match file.bytes() {
                Ok(bytes) => {
                    let name = file.path().file_name().map_or_else(|| "the file".to_owned(), |n| n.to_string_lossy().into_owned());
                    self.path = Some(file.path().display().to_string());
                    self.loading = Some(Loading::start(bytes, &name));
                }
                Err(e) => self.message = e,
            }
        }
        if let Some(loading) = &mut self.loading {
            match loading.poll() {
                None => {
                    loading.show(ui);
                    ctx.request_repaint();
                    return;
                }
                Some(Ok(sheet)) => {
                    self.message = format!("opened {}", loading.name);
                    self.sheet = sheet;
                }
                Some(Err(e)) => self.message = e,
            }
            self.loading = None;
        }

        egui::Panel::top("demo").show(ui, |ui| {
            ui.horizontal(|ui| {
                if ui.button("Budget").clicked() {
                    self.sheet = budget();
                }
                if ui.button("1,000,000 cells").clicked() {
                    let started = Instant::now();
                    self.sheet = million();
                    self.message = format!("1,000,000 cells in {:.1} s", started.elapsed().as_secs_f32());
                }
                if ui.button("Save").clicked() {
                    let to = self.path.clone().unwrap_or_else(|| "spreadsheet.xlsx".to_owned());
                    self.message = match self.sheet.to_xlsx().and_then(|b| std::fs::write(&to, b).map_err(|e| e.to_string())) {
                        Ok(()) => format!("saved {to}"),
                        Err(e) => e,
                    };
                }
                ui.weak("Drop an .xlsx on the window to open it");
                ui.with_layout(egui::Layout::right_to_left(egui::Align::Center), |ui| {
                    let ms = ctx.input(|i| i.unstable_dt) * 1000.0;
                    ui.monospace(format!("frame {ms:.1} ms"));
                    ui.weak(&self.message);
                });
            });
        });
        egui::CentralPanel::default().show(ui, |ui| {
            let out = self.sheet.show(ui);
            for (sheet, row, col) in &out.changed {
                println!("changed {} (sheet {sheet})", egui_spreadsheet::cell_name(*row, *col));
            }
        });
    }
}

fn main() -> eframe::Result {
    env_logger::init();
    let path = std::env::args().nth(1);
    let loading = path.as_ref().and_then(|p| std::fs::read(p).ok().map(|b| Loading::start(b, p)));
    let demo = Demo { sheet: budget(), loading, message: String::new(), path };
    eframe::run_native("egui-spreadsheet", eframe::NativeOptions::default(), Box::new(|_| Ok(Box::new(demo))))
}
