//! The monitor with a config of its own (orders, not logs): reading messages, the statistics, groups, charts,
//! filters, details, layout, and configs it refuses.

use egui::Vec2;
use egui_kittest::Harness;
use egui_kittest::kittest::Queryable;
use egui4j::monitor::Monitor;
use egui4j::monitor::config::{self, Agg, Format, Rule, fill, format_number, get, parse_every, parse_time};
use egui4j::monitor::stats::{self, Message, Series};
use egui4j::{Component, Events};
use serde_json::{Value, json};

type State = (Monitor, Events);

/// Orders: ISO times, a nested customer, an amount, a status; failed when status is "failed".
fn orders_config() -> Value {
    json!({
        "title": "Orders",
        "time": "at",
        "facet": "status",
        "error": { "field": "status", "eq": "failed" },
        "columns": [
            { "label": "When", "field": "at", "format": "since", "width": 70 },
            { "label": "Order", "text": [{ "text": "#{id} {customer.name}" }], "summary": true },
            { "label": "Amount", "field": "amount", "format": "number", "width": 80, "warnAbove": 1000 },
            { "label": "Status", "field": "status", "width": 80, "colors": [{ "when": { "error": true }, "color": "error" }] }
        ],
        "summary": [
            { "label": "orders", "agg": "count", "text": "{value} orders" },
            { "label": "failed", "agg": "errorRate", "text": "{value} failed" },
            { "label": "biggest", "agg": "max", "field": "amount", "link": true }
        ],
        "stats": [
            { "label": "total", "agg": "sum", "field": "amount" },
            { "label": "average", "agg": "avg", "field": "amount" },
            { "label": "a second", "agg": "rate" },
            { "label": "paid", "agg": "count", "where": { "field": "status", "eq": "paid" } }
        ],
        "groupBy": ["customer.country"],
        "groupValue": "amount",
        "charts": [
            { "type": "line", "agg": "sum", "y": "amount", "every": "1s" },
            { "type": "bar", "x": "status", "agg": "count" }
        ],
        "details": [
            { "tab": "Order", "json": "" },
            { "tab": "Customer", "tables": [{ "title": "Customer", "field": "customer" }] }
        ]
    })
}

fn order(id: u32, second: u32, name: &str, country: &str, amount: f64, status: &str) -> Value {
    json!({ "id": id, "at": format!("2026-11-25T06:00:{second:02}Z"), "amount": amount, "status": status,
            "customer": { "name": name, "country": country } })
}

fn orders() -> Value {
    json!([
        order(1, 0, "Ana", "PH", 120.0, "paid"),
        order(2, 0, "Ben", "US", 80.0, "paid"),
        order(3, 1, "Cai", "PH", 2500.0, "failed"),
        order(4, 2, "Dee", "US", 40.0, "paid"),
        order(5, 3, "Eli", "JP", 300.0, "pending")
    ])
}

fn monitor() -> Monitor {
    let mut m = Monitor::default();
    m.update(json!({ "config": orders_config(), "append": orders() })).expect("valid");
    m
}

fn harness(m: Monitor) -> Harness<'static, State> {
    let mut h = Harness::builder()
        .with_size(Vec2::new(1000.0, 700.0))
        .with_step_dt(1.0 / 60.0)
        .with_max_steps(120)
        // egui_dock's splitters have no accessible name
        .with_accessibility_check(false)
        .build_ui_state(|ui, (m, events): &mut State| m.ui(ui, events), (m, Events::default()));
    h.run();
    h
}

// ---- reading messages ----

#[test]
fn paths_reach_into_objects_and_arrays() {
    let v = json!({ "a": { "b": [10, { "c": "x" }] } });
    assert_eq!(get(&v, "a.b.0"), Some(&json!(10)));
    assert_eq!(get(&v, "a.b.1.c"), Some(&json!("x")));
    assert_eq!(get(&v, "a.nope"), None);
    assert_eq!(get(&v, "a.b.9"), None);
    assert_eq!(get(&v, ""), Some(&v));
}

#[test]
fn templates_fill_fields() {
    let v = json!({ "level": "warn", "n": 3, "user": { "name": "Ana" } });
    assert_eq!(fill("{level|upper}: {n} for {user.name}{missing}", &v), "WARN: 3 for Ana");
    assert_eq!(fill("no fields, {unclosed", &v), "no fields, {unclosed");
}

