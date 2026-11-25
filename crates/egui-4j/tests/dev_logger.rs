//! The dev logger, headless: entries in, filters, timings, the JSON viewer, and the list as the user uses it.

use egui::Vec2;
use egui_kittest::Harness;
use egui_kittest::kittest::Queryable;
use egui4j::dev_logger::{DevLogger, Kind};
use egui4j::json_view::Body;
use egui4j::{Component, Events};
use serde_json::{Value, json};

type State = (DevLogger, Events);

fn http(time: f64, url: &str, status: u16, ms: f64, body: Value) -> Value {
    json!({ "kind": "http", "time": time, "source": "page", "method": "GET", "url": url, "status": status,
            "durationMs": ms, "request": { "headers": { "accept": "application/json" } },
            "response": { "headers": { "content-type": "application/json" }, "body": body } })
}

fn sample() -> Value {
    json!({ "append": [
        { "kind": "log", "time": 1000.0, "level": "info", "target": "app", "message": "started" },
        http(1100.0, "/api/todos", 200, 40.0, json!("{\"items\":[{\"id\":1,\"title\":\"milk\"}],\"page\":1}")),
        http(1200.0, "/api/users", 500, 900.0, json!({ "error": "database is locked" })),
        { "kind": "bridge", "time": 1300.0, "direction": "toJava", "name": "TodoController.add", "durationMs": 3.0,
          "request": { "body": { "title": "bread" } }, "response": { "body": { "id": 7 } } },
        { "kind": "http", "time": 1400.0, "source": "java", "method": "GET", "url": "https://rates.example/today",
          "durationMs": 3000.0, "error": "timed out" },
        { "kind": "perf", "time": 1500.0, "name": "long task", "value": 87, "unit": "ms" }
    ]})
}

fn harness(props: Value, size: Vec2) -> Harness<'static, State> {
    let mut logger = DevLogger::default();
    logger.update(props).expect("valid props");
    let mut h = Harness::builder()
        .with_size(size)
        .with_step_dt(1.0 / 60.0)
        .with_max_steps(120)
        // egui_dock's splitters (between the panes) have no accessible name, and cannot be given one from here
        .with_accessibility_check(false)
        .build_ui_state(|ui, (logger, events): &mut State| logger.ui(ui, events), (logger, Events::default()));
    h.run();
    h
}

fn events(h: &mut Harness<'static, State>) -> Vec<Value> {
    h.state_mut().1.drain()
}

fn kinds(logger: &DevLogger) -> Vec<Kind> {
    logger.entries().map(|e| e.kind()).collect()
}

// ---- entries ----

#[test]
fn takes_every_kind_in_order() {
    let mut logger = DevLogger::default();
    logger.update(sample()).unwrap();
    assert_eq!(kinds(&logger), [Kind::Log, Kind::Http, Kind::Http, Kind::Bridge, Kind::Http, Kind::Perf]);
    let ids: Vec<u64> = logger.entries().map(|e| e.id).collect();
    assert_eq!(ids, [1, 2, 3, 4, 5, 6]);
}

#[test]
fn errors_are_failed_calls_and_error_lines() {
    let mut logger = DevLogger::default();
    logger.update(sample()).unwrap();
    logger.update(json!({ "append": [{ "kind": "log", "time": 0, "level": "ERROR", "message": "boom" }] })).unwrap();
    let errors: Vec<u64> = logger.entries().filter(|e| e.is_error()).map(|e| e.id).collect();
    assert_eq!(errors, [3, 5, 7], "the 500, the timeout and the error line");
}

#[test]
fn keeps_the_latest_max_entries() {
    let mut logger = DevLogger::default();
    logger.update(json!({ "maxEntries": 3 })).unwrap();
    logger.update(sample()).unwrap();
    let ids: Vec<u64> = logger.entries().map(|e| e.id).collect();
    assert_eq!(ids, [4, 5, 6]);
}

