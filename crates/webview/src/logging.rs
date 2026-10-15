//! Forwards the native side's `log` records (this crate, Tao, Wry) to Java as events:
//! `{"type":"log","source":"native","level":"warn","target":"wry::...","message":"..."}`.

use log::{LevelFilter, Log, Metadata, Record};
use serde_json::json;

use crate::runtime;

struct Forward;

impl Log for Forward {
    fn enabled(&self, metadata: &Metadata) -> bool {
        metadata.level() <= log::max_level()
    }

    fn log(&self, record: &Record) {
        if self.enabled(record.metadata()) {
            runtime::emit(json!({
                "type": "log",
                "source": "native",
                "level": record.level().as_str().to_ascii_lowercase(),
                "target": record.target(),
                "message": record.args().to_string(),
            }));
        }
    }

    fn flush(&self) {}
}

/// Installs the forwarder at `info`, once per process; a logger installed before it wins.
pub fn install() {
    static FORWARD: Forward = Forward;
    if log::set_logger(&FORWARD).is_ok() {
        log::set_max_level(LevelFilter::Info);
    }
}