#[test]
fn rules_hold_or_not() {
    let v = json!({ "status": 503, "kind": "HTTP", "error": "", "msg": "Database Locked" });
    let rule = |r: Value| serde_json::from_value::<Rule>(r).unwrap().holds(&v, &|| false);
    assert!(rule(json!({ "field": "status", "gte": 500 })));
    assert!(!rule(json!({ "field": "status", "lt": 500 })));
    assert!(rule(json!({ "field": "kind", "eq": "http" })), "text compares ignoring case");
    assert!(rule(json!({ "field": "kind", "in": ["log", "http"] })));
    assert!(!rule(json!({ "field": "error", "exists": true })), "empty text is not there");
    assert!(rule(json!({ "field": "msg", "contains": "locked" })));
    assert!(rule(json!({ "any": [{ "field": "status", "eq": 200 }, { "field": "status", "eq": 503 }] })));
    assert!(!rule(json!({ "all": [{ "field": "status", "eq": 503 }, { "field": "kind", "eq": "log" }] })));
    assert!(rule(json!({ "not": { "field": "kind", "eq": "log" } })));
    assert!(rule(json!({})), "no condition holds");
}

#[test]
fn times_are_milliseconds_or_iso() {
    assert_eq!(parse_time(&json!(1_732_500_000_123_u64)), Some(1_732_500_000_123.0));
    assert_eq!(parse_time(&json!("1970-01-01T00:00:01Z")), Some(1000.0));
    assert_eq!(parse_time(&json!("2026-11-25T06:12:30.250Z")), parse_time(&json!("2026-11-25T14:12:30.250+08:00")));
    assert_eq!(parse_time(&json!("2000-03-01T00:00:00Z")), Some(951_868_800_000.0));
    assert_eq!(parse_time(&json!("yesterday")), None);
    assert_eq!(parse_time(&json!("2026-13-01T00:00:00Z")), None);
}

#[test]
fn numbers_read_as_described() {
    assert_eq!(format_number(42.4, Format::Duration), "42 ms");
    assert_eq!(format_number(1234.0, Format::Duration), "1.23 s");
    assert_eq!(format_number(1536.0, Format::Bytes), "1.5 KB");
    assert_eq!(format_number(0.125, Format::Percent), "12.5%");
    assert_eq!(format_number(3.0, Format::Number), "3");
    assert_eq!(format_number(12.3456, Format::Number), "12.35");
    assert_eq!(parse_every("500ms"), Some(500.0));
    assert_eq!(parse_every("2m"), Some(120_000.0));
    assert_eq!(parse_every("soon"), None);
}

// ---- statistics ----

fn messages(values: &[f64]) -> Vec<Message> {
    values.iter().enumerate().map(|(i, v)| Message { id: i as u64 + 1, time: i as f64 * 500.0, value: json!({ "v": v }), error: *v > 100.0 }).collect()
}

#[test]
fn aggregates() {
    let list = messages(&[10.0, 20.0, 30.0, 40.0, 500.0]);
    let refs: Vec<&Message> = list.iter().collect();
    let at = |agg| stats::aggregate(agg, Some("v"), &refs);
    assert_eq!(at(Agg::Count).0, Some(5.0));
    assert_eq!(at(Agg::ErrorCount).0, Some(1.0));
    assert_eq!(at(Agg::ErrorRate).0, Some(0.2));
    assert_eq!(at(Agg::Sum).0, Some(600.0));
    assert_eq!(at(Agg::Avg).0, Some(120.0));
    assert_eq!(at(Agg::Min), (Some(10.0), Some(1)));
    assert_eq!(at(Agg::Max), (Some(500.0), Some(5)), "max names its message");
    assert_eq!(at(Agg::P50).0, Some(30.0));
    assert_eq!(at(Agg::P95).0, Some(500.0));
    // 5 messages over 2 seconds
    assert_eq!(at(Agg::Rate).0, Some(2.5));
    let none: Vec<&Message> = Vec::new();
    assert_eq!(stats::aggregate(Agg::Avg, Some("v"), &none).0, None);
    assert_eq!(stats::aggregate(Agg::Rate, None, &none).0, None);
}