#[test]
fn clear_empties_it_before_what_is_appended() {
    let mut logger = DevLogger::default();
    logger.update(sample()).unwrap();
    logger.update(json!({ "clear": true, "append": [{ "kind": "perf", "time": 0, "name": "fps", "value": 60 }] })).unwrap();
    assert_eq!(kinds(&logger), [Kind::Perf]);
}

#[test]
fn bad_props_are_refused_and_change_nothing() {
    let mut logger = DevLogger::default();
    logger.update(sample()).unwrap();
    let before: Vec<_> = logger.entries().cloned().collect();
    for bad in [
        json!({ "append": [{ "kind": "sql", "time": 0 }] }),
        json!({ "append": [{ "kind": "http", "time": 0, "method": "GET" }] }),
        json!({ "append": [{ "kind": "log", "time": 0, "level": "info", "message": "x", "colour": "red" }] }),
        json!({ "append": [{ "kind": "log", "level": "info", "message": "no time" }] }),
        json!({ "maxEntries": 0 }),
        json!({ "slowMs": -1 }),
        json!({ "filter": { "kinds": ["nope"] } }),
        json!({ "apend": [] }),
    ] {
        assert!(logger.update(bad.clone()).is_err(), "took {bad}");
    }
    let after: Vec<_> = logger.entries().cloned().collect();
    assert_eq!(after, before);
}

#[test]
fn timings_are_over_the_calls() {
    let mut logger = DevLogger::default();
    logger.update(sample()).unwrap();
    let t = logger.timings();
    assert_eq!(t.calls, 4, "3 HTTP + 1 bridge");
    assert_eq!(t.errors, 2);
    assert_eq!(t.p50, Some(40.0));
    assert_eq!(t.p95, Some(3000.0));
    assert_eq!(t.slowest, Some((5, 3000.0)));

    let mut empty = DevLogger::default();
    let t = empty.timings();
    assert_eq!((t.calls, t.p50, t.slowest), (0, None, None));
}

// ---- the JSON viewer's reading ----

#[test]
fn bodies_are_read_as_json_when_they_are() {
    assert_eq!(Body::from_value(&json!("{\"a\":1}")), Body::Json(json!({ "a": 1 })));
    assert_eq!(Body::from_value(&json!("[1,2]")), Body::Json(json!([1, 2])));
    assert_eq!(Body::from_value(&json!({ "a": 1 })), Body::Json(json!({ "a": 1 })));
    // text that only looks like a JSON value stays text
    assert_eq!(Body::from_value(&json!("42")), Body::Text("42".into()));
    assert_eq!(Body::from_value(&json!("<html>")), Body::Text("<html>".into()));
    assert_eq!(Body::from_value(&json!("{broken")), Body::Text("{broken".into()));
    assert_eq!(Body::Json(json!({ "a": 1 })).to_text(), "{\n  \"a\": 1\n}");
}

// ---- the list ----

#[test]
fn filters_by_kind_search_and_errors() {
    let mut h = harness(sample(), Vec2::new(900.0, 600.0));
    assert_eq!(h.state().0.visible().len(), 6);

    h.state_mut().0.update(json!({ "filter": { "kinds": ["http", "bridge"] } })).unwrap();
    h.run();
    assert_eq!(h.state().0.visible(), [2, 3, 4, 5]);

    // search looks in the bodies too: "locked" is only in the 500's body
    h.state_mut().0.update(json!({ "filter": { "search": "LOCKED" } })).unwrap();
    h.run();
    assert_eq!(h.state().0.visible(), [3]);

    h.state_mut().0.update(json!({ "filter": { "search": "", "errorsOnly": true } })).unwrap();
    h.run();
    assert_eq!(h.state().0.visible(), [3, 5]);
}

#[test]
fn the_kind_buttons_hide_and_show() {
    let mut h = harness(sample(), Vec2::new(900.0, 600.0));
    h.get_by_label("HTTP 3").click();
    h.run();
    assert_eq!(h.state().0.visible(), [1, 4, 6]);
    h.get_by_label("HTTP 3").click();
    h.run();
    assert_eq!(h.state().0.visible().len(), 6);
}

