//! The viewer of data sources: sort, filter, search, rows arriving; and its windows.

use std::collections::BTreeSet;
use std::sync::Arc;
use std::time::{Duration, Instant};

use egui::{Event, Modifiers, PointerButton, Pos2, Vec2};
use egui_kittest::Harness;
use egui_kittest::kittest::Queryable;
use egui_spreadsheet::filter::Condition;
use egui_spreadsheet::source::{DataSource, Rows, Status, Value};
use egui_spreadsheet::viewer::Viewer;
use egui_spreadsheet::viewers::Viewers;

/// id, item, qty, status: 8 rows.
fn orders() -> Rows {
    let rows = Rows::new("orders", ["id", "item", "qty", "status"]);
    let items = ["lamp", "hub", "lamp", "mouse", "hub", "lamp", "mouse", "hub"];
    let status = ["paid", "pending", "refunded"];
    for i in 0..8 {
        rows.push([Value::from(i + 1), Value::from(items[i as usize]), Value::from(i + 1), Value::from(status[i as usize % 3])]);
    }
    rows
}

fn column(v: &Viewer, col: usize) -> Vec<String> {
    (0..v.shown_rows()).map(|r| v.text(r, col)).collect()
}

fn wait_done(source: &dyn DataSource) {
    let start = Instant::now();
    while matches!(source.status(), Status::Loading { .. }) {
        assert!(start.elapsed() < Duration::from_secs(20), "still loading");
        std::thread::sleep(Duration::from_millis(5));
    }
}

#[test]
fn a_csv_loads_in_the_background_with_its_header() {
    let mut csv = String::from("id,name,amount\n");
    for i in 0..100_000 {
        csv.push_str(&format!("{i},name {i},{}.5\n", i % 1000));
    }
    let rows = Rows::load_csv(csv.into_bytes(), "big.csv");
    wait_done(&rows);
    assert_eq!(rows.status(), Status::Done);
    assert_eq!(rows.columns(), ["id", "name", "amount"]);
    assert_eq!(rows.row_count(), 100_000);
    assert_eq!(rows.value(99_999, 2), Value::Number(999.5));
}

#[test]
fn a_broken_csv_says_so_and_keeps_what_came() {
    let rows = Rows::load_csv(b"a,b\n1,2\n\"open quote\n".to_vec(), "bad.csv");
    wait_done(&rows);
    assert!(matches!(rows.status(), Status::Done | Status::Failed(_)));
    assert!(rows.row_count() >= 1);
}

#[test]
fn sort_filter_and_search() {
    let mut v = Viewer::new(orders().source());
    v.refresh();
    assert_eq!(v.shown_rows(), 8);

    v.sort_by(2, true);
    v.refresh();
    assert_eq!(column(&v, 2), ["8", "7", "6", "5", "4", "3", "2", "1"]);
    v.sort_by(1, false);
    v.refresh();
    assert_eq!(column(&v, 1), ["hub", "hub", "hub", "lamp", "lamp", "lamp", "mouse", "mouse"]);
    assert_eq!(column(&v, 0)[..3], ["2", "5", "8"], "ties keep the order they came in");
    assert_eq!(v.source_row(0), 1);

    v.filter(3, Condition::Values(BTreeSet::from(["paid".to_owned()])));
    v.refresh();
    assert_eq!(column(&v, 0), ["1", "4", "7"], "paid (lamp, mouse, mouse), still sorted by item");
    v.search("MOU");
    v.refresh();
    assert_eq!(column(&v, 0), ["4", "7"], "and with mou somewhere");
    v.clear_filters();
    v.clear_sort();
    v.refresh();
    assert_eq!(column(&v, 0), ["4", "7"]);
    v.search("");
    v.refresh();
    assert_eq!(v.shown_rows(), 8);
}

#[test]
fn rows_that_arrive_are_filtered_and_sorted_too() {
    let rows = orders();
    let mut v = Viewer::new(rows.source());
    v.filter(1, Condition::Equals("hub".into()));
    v.refresh();
    assert_eq!(v.shown_rows(), 3);
    rows.push([Value::from(9), Value::from("hub"), Value::from(0), Value::from("paid")]);
    rows.push([Value::from(10), Value::from("lamp"), Value::from(0), Value::from("paid")]);
    v.refresh();
    assert_eq!(column(&v, 0), ["2", "5", "8", "9"], "the new hub, at the end");

    v.sort_by(2, false);
    v.refresh();
    assert_eq!(column(&v, 0), ["9", "2", "5", "8"]);
    rows.push([Value::from(11), Value::from("hub"), Value::from(3), Value::from("paid")]);
    v.refresh();
    assert_eq!(column(&v, 0), ["9", "2", "11", "5", "8"], "in its place");
}

#[test]
fn the_selection_copies_and_counts() {
    let mut v = Viewer::new(orders().source());
    v.refresh();
    v.select((0, 1), (2, 2));
    assert_eq!(v.selection_text(), "lamp\t1\nhub\t2\nlamp\t3\n");
    let stats = v.selection_stats().unwrap();
    assert_eq!((stats.count, stats.numbers, stats.sum, stats.min, stats.max), (6, 3, 6.0, 1.0, 3.0));
}

// ---- on screen ----