#[test]
fn a_window_counts_only_the_latest_seconds() {
    let list = messages(&[1.0, 2.0, 3.0, 4.0, 5.0]);
    // times 0, 500, 1000, 1500, 2000: the last second is 1000..2000
    let scope = stats::in_scope(&list, None, Some(1.0));
    assert_eq!(scope.iter().map(|m| m.id).collect::<Vec<_>>(), [3, 4, 5]);
}

#[test]
fn the_orders_add_up() {
    let mut m = monitor();
    assert_eq!(m.summary_stat("orders"), Some(5.0));
    assert_eq!(m.summary_stat("failed"), Some(0.2));
    assert_eq!(m.summary_stat("biggest"), Some(2500.0));
    assert_eq!(m.stat("total"), Some(3040.0));
    assert_eq!(m.stat("average"), Some(608.0));
    assert_eq!(m.stat("paid"), Some(3.0));
    // 5 orders from second 0 to second 3
    assert_eq!(m.stat("a second"), Some(5.0 / 3.0));
}

#[test]
fn groups_by_nested_fields() {
    let mut m = monitor();
    let groups = m.groups();
    let shape: Vec<(String, usize, usize)> = groups.iter().map(|g| (g.key.clone(), g.count, g.errors)).collect();
    assert_eq!(shape, [("PH".into(), 2, 1), ("US".into(), 2, 0), ("JP".into(), 1, 0)]);
    assert_eq!(groups[0].max, Some(2500.0));
}

#[test]
fn charts_bucket_time_and_count_values() {
    let mut m = monitor();
    assert_eq!(
        m.chart(0),
        Some(Series::Lines(vec![(String::new(), vec![[0.0, 200.0], [1.0, 2500.0], [2.0, 40.0], [3.0, 300.0]])]))
    );
    assert_eq!(
        m.chart(1),
        Some(Series::Bars(vec![("failed".into(), 1.0), ("paid".into(), 3.0), ("pending".into(), 1.0)]))
    );
}

// ---- props ----

#[test]
fn a_new_error_rule_judges_the_kept_messages_again() {
    let mut m = monitor();
    let mut c = orders_config();
    c["error"] = json!({ "field": "status", "ne": "paid" });
    m.update(json!({ "config": c })).unwrap();
    let errors: Vec<u64> = m.messages().filter(|x| x.error).map(|x| x.id).collect();
    assert_eq!(errors, [3, 5]);
}

#[test]
fn keeps_the_latest_max_messages() {
    let mut m = Monitor::default();
    m.update(json!({ "config": { "maxMessages": 2 }, "append": orders() })).unwrap();
    assert_eq!(m.messages().map(|x| x.id).collect::<Vec<_>>(), [4, 5]);
}

#[test]
fn without_a_time_field_messages_get_their_arrival() {
    let mut m = Monitor::default();
    m.update(json!({ "append": [{ "a": 1 }] })).unwrap();
    let t = m.messages().next().unwrap().time;
    assert!(t > 1_700_000_000_000.0, "{t}");
}

#[test]
fn bad_configs_are_refused_and_change_nothing() {
    let mut m = monitor();
    for bad in [
        json!({ "config": { "colums": [] } }),
        json!({ "config": { "columns": [{ "label": "x" }] } }),
        json!({ "config": { "columns": [{ "field": "a", "colors": [{ "when": {}, "color": "bluish" }] }] } }),
        json!({ "config": { "stats": [{ "label": "p95", "agg": "p95" }] } }),
        json!({ "config": { "stats": [{ "label": "x", "agg": "median", "field": "a" }] } }),
        json!({ "config": { "charts": [{ "type": "line", "every": "often" }] } }),
        json!({ "config": { "charts": [{ "type": "bar" }] } }),
        json!({ "config": { "charts": [{ "type": "pie", "x": "a" }] } }),
        json!({ "config": { "maxMessages": 0 } }),
        json!({ "config": { "windowSeconds": -5 } }),
        json!({ "config": { "error": { "field": "a", "eqq": 1 } } }),
        json!({ "layout": { "not": "a layout" } }),
        json!({ "apend": [] }),
    ] {
        assert!(m.update(bad.clone()).is_err(), "took {bad}");
    }
    assert_eq!(m.config().title, "Orders");
    assert_eq!(m.messages().count(), 5);
}

// ---- the window ----

