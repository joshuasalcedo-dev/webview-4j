//! The line chart in a native window: `cargo run -p egui-4j --example line_chart`.
//!
//! Two lines stream in (60 points a second, the latest 600 kept), the same way a page would feed them from
//! Java: as JSON props. Zoom with the wheel, pan by dragging, double-click to fit the data again, click near a
//! point: each event shows on the right (and on stdout), as Java would get it.

use egui4j::{Component, Events, line_chart::LineChart};
use serde_json::{Value, json};

fn main() -> eframe::Result {
    // RUST_LOG=egui4j=debug (or trace: every event and point) shows what the chart does
    env_logger::Builder::from_env(env_logger::Env::default().default_filter_or("egui4j=info")).init();
    let options = eframe::NativeOptions {
        viewport: egui::ViewportBuilder::default().with_inner_size([1000.0, 600.0]).with_title("egui-4j: line-chart"),
        ..Default::default()
    };
    eframe::run_native("egui-4j line-chart", options, Box::new(|_| Ok(Box::new(Demo::new()))))
}

struct Demo {
    chart: LineChart,
    events: Events,
    log: Vec<String>,
    t: f64,
    streaming: bool,
    dark: bool,
}

impl Demo {
    fn new() -> Self {
        let mut chart = LineChart::default();
        chart
            .update(json!({
                "series": [
                    { "name": "cpu", "color": "#0a84ff" },
                    { "name": "memory", "color": "#ff9f0a" }
                ],
                "maxPoints": 600,
                "xLabel": "seconds",
                "yLabel": "%"
            }))
            .expect("valid props");
        Demo { chart, events: Events::default(), log: Vec::new(), t: 0.0, streaming: true, dark: true }
    }

    fn send(&mut self, props: Value) {
        if let Err(e) = self.chart.update(props) {
            self.log.push(format!("update refused: {e}"));
        }
    }
}

impl eframe::App for Demo {
    fn ui(&mut self, ui: &mut egui::Ui, _frame: &mut eframe::Frame) {
        let ctx = ui.ctx().clone();
        ctx.set_visuals(if self.dark { egui::Visuals::dark() } else { egui::Visuals::light() });

        if self.streaming {
            self.t += 1.0 / 60.0;
            let t = self.t;
            let cpu = 50.0 + 30.0 * (t * 1.3).sin() + 8.0 * (t * 7.1).sin();
            let memory = 40.0 + 10.0 * (t * 0.4).sin() + t.min(20.0);
            self.send(json!({ "append": [
                { "series": "cpu", "points": [[t, cpu]] },
                { "series": "memory", "points": [[t, memory]] }
            ]}));
            ctx.request_repaint();
        }

        egui::Panel::right("events").default_size(320.0).show(ui, |ui| {
            ui.heading("Props");
            ui.horizontal_wrapped(|ui| {
                ui.toggle_value(&mut self.streaming, "streaming");
                ui.toggle_value(&mut self.dark, "dark");
                if ui.button("replace data").clicked() {
                    let points: Vec<[f64; 2]> = (0..200).map(|i| [i as f64, ((i as f64) / 10.0).cos() * 50.0]).collect();
                    self.streaming = false;
                    self.send(json!({ "series": [{ "name": "cos", "points": points, "color": "#30d158" }] }));
                }
                if ui.button("clear").clicked() {
                    self.send(json!({ "series": [] }));
                }
                if ui.button("bad props").clicked() {
                    self.send(json!({ "maxPonts": 10 }));
                }
            });
            ui.separator();
            ui.heading("Events");
            egui::ScrollArea::vertical().stick_to_bottom(true).show(ui, |ui| {
                for line in &self.log {
                    ui.monospace(line);
                }
            });
        });

        egui::CentralPanel::default().show(ui, |ui| {
            self.chart.ui(ui, &mut self.events);
        });

        for event in self.events.drain() {
            println!("{event}");
            self.log.push(event.to_string());
        }
    }
}
