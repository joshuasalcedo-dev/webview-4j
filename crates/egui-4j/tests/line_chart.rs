//! The line chart, headless: props in, events out, and the edge cases (no data, one point, huge values, no room).

use egui::{Event, Modifiers, PointerButton, Pos2, Vec2};
use egui4j::{Component, Events, Registry, line_chart::LineChart};
use egui_kittest::Harness;
use serde_json::{Value, json};

type State = (LineChart, Events);

fn harness(props: Value) -> Harness<'static, State> {
    let mut chart = LineChart::default();
    chart.update(props).expect("valid props");
    // 60 frames a second, as in a window (the harness's default 0.25 s a frame is too slow for a double-click)
    let mut harness = Harness::builder()
        .with_size(Vec2::new(800.0, 500.0))
        .with_step_dt(1.0 / 60.0)
        .with_max_steps(120)
        .build_ui_state(|ui, (chart, events): &mut State| chart.ui(ui, events), (chart, Events::default()));
    harness.run();
    harness
}

fn events(harness: &mut Harness<'static, State>) -> Vec<Value> {
    harness.state_mut().1.drain()
}

fn names(events: &[Value]) -> Vec<&str> {
    events.iter().map(|e| e["event"].as_str().unwrap()).collect()
}

fn press(harness: &mut Harness<'static, State>, pos: Pos2, pressed: bool) {
    harness.event(Event::PointerButton { pos, button: PointerButton::Primary, pressed, modifiers: Modifiers::NONE });
    harness.step();
}

fn click(harness: &mut Harness<'static, State>, pos: Pos2) {
    harness.hover_at(pos);
    harness.step();
    press(harness, pos, true);
    press(harness, pos, false);
    harness.run();
}

fn three_points() -> Value {
    json!({ "series": [{ "name": "a", "points": [[0, 0], [5, 10], [10, 0]], "color": "#0a84ff" }] })
}

// ---- props ----

#[test]
fn series_replaces_every_line() {
    let mut chart = LineChart::default();
    chart.update(json!({ "series": [{ "name": "a", "points": [[0, 1]] }, { "name": "b" }] })).unwrap();
    chart.update(json!({ "series": [{ "name": "c", "points": [[0, 1], [1, 2]] }] })).unwrap();
    let names: Vec<_> = chart.series().iter().map(|s| s.name.as_str()).collect();
    assert_eq!(names, ["c"]);
    assert_eq!(chart.series()[0].points.len(), 2);
}

#[test]
fn append_extends_a_line_or_starts_one() {
    let mut chart = LineChart::default();
    chart.update(json!({ "series": [{ "name": "a", "points": [[0, 1]] }] })).unwrap();
    chart.update(json!({ "append": [{ "series": "a", "points": [[1, 2]] }, { "series": "new", "points": [[0, 5]] }] })).unwrap();
    assert_eq!(chart.series()[0].points.len(), 2);
    assert_eq!(chart.series()[1].name, "new");
    assert_eq!(chart.series()[1].points[0].y, 5.0);
}

#[test]
fn max_points_keeps_the_latest() {
    let mut chart = LineChart::default();
    chart.update(json!({ "maxPoints": 3 })).unwrap();
    for i in 0..10 {
        chart.update(json!({ "append": [{ "series": "s", "points": [[i, i]] }] })).unwrap();
    }
    let xs: Vec<f64> = chart.series()[0].points.iter().map(|p| p.x).collect();
    assert_eq!(xs, [7.0, 8.0, 9.0]);
}

#[test]
fn what_is_left_out_stays() {
    let mut chart = LineChart::default();
    chart.update(three_points()).unwrap();
    chart.update(json!({ "xLabel": "seconds" })).unwrap();
    assert_eq!(chart.series().len(), 1);
    assert_eq!(chart.series()[0].points.len(), 3);
}

#[test]
fn bad_props_are_refused_and_change_nothing() {
    let mut chart = LineChart::default();
    chart.update(three_points()).unwrap();
    let before = chart.series().to_vec();

    let typo = chart.update(json!({ "maxPonts": 10 })).unwrap_err();
    assert!(typo.contains("maxPonts"), "{typo}");
    let colour = chart.update(json!({ "series": [{ "name": "x", "color": "blue" }] })).unwrap_err();
    assert!(colour.contains("not a colour"), "{colour}");
    assert!(chart.update(json!({ "maxPoints": 0 })).is_err());
    assert!(chart.update(json!({ "series": [{ "name": "x", "points": [[1]] }] })).is_err());
    assert!(chart.update(json!("not an object")).is_err());

    assert_eq!(chart.series(), &before[..]);
}

#[test]
fn the_registry_creates_it_by_name() {
    let registry = Registry::default();
    assert!(registry.names().any(|n| n == "line-chart"));
    assert!(registry.create("line-chart").is_some());
    assert!(registry.create("nope").is_none());
}

// ---- drawing ----