#[test]
fn shows_the_columns_described() {
    let h = harness(monitor());
    assert!(h.query_by_label("#3 Cai").is_some());
    // in the list (and in the Stats pane, as the biggest)
    assert!(h.query_all_by_label("2500").count() >= 1);
    assert!(h.query_by_label("When").is_some());
    assert!(h.query_by_label("5 orders").is_some());
    assert!(h.query_by_label("20.0% failed").is_some());
}

#[test]
fn facet_toggles_count_and_hide() {
    let mut h = harness(monitor());
    assert_eq!(h.state().0.visible(), [1, 2, 3, 4, 5]);
    h.get_by_label("paid 3").click();
    h.run();
    assert_eq!(h.state().0.visible(), [3, 5]);
}

#[test]
fn errors_only_and_search() {
    let mut h = harness(monitor());
    h.get_by_label("Errors").click();
    h.run();
    assert_eq!(h.state().0.visible(), [3]);
    h.state_mut().0.update(json!({ "filter": { "errorsOnly": false, "search": "eli" } })).unwrap();
    h.run();
    assert_eq!(h.state().0.visible(), [5]);
}

#[test]
fn a_group_chosen_filters_the_list() {
    let mut h = harness(monitor());
    h.get_by_label("Groups").click();
    h.run();
    h.state_mut().1.drain();
    h.get_by_label("PH").click();
    h.run();
    assert_eq!(h.state_mut().1.drain(), [json!({ "event": "group", "key": "PH" })]);
    assert_eq!(h.state().0.visible(), [1, 3]);
    assert!(h.query_by_label("Group: PH").is_some());
    h.get_by_label("All groups").click();
    h.run();
    assert_eq!(h.state().0.visible().len(), 5);
}

#[test]
fn the_biggest_order_is_a_link_to_it() {
    let mut h = harness(monitor());
    h.get_by_label("biggest 2500 · #3 Cai").click();
    h.run();
    assert_eq!(h.state().0.selected(), Some(3));
}

#[test]
fn details_show_the_tabs_described() {
    let mut h = harness(monitor());
    assert!(h.query_by_label("Choose a row to see it here").is_some());
    h.get_by_label("#5 Eli").click();
    h.run();
    // "Order": the whole message as a tree
    assert!(h.query_by_label("customer:").is_some());
    h.get_by_label("Customer").click();
    h.run();
    assert!(h.query_by_label("country").is_some());
    assert!(h.query_by_label("JP").is_some());
}

#[test]
fn with_no_config_it_shows_messages_as_json() {
    let mut m = Monitor::default();
    m.update(json!({ "append": [{ "hello": "world" }] })).unwrap();
    let h = harness(m);
    assert!(h.query_by_label(r#"{"hello":"world"}"#).is_some());
}

#[test]
fn a_layout_given_back_is_restored() {
    let mut m = monitor();
    let mut layout = m.layout();
    // move the right-hand leaf's tabs about: Charts first
    let text = layout.to_string().replace(r#"["Stats","Groups","Charts"]"#, r#"["Charts","Stats","Groups"]"#);
    layout = serde_json::from_str(&text).unwrap();
    m.update(json!({ "layout": layout })).unwrap();
    assert!(m.layout().to_string().contains(r#"["Charts","Stats","Groups"]"#));
    // and it draws that way: the Charts pane is the one shown
    let h = harness(m);
    assert!(h.query_by_label("sum of amount").is_some());
}

#[test]
fn stats_pane_lists_everything() {
    let mut h = harness(monitor());
    h.get_by_label("Stats").click();
    h.run();
    for label in ["orders", "total", "average", "a second", "paid"] {
        // "paid" is also in the list's Status column
        assert!(h.query_all_by_label(label).count() >= 1, "{label}");
    }
    assert!(h.query_by_label("3040").is_some());
}

#[test]
fn survives_no_room_and_empty() {
    let mut h = harness(Monitor::default());
    for size in [Vec2::ZERO, Vec2::new(2.0, 2.0), Vec2::new(3000.0, 30.0), Vec2::new(900.0, 600.0)] {
        h.set_size(size);
        h.run();
    }
    assert!(h.query_by_label("Nothing yet").is_some());
}

#[test]
fn rules_parse_from_config_json() {
    let c: config::Config = serde_json::from_value(orders_config()).unwrap();
    assert_eq!(c.columns.len(), 4);
    assert!(config::validate(&c).is_ok());
}