#[test]
fn a_row_opens_its_bodies_and_headers() {
    let mut h = harness(sample(), Vec2::new(900.0, 700.0));
    events(&mut h);
    h.get_by_label("GET /api/todos  (page)").click();
    h.run();
    assert_eq!(events(&mut h), [json!({ "event": "select", "id": 2 })]);
    assert_eq!(h.state().0.selected(), Some(2));

    // the response body, JSON text read as a tree: its keys show
    assert!(h.query_by_label("items:").is_some(), "no JSON tree");
    assert!(h.query_by_label("page:").is_some());

    h.get_by_label("Request").click();
    h.run();
    assert!(h.query_by_label("(no body)").is_some());

    h.get_by_label("Headers").click();
    h.run();
    assert!(h.query_by_label("content-type").is_some());
    assert!(h.query_by_label("accept").is_some());
}

#[test]
fn a_failed_call_says_why() {
    let mut h = harness(sample(), Vec2::new(900.0, 700.0));
    h.get_by_label("GET https://rates.example/today  (java)").click();
    h.run();
    assert!(h.query_by_label("timed out").is_some());
}

#[test]
fn the_slowest_call_is_a_link_to_it() {
    let mut h = harness(sample(), Vec2::new(900.0, 700.0));
    h.get_by_label("slowest 3.00 s · GET https://rates.example/today  (java)").click();
    h.run();
    assert_eq!(h.state().0.selected(), Some(5));
}

#[test]
fn clear_empties_the_list() {
    let mut h = harness(sample(), Vec2::new(900.0, 600.0));
    events(&mut h);
    h.get_by_label("Clear").click();
    h.run();
    assert_eq!(events(&mut h), [json!({ "event": "clear" })]);
    assert_eq!(h.state().0.entries().count(), 0);
    assert!(h.query_by_label("Nothing yet").is_some());
}

#[test]
fn says_when_nothing_matches() {
    let mut h = harness(sample(), Vec2::new(900.0, 600.0));
    h.state_mut().0.update(json!({ "filter": { "search": "no such thing" } })).unwrap();
    h.run();
    assert!(h.query_by_label("Nothing matches").is_some());
}

#[test]
fn always_shows_the_newest_line() {
    let mut h = harness(json!({}), Vec2::new(900.0, 400.0));
    for batch in 0..5 {
        let lines: Vec<Value> = (0..100)
            .map(|i| json!({ "kind": "log", "time": (batch * 100 + i) as f64, "level": "info", "message": format!("line {}", batch * 100 + i) }))
            .collect();
        h.state_mut().0.update(json!({ "append": lines })).unwrap();
        h.run();
        let last = format!("INFO line {}", batch * 100 + 99);
        assert!(h.query_by_label(&last).is_some(), "{last} not in view");
    }
    // choosing a line does not stop it following
    h.get_by_label("INFO line 495").click();
    h.run();
    h.state_mut().0.update(json!({ "append": [{ "kind": "log", "time": 999.0, "level": "info", "message": "newest" }] })).unwrap();
    h.run();
    assert!(h.query_by_label("INFO newest").is_some(), "stopped following after a click");
}

#[test]
fn a_huge_body_is_cut_short() {
    let items: Vec<Value> = (0..10_000).map(|i| json!({ "id": i })).collect();
    let mut h = harness(json!({ "append": [http(0.0, "/big", 200, 5.0, json!(items))] }), Vec2::new(900.0, 700.0));
    h.get_by_label("GET /big  (page)").click();
    h.run();
    assert!(h.query_by_label("… 9800 more").is_some());
}