#[test]
fn draws_with_no_data_one_point_and_huge_values() {
    for props in [
        json!({}),
        json!({ "series": [] }),
        json!({ "series": [{ "name": "one", "points": [[1, 1]] }] }),
        json!({ "series": [{ "name": "flat", "points": [[0, 5], [1, 5], [2, 5]] }] }),
        json!({ "series": [{ "name": "huge", "points": [[-1e300, -1e300], [1e300, 1e300]] }] }),
        json!({ "series": [{ "name": "tiny", "points": [[0, 1e-300], [1e-300, 0]] }] }),
    ] {
        let mut h = harness(props.clone());
        h.run();
        assert!(h.state().0.view().is_some(), "not drawn: {props}");
    }
}

#[test]
fn survives_having_no_room() {
    let mut h = harness(three_points());
    for size in [Vec2::ZERO, Vec2::new(1.0, 1.0), Vec2::new(5000.0, 3.0), Vec2::new(800.0, 500.0)] {
        h.set_size(size);
        h.run();
    }
    assert!(h.state().0.view().is_some());
}

#[test]
fn fits_its_data() {
    let h = harness(three_points());
    let [x_min, x_max, y_min, y_max] = h.state().0.view().unwrap();
    assert!(x_min <= 0.0 && x_max >= 10.0 && y_min <= 0.0 && y_max >= 10.0, "{:?}", h.state().0.view());
}

// ---- events ----

#[test]
fn a_click_on_a_point_names_it() {
    let mut h = harness(three_points());
    events(&mut h);
    let pos = h.state().0.screen_position(5.0, 10.0).unwrap();
    click(&mut h, pos + Vec2::new(3.0, 2.0));

    let got = events(&mut h);
    assert_eq!(names(&got), ["click"], "{got:?}");
    let nearest = &got[0]["nearest"];
    assert_eq!(nearest["series"], "a");
    assert_eq!(nearest["index"], 1);
    assert_eq!(nearest["x"], 5.0);
    assert!((got[0]["x"].as_f64().unwrap() - 5.0).abs() < 0.5, "{got:?}");
}

#[test]
fn a_click_away_from_the_points_has_no_nearest() {
    let mut h = harness(three_points());
    let pos = h.state().0.screen_position(5.0, 2.0).unwrap();
    click(&mut h, pos);
    let got = events(&mut h);
    assert_eq!(names(&got), ["click"]);
    assert!(got[0]["nearest"].is_null(), "{got:?}");
}

#[test]
fn a_pan_reports_the_view_once_the_drag_ends() {
    let mut h = harness(three_points());
    events(&mut h);
    let from = h.state().0.screen_position(5.0, 5.0).unwrap();

    h.hover_at(from);
    h.step();
    press(&mut h, from, true);
    for i in 1..=10 {
        h.hover_at(from + Vec2::new(i as f32 * 10.0, 0.0));
        h.step();
    }
    assert!(!names(&events(&mut h)).contains(&"view"), "reported while dragging");

    press(&mut h, from + Vec2::new(100.0, 0.0), false);
    h.run();
    let got = events(&mut h);
    let views: Vec<_> = got.iter().filter(|e| e["event"] == "view").collect();
    assert_eq!(views.len(), 1, "{got:?}");
    // dragged right: the view moved left
    assert!(views[0]["xMin"].as_f64().unwrap() < -0.5, "{got:?}");
}

#[test]
fn new_data_moves_the_view_without_a_view_event() {
    let mut h = harness(three_points());
    events(&mut h);
    h.state_mut().0.update(json!({ "append": [{ "series": "a", "points": [[100, 50]] }] })).unwrap();
    h.run();
    assert!(h.state().0.view().unwrap()[1] >= 100.0, "did not follow the data");
    assert!(events(&mut h).is_empty());
}

#[test]
fn a_double_click_fits_the_data_again() {
    let mut h = harness(three_points());
    let center = h.state().0.screen_position(5.0, 5.0).unwrap();
    // zoom in with the wheel, so the view is the user's
    h.hover_at(center);
    h.step();
    h.event(Event::MouseWheel { unit: egui::MouseWheelUnit::Line, delta: Vec2::new(0.0, 5.0), modifiers: Modifiers::CTRL, phase: egui::TouchPhase::Move, source: egui::MouseWheelSource::Wheel });
    h.run();
    assert!(names(&events(&mut h)).contains(&"view"), "zooming reported no view");

    for _ in 0..2 {
        press(&mut h, center, true);
        press(&mut h, center, false);
    }
    h.run();
    let got = events(&mut h);
    // the reset is the last thing said: fitting the data again is not the user choosing a view
    assert_eq!(names(&got).last(), Some(&"reset"), "{got:?}");
    h.run_steps(30);
    assert!(events(&mut h).is_empty(), "something reported after the reset");
    let [x_min, x_max, ..] = h.state().0.view().unwrap();
    assert!(x_min <= 0.0 && x_max >= 10.0, "did not fit the data again");
}