fn harness(viewer: Viewer) -> Harness<'static, Viewer> {
    let mut h = Harness::builder()
        .with_size(Vec2::new(900.0, 500.0))
        .with_max_steps(60)
        .with_accessibility_check(false)
        .build_ui_state(|ui, v: &mut Viewer| v.show(ui), viewer);
    h.run();
    h
}

fn click(h: &mut Harness<'static, Viewer>, pos: Pos2) {
    h.hover_at(pos);
    h.step();
    for pressed in [true, false] {
        h.event(Event::PointerButton { pos, button: PointerButton::Primary, pressed, modifiers: Modifiers::NONE });
        h.step();
    }
    h.run();
}

#[test]
fn a_header_click_sorts_then_reverses_then_undoes() {
    let mut h = harness(Viewer::new(orders().source()));
    let header = h.get_by_label("qty").rect().center();
    click(&mut h, header);
    assert_eq!(h.state().sorted_by(), Some((2, false)));
    click(&mut h, header);
    assert_eq!(h.state().sorted_by(), Some((2, true)));
    assert_eq!(column(h.state(), 2)[0], "8");
    click(&mut h, header);
    assert_eq!(h.state().sorted_by(), None);
}

#[test]
fn the_search_box_finds() {
    let mut h = harness(Viewer::new(orders().source()));
    for pressed in [true, false] {
        h.event(Event::Key { key: egui::Key::F, physical_key: None, pressed, repeat: false, modifiers: Modifiers::COMMAND });
        h.step();
    }
    h.run();
    h.event(Event::Text("refund".into()));
    h.run();
    assert_eq!(h.state().shown_rows(), 2);
    assert!(h.query_by_label("2 of 8 rows").is_some());
}

#[test]
fn a_column_menu_filters() {
    let mut h = harness(Viewer::new(orders().source()));
    let button = h.get_by_label("Sort and filter status").rect().center();
    click(&mut h, button);
    h.get_by_label("pending").click();
    h.get_by_label("refunded").click();
    h.run();
    h.get_by_label("OK").click();
    h.run();
    assert_eq!(column(h.state(), 3), ["paid", "paid", "paid"]);
}

// ---- the windows ----

#[test]
fn the_context_window_follows_and_a_pinned_one_stays() {
    let a = orders().source();
    let b: Arc<dyn DataSource> = Rows::new("customers", ["id", "name"]).source();
    let mut views = Viewers::new();
    views.follow(Some(a.clone()));
    views.context_viewer().unwrap().sort_by(2, true);
    views.follow(Some(a.clone()));
    assert_eq!(views.context_viewer().unwrap().sorted_by(), Some((2, true)), "the same context keeps its sort");

    views.open(a.clone());
    assert!(views.is_open(&a));
    assert!(views.context_viewer().is_none(), "pinned: its viewer went with it");
    assert_eq!(views.viewer(&a).unwrap().sorted_by(), Some((2, true)));

    views.follow(Some(b.clone()));
    assert!(views.context_viewer().unwrap().shows(&b));
    assert!(views.is_open(&a), "the pinned one stays");
    views.follow(None);
    assert!(views.context_viewer().is_none());
}

#[test]
fn windows_on_screen_pin_and_close() {
    let a = orders().source();
    let mut h = Harness::builder()
        .with_size(Vec2::new(1200.0, 800.0))
        .with_max_steps(60)
        .with_accessibility_check(false)
        .build_ui_state(
            |ui, (views, source): &mut (Viewers, Arc<dyn DataSource>)| {
                views.follow(Some(source.clone()));
                views.show(ui);
            },
            (Viewers::new(), a.clone()),
        );
    h.run();
    assert!(h.query_by_label("Data: orders").is_some(), "the context window");
    h.get_by_label("Pin").click();
    h.run();
    assert_eq!(h.state().0.pinned(), 1);
    assert!(h.query_by_label("orders is in a window of its own.").is_some());
}

#[test]
fn the_columns_fill_the_window() {
    let h = harness(Viewer::new(orders().source()));
    let last = h.get_by_label("Sort and filter status").rect();
    // the last column ends at the window's right (less the scroll bar), not after its text
    assert!(last.max.x > 860.0, "the last column's button at {}", last.max.x);
}

#[test]
fn a_viewer_from_a_file_or_a_dataset() {
    let dir = std::env::temp_dir().join(format!("egui-spreadsheet-viewer-{}", std::process::id()));
    std::fs::create_dir_all(&dir).unwrap();
    let path = dir.join("people.csv");
    std::fs::write(&path, "name,age\nAna,31\nBen,27\n").unwrap();
    let mut v = egui_spreadsheet::Viewer::open(&path).unwrap();
    wait_done(v.source().as_ref());
    v.refresh();
    assert_eq!(v.source().name(), "people.csv");
    assert_eq!(column(&v, 0), ["Ana", "Ben"]);
    std::fs::remove_dir_all(&dir).unwrap();
    assert!(egui_spreadsheet::Viewer::open(dir.join("missing.csv")).is_err());

    let mut v = egui_spreadsheet::Viewer::from_rows("people", ["name", "age"], [vec![Value::from("Cy"), Value::from(40)]]);
    v.refresh();
    assert_eq!(column(&v, 1), ["40"]);
}
