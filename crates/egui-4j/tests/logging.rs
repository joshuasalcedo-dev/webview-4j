//! What the components log, and at which level (a test binary of its own: the logger is the process's).

use std::sync::Mutex;

use egui4j::{Component, Events, line_chart::LineChart};
use egui_kittest::Harness;
use log::{Level, LevelFilter, Log, Metadata, Record};
use serde_json::json;

static LINES: Mutex<Vec<(Level, String, String)>> = Mutex::new(Vec::new());

struct Capture;

impl Log for Capture {
    fn enabled(&self, metadata: &Metadata<'_>) -> bool {
        metadata.target().starts_with("egui4j")
    }
    fn log(&self, record: &Record<'_>) {
        if self.enabled(record.metadata()) {
            LINES.lock().unwrap().push((record.level(), record.target().to_owned(), record.args().to_string()));
        }
    }
    fn flush(&self) {}
}

fn take() -> Vec<(Level, String, String)> {
    std::mem::take(&mut LINES.lock().unwrap())
}

fn find(lines: &[(Level, String, String)], level: Level, text: &str) -> bool {
    lines.iter().any(|(l, _, m)| *l == level && m.contains(text))
}

#[test]
fn the_line_chart_says_what_it_does() {
    log::set_logger(&Capture).unwrap();
    log::set_max_level(LevelFilter::Trace);
    let mut chart = LineChart::default();

    // refused props: a warning, with the reason
    assert!(chart.update(json!({ "maxPonts": 10 })).is_err());
    let lines = take();
    assert!(find(&lines, Level::Warn, "refused props: line-chart: unknown field `maxPonts`"), "{lines:?}");
    assert!(lines.iter().all(|(_, target, _)| target == "egui4j::line_chart"), "{lines:?}");

    // new lines: debug; points streamed in: trace (many a second)
    chart.update(json!({ "series": [{ "name": "a", "points": [[0, 0], [1, 1]] }] })).unwrap();
    let lines = take();
    assert!(find(&lines, Level::Debug, "1 lines, 2 points"), "{lines:?}");
    chart.update(json!({ "append": [{ "series": "a", "points": [[2, 2]] }] })).unwrap();
    let lines = take();
    assert!(find(&lines, Level::Trace, "1 lines, 3 points"), "{lines:?}");
    assert!(!lines.iter().any(|(l, ..)| *l <= Level::Info), "a stream must not log at info: {lines:?}");

    // each event: trace
    let mut h = Harness::builder()
        .with_step_dt(1.0 / 60.0)
        .with_max_steps(120)
        .build_ui_state(|ui, (chart, events): &mut (LineChart, Events)| chart.ui(ui, events), (chart, Events::default()));
    h.run();
    let pos = h.state().0.screen_position(1.0, 1.0).unwrap();
    h.hover_at(pos);
    h.step();
    for pressed in [true, false] {
        h.event(egui::Event::PointerButton { pos, button: egui::PointerButton::Primary, pressed, modifiers: egui::Modifiers::NONE });
        h.step();
    }
    h.run();
    let lines = take();
    assert!(find(&lines, Level::Trace, r#""event":"click""#), "{lines:?}");
}