#[test]
fn survives_having_no_room() {
    let mut h = harness(sample(), Vec2::new(900.0, 600.0));
    h.get_by_label("GET /api/todos  (page)").click();
    for size in [Vec2::ZERO, Vec2::new(1.0, 1.0), Vec2::new(4000.0, 20.0), Vec2::new(900.0, 600.0)] {
        h.set_size(size);
        h.run();
    }
}

fn log_lines(from: usize, count: usize) -> Value {
    let lines: Vec<Value> = (from..from + count)
        .map(|i| json!({ "kind": "log", "time": i as f64, "level": "info", "message": format!("line {i}") }))
        .collect();
    json!({ "append": lines })
}

fn wheel(h: &mut Harness<'static, State>, lines: f32) {
    // over the list (the pane at the top left)
    h.hover_at(egui::pos2(300.0, 150.0));
    h.step();
    h.event(egui::Event::MouseWheel {
        unit: egui::MouseWheelUnit::Line,
        delta: Vec2::new(0.0, lines),
        modifiers: egui::Modifiers::NONE,
        phase: egui::TouchPhase::Move,
        source: egui::MouseWheelSource::Wheel,
    });
    h.run_steps(30);
}

#[test]
fn follows_again_once_scrolled_back_down() {
    let mut h = harness(log_lines(0, 300), Vec2::new(900.0, 400.0));
    assert!(h.query_by_label("INFO line 299").is_some());

    // scrolled up to read: new lines do not pull it down
    wheel(&mut h, 20.0);
    h.state_mut().0.update(log_lines(300, 5)).unwrap();
    h.run();
    assert!(h.query_by_label("INFO line 304").is_none(), "pulled down while reading");

    // back down, by the wheel (where it rarely lands on the exact pixel): it follows again
    for _ in 0..10 {
        wheel(&mut h, -40.0);
    }
    for batch in 0..3 {
        let from = 305 + batch * 50;
        h.state_mut().0.update(log_lines(from, 50)).unwrap();
        h.run();
        let last = format!("INFO line {}", from + 49);
        assert!(h.query_by_label(&last).is_some(), "{last} not in view: not following after scrolling back down");
    }
}

#[test]
fn follows_with_the_details_open_and_resized() {
    let mut h = harness(log_lines(0, 300), Vec2::new(900.0, 700.0));
    h.get_by_label("INFO line 299").click();
    h.run();
    for (batch, height) in [(0, 500.0), (1, 800.0), (2, 600.0)] {
        h.set_size(Vec2::new(900.0, height));
        let from = 300 + batch * 50;
        h.state_mut().0.update(log_lines(from, 50)).unwrap();
        h.run();
        let last = format!("INFO line {}", from + 49);
        assert!(h.query_by_label(&last).is_some(), "{last} not in view at height {height}");
    }
}

/// What Java sends (protocol/fixtures/dev-logger.json, checked by core's DevLoggerTest): every entry is taken.
#[test]
fn takes_what_java_sends() {
    let path = std::path::Path::new(env!("CARGO_MANIFEST_DIR")).join("../../protocol/fixtures/dev-logger.json");
    let fixture: Value = serde_json::from_str(&std::fs::read_to_string(path).unwrap()).unwrap();
    let entries: Vec<Value> = fixture["cases"]
        .as_array()
        .unwrap()
        .iter()
        .map(|c| {
            let mut e = c["entry"].clone();
            e["time"] = json!(1_732_500_000_000_u64);
            e
        })
        .collect();
    let mut logger = DevLogger::default();
    logger.update(json!({ "append": entries })).expect("Java's entries are the dev logger's format");
    assert_eq!(
        kinds(&logger),
        [Kind::Log, Kind::Log, Kind::Log, Kind::Http, Kind::Http, Kind::Bridge, Kind::Bridge, Kind::Log, Kind::Http, Kind::Http]
    );
    let errors: Vec<u64> = logger.entries().filter(|e| e.is_error()).map(|e| e.id).collect();
    assert_eq!(errors, [2, 4, 5, 7, 10], "the native error, the 500, the calls with no answer, the failed route");
}

