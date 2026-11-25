//! live-csv: rows pushed with updates, shown as they come, the newest in view.

use egui::Vec2;
use egui_kittest::Harness;
use egui_kittest::kittest::Queryable;
use egui_spreadsheet::DataSource;
use egui4j::live_csv::LiveCsv;
use egui4j::{Component, Events};
use serde_json::json;

#[test]
fn rows_come_as_arrays_or_csv() {
    let mut v = LiveCsv::default();
    v.update(json!({ "name": "import", "columns": ["id", "item"], "append": [[1, "lamp"], [2, null]] })).unwrap();
    v.update(json!({ "csv": "3,hub\n4,\"a, quoted\"\n" })).unwrap();
    let rows = v.rows();
    assert_eq!(rows.name(), "import");
    assert_eq!(rows.row_count(), 4);
    assert_eq!(rows.value(3, 1).text(), "a, quoted");
    assert!(rows.value(1, 1).is_empty());
}

#[test]
fn the_first_csv_line_names_the_columns_when_there_are_none() {
    let mut v = LiveCsv::default();
    v.update(json!({ "csv": "time\tlevel\n1\tinfo\n", "separator": "\t" })).unwrap();
    assert_eq!(v.rows().columns(), ["time", "level"]);
    assert_eq!(v.rows().row_count(), 1);
}

#[test]
fn a_bad_update_changes_nothing() {
    let mut v = LiveCsv::default();
    v.update(json!({ "columns": ["a"], "append": [[1]] })).unwrap();
    assert!(v.update(json!({ "append": [[2]], "follow": "yes" })).is_err());
    assert!(v.update(json!({ "append": [[{ "no": 1 }]] })).is_err());
    assert!(v.update(json!({ "append": [[3]], "separator": ";;" })).is_err());
    assert_eq!(v.rows().row_count(), 1);
    v.update(json!({ "clear": true, "append": [[9]] })).unwrap();
    assert_eq!(v.rows().row_count(), 1);
    assert_eq!(v.rows().value(0, 0).text(), "9");
}

#[test]
fn the_newest_row_stays_in_view() {
    let mut h = Harness::builder().with_size(Vec2::new(700.0, 300.0)).with_max_steps(60).with_accessibility_check(false).build_ui_state(
        |ui, v: &mut LiveCsv| v.ui(ui, &mut Events::default()),
        LiveCsv::default(),
    );
    h.state_mut().update(json!({ "columns": ["seq", "message"], "progress": null })).unwrap();
    // loading: its bar moves, so it never settles; a few frames at a time
    h.run_steps(3);
    for batch in 0..20 {
        let rows: Vec<_> = (0..10).map(|i| json!([batch * 10 + i, format!("row {}", batch * 10 + i)])).collect();
        h.state_mut().update(json!({ "append": rows })).unwrap();
        h.run_steps(3);
        // the newest row is drawn, the first ones are scrolled away
        let visible = h.state_mut().viewer().visible_rows();
        let total = (batch + 1) as usize * 10;
        assert_eq!(visible.end, total, "after {total} rows: {visible:?}");
    }
    assert!(h.state_mut().viewer().visible_rows().start > 100);

    // following off: new rows come below, out of view
    h.state_mut().update(json!({ "follow": false })).unwrap();
    h.state_mut().update(json!({ "append": (0..50).map(|i| json!([i, "more"])).collect::<Vec<_>>() })).unwrap();
    h.run_steps(3);
    assert_eq!(h.state_mut().viewer().visible_rows().end, 200);
    let _ = h.query_by_label("Follow");
}
