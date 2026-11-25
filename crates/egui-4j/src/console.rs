//! The log in the page (wasm only): each line goes to the browser's console, `[target] message`, at its own
//! level (an error is a `console.error`). webview-4j passes a page's console to Java (`LogRecord`, source PAGE),
//! so what a component logs shows in the app's log and in devtools.
//!
//! egui-4j's own lines (`egui4j::*`) are kept from `level` up (info at first); egui's and eframe's from warn up,
//! as their debug lines are many.

use std::sync::atomic::{AtomicUsize, Ordering};

use log::{Level, LevelFilter, Log, Metadata, Record};

static LEVEL: AtomicUsize = AtomicUsize::new(LevelFilter::Info as usize);
const OTHERS: Level = Level::Warn;

struct Console;

fn own_level() -> LevelFilter {
    match LEVEL.load(Ordering::Relaxed) {
        0 => LevelFilter::Off,
        1 => LevelFilter::Error,
        2 => LevelFilter::Warn,
        3 => LevelFilter::Info,
        4 => LevelFilter::Debug,
        _ => LevelFilter::Trace,
    }
}

impl Log for Console {
    fn enabled(&self, metadata: &Metadata<'_>) -> bool {
        if metadata.target().starts_with("egui4j") {
            metadata.level() <= own_level()
        } else {
            metadata.level() <= OTHERS
        }
    }

    fn log(&self, record: &Record<'_>) {
        if !self.enabled(record.metadata()) {
            return;
        }
        let line = wasm_bindgen::JsValue::from_str(&format!("[{}] {}", record.target(), record.args()));
        match record.level() {
            Level::Error => web_sys::console::error_1(&line),
            Level::Warn => web_sys::console::warn_1(&line),
            Level::Info => web_sys::console::info_1(&line),
            Level::Debug | Level::Trace => web_sys::console::debug_1(&line),
        }
    }

    fn flush(&self) {}
}

/// Installs the logger (once per page: a second call only keeps the level).
pub(crate) fn install() {
    if log::set_logger(&Console).is_ok() {
        log::set_max_level(LevelFilter::Trace);
    }
}

/// How much of egui-4j's own log to keep: "off", "error", "warn", "info", "debug" or "trace".
pub(crate) fn set_level(level: &str) -> Result<(), String> {
    let filter: LevelFilter = level.parse().map_err(|_| format!("not a log level: {level:?} (off, error, warn, info, debug, trace)"))?;
    LEVEL.store(filter as usize, Ordering::Relaxed);
    Ok(())
}