// ---- the host searches (it keeps everything; the logger only the latest) ----

fn searches(h: &mut Harness<'static, State>) -> Vec<Value> {
    events(h).into_iter().filter(|e| e["event"] == "search").collect()
}

#[test]
fn typing_asks_the_host_once_the_box_is_still() {
    let mut h = harness(json!({ "hostSearch": true, "maxEntries": 100 }), Vec2::new(900.0, 600.0));
    h.state_mut().0.update(sample()).unwrap();
    h.run();
    events(&mut h);
    let search = h.get_by_role(egui::accesskit::Role::TextInput);
    search.click();
    h.run_steps(2);
    h.get_by_role(egui::accesskit::Role::TextInput).type_text("lock");
    h.run_steps(5);
    assert!(searches(&mut h).is_empty(), "asked while still typing");
    h.run_steps(30);
    assert_eq!(searches(&mut h), [json!({ "event": "search", "text": "lock" })]);
    h.run_steps(30);
    assert!(searches(&mut h).is_empty(), "asked twice for the same text");
    assert!(h.query_by_label("Searching…").is_some());
}

#[test]
fn shows_what_the_host_found_while_that_is_the_search() {
    let mut h = harness(json!({ "hostSearch": true }), Vec2::new(900.0, 700.0));
    h.state_mut().0.update(sample()).unwrap();
    h.state_mut().0.update(json!({ "filter": { "search": "locked" } })).unwrap();
    h.run();
    assert_eq!(searches(&mut h), [json!({ "event": "search", "text": "locked" })]);

    // an old entry, no longer among the latest: only the host had it
    let old = http(5.0, "/api/old-orders", 503, 40.0, json!({ "error": "database is locked" }));
    h.state_mut().0.update(json!({ "found": { "text": "locked", "entries": [old] } })).unwrap();
    h.run();
    assert_eq!(h.state().0.visible().len(), 1);
    assert!(h.query_by_label("GET /api/old-orders  (page)").is_some());

    // it opens like any row
    h.get_by_label("GET /api/old-orders  (page)").click();
    h.run();
    assert!(h.query_by_label("error:").is_some());

    // the filters still apply to what was found
    h.state_mut().0.update(json!({ "filter": { "kinds": ["log"] } })).unwrap();
    h.run();
    assert!(h.state().0.visible().is_empty());
    h.state_mut().0.update(json!({ "filter": { "kinds": ["log", "http", "bridge", "perf"] } })).unwrap();

    // the search cleared: the latest again
    h.state_mut().0.update(json!({ "filter": { "search": "" } })).unwrap();
    h.run();
    assert_eq!(h.state().0.visible(), [1, 2, 3, 4, 5, 6]);
}

#[test]
fn results_for_an_older_search_are_not_shown() {
    let mut h = harness(json!({ "hostSearch": true }), Vec2::new(900.0, 600.0));
    h.state_mut().0.update(json!({ "filter": { "search": "new" } })).unwrap();
    h.run();
    h.state_mut().0.update(json!({ "found": { "text": "old", "entries": [http(1.0, "/x", 200, 1.0, json!(null))] } })).unwrap();
    h.run();
    assert!(h.state().0.visible().is_empty());
    assert!(h.query_by_label("Searching…").is_some());
}

#[test]
fn found_entries_are_checked_like_any() {
    let mut logger = DevLogger::default();
    assert!(logger.update(json!({ "found": { "text": "x", "entries": [{ "kind": "sql", "time": 0 }] } })).is_err());
    assert!(logger.update(json!({ "found": { "text": "x" } })).is_err());
}

#[test]
fn without_host_search_it_searches_what_it_has() {
    let mut h = harness(sample(), Vec2::new(900.0, 600.0));
    h.state_mut().0.update(json!({ "filter": { "search": "locked" } })).unwrap();
    h.run_steps(40);
    assert!(searches(&mut h).is_empty());
    assert_eq!(h.state().0.visible(), [3]);
}
