//! Columns, rows, filters, tables and validation: through the API, and through the grid where they have a UI.

use std::collections::BTreeSet;

use egui::{Event, Key, Modifiers, PointerButton, Pos2, Vec2};
use egui_kittest::Harness;
use egui_kittest::kittest::Queryable;
use egui_spreadsheet::filter::Condition;
use egui_spreadsheet::table::Table;
use egui_spreadsheet::validation::{Rule, Validation};
use egui_spreadsheet::{Output, Spreadsheet};

/// Orders: a header, then 8 rows (status cycles paid, pending, refunded; qty 1..=8).
fn orders() -> Spreadsheet {
    let mut s = Spreadsheet::from_csv(
        b"id,item,qty,status\n1,lamp,1,paid\n2,hub,2,pending\n3,lamp,3,refunded\n4,mouse,4,paid\n5,hub,5,pending\n6,lamp,6,refunded\n7,mouse,7,paid\n8,hub,8,pending\n",
        "orders.csv",
    )
    .unwrap();
    s.set(0, 12, 1, "below the data").unwrap();
    s
}

fn hidden(s: &Spreadsheet) -> Vec<i32> {
    (1..=12).filter(|r| s.is_row_hidden(0, *r)).collect()
}

type State = (Spreadsheet, Vec<Output>);

fn harness(sheet: Spreadsheet) -> Harness<'static, State> {
    let mut h = Harness::builder()
        .with_size(Vec2::new(1000.0, 700.0))
        .with_step_dt(1.0 / 60.0)
        .with_max_steps(120)
        .with_accessibility_check(false)
        .build_ui_state(
            |ui, (sheet, outs): &mut State| {
                let out = sheet.show(ui);
                if out != Output::default() {
                    outs.push(out);
                }
            },
            (sheet, Vec::new()),
        );
    h.run();
    h
}

fn click(h: &mut Harness<'static, State>, pos: Pos2) {
    h.hover_at(pos);
    h.step();
    for pressed in [true, false] {
        h.event(Event::PointerButton { pos, button: PointerButton::Primary, pressed, modifiers: Modifiers::NONE });
        h.step();
    }
    h.run();
}

fn key(h: &mut Harness<'static, State>, key: Key) {
    for pressed in [true, false] {
        h.event(Event::Key { key, physical_key: None, pressed, repeat: false, modifiers: Modifiers::NONE });
        h.step();
    }
    h.run();
}

// ---- columns and rows ----

#[test]
fn columns_size_hide_insert_delete_and_fit() {
    let mut s = orders();
    s.set_column_width(0, 2, 3, 150.0).unwrap();
    assert_eq!((s.column_width(0, 2), s.column_width(0, 3)), (150.0, 150.0));
    assert!(s.set_column_width(0, 1, 1, 0.0).is_err());

    s.set(0, 10, 3, "=SUM(C2:C9)").unwrap();
    s.insert_columns(0, 1, 1).unwrap();
    assert_eq!(s.text(0, 1, 2), "id", "moved right");
    assert_eq!(s.content(0, 10, 4), "=SUM(D2:D9)", "the formula follows");
    s.delete_columns(0, 1, 1).unwrap();
    assert_eq!(s.text(0, 1, 1), "id");

    s.hide_columns(0, 2, 2, true).unwrap();
    assert!(s.is_column_hidden(0, 2));
    s.hide_columns(0, 2, 2, false).unwrap();
    assert!(!s.is_column_hidden(0, 2));

    s.set(0, 3, 2, "a long piece of text in the item column").unwrap();
    s.autofit_column(0, 2).unwrap();
    assert!(s.column_width(0, 2) > 250.0, "{}", s.column_width(0, 2));
}

#[test]
fn rows_size_hide_insert_delete() {
    let mut s = orders();
    s.set_row_height(0, 2, 2, 40.0).unwrap();
    assert_eq!(s.row_height(0, 2), 40.0);
    s.hide_rows(0, 3, 4, true).unwrap();
    assert_eq!(hidden(&s), [3, 4]);
    s.hide_rows(0, 3, 4, false).unwrap();
    s.insert_rows(0, 2, 2).unwrap();
    assert_eq!(s.text(0, 4, 2), "lamp", "row 2 is now row 4");
    s.delete_rows(0, 2, 2).unwrap();
    assert_eq!(s.text(0, 2, 2), "lamp");
}

#[test]
fn the_arrows_skip_hidden_rows() {
    let mut s = orders();
    s.hide_rows(0, 3, 5, true).unwrap();
    let mut h = harness(s);
    let at = h.state().0.cell_center(2, 1).unwrap();
    click(&mut h, at);
    key(&mut h, Key::ArrowDown);
    assert_eq!(h.state().0.cursor(), (6, 1));
    key(&mut h, Key::ArrowUp);
    assert_eq!(h.state().0.cursor(), (2, 1));
}

// ---- filters ----

#[test]
fn a_filter_hides_the_rows_that_do_not_match() {
    let mut s = orders();
    s.filter(0, 4, Condition::Values(BTreeSet::from(["paid".to_owned()]))).unwrap();
    assert_eq!(hidden(&s), [3, 4, 6, 7, 9], "only the paid rows (2, 5, 8) show");
    assert!(!s.is_row_hidden(0, 1), "the header stays");

    // a second column's condition: both must hold
    s.filter(0, 3, Condition::GreaterThan(3.0)).unwrap();
    assert_eq!(hidden(&s), [2, 3, 4, 6, 7, 9]);
    s.clear_filter(0, 3).unwrap();
    assert_eq!(hidden(&s), [3, 4, 6, 7, 9]);
    s.clear_filters(0).unwrap();
    assert!(hidden(&s).is_empty());

    s.filter(0, 2, Condition::Contains("LAMP".into())).unwrap();
    assert_eq!(hidden(&s), [3, 5, 6, 8, 9], "the data under the header only: not the blank rows after it, not row 12");
}

#[test]
fn a_filter_offers_its_columns_values() {
    let mut s = orders();
    s.set_filter_range(0, 1, 1, 4).unwrap();
    assert_eq!(s.filter_values(0, 4), ["paid", "pending", "refunded"], "the data under the header: no blank past its end");
    assert!(s.filter(0, 9, Condition::Blank).is_err(), "not one of the filter's columns");
}

#[test]
fn the_filter_button_opens_its_menu() {
    let mut s = orders();
    s.add_table(Table::at("Orders", 0, "A1:D9").unwrap()).unwrap();
    let mut h = harness(s);
    let cell = h.state().0.cell_screen_rect(1, 4).unwrap();
    click(&mut h, egui::pos2(cell.max.x - 10.0, cell.center().y));
    // untick "pending" and "refunded": only paid
    h.get_by_label("pending").click();
    h.get_by_label("refunded").click();
    h.run();
    h.get_by_label("OK").click();
    h.run();
    assert_eq!(hidden(&h.state().0), [3, 4, 6, 7, 9]);
    assert!(h.query_by_label("OK").is_none(), "the menu closed");
}

// ---- tables ----

#[test]
fn tables_are_named_and_do_not_overlap() {
    let mut s = orders();
    s.add_table(Table::at("Orders", 0, "A1:D9").unwrap()).unwrap();
    assert_eq!(s.table("orders").unwrap().reference(), "A1:D9");
    assert!(s.add_table(Table::at("ORDERS", 0, "F1:G3").unwrap()).is_err(), "the name is taken");
    assert!(s.add_table(Table::at("Other", 0, "C5:F6").unwrap()).is_err(), "it overlaps");
    assert!(s.add_table(Table::at("Other", 0, "F1:G3").unwrap().no_filter()).is_ok());
    assert_eq!(s.table_at(0, 5, 2).map(|t| t.name.as_str()), Some("Orders"));
    assert!(s.table_at(0, 10, 2).is_none());
}

#[test]
fn a_tables_filter_stays_in_the_table() {
    let mut s = orders();
    s.add_table(Table::at("Orders", 0, "A1:D9").unwrap()).unwrap();
    s.filter(0, 4, Condition::Equals("pending".into())).unwrap();
    assert_eq!(hidden(&s), [2, 4, 5, 7, 8], "row 12, outside the table, stays");
    s.remove_table("Orders").unwrap();
    assert!(hidden(&s).is_empty(), "its filter went with it");
    assert!(s.filter_range(0).is_none());
}

// ---- validation ----

#[test]
fn a_refused_value_is_not_written_and_says_why() {
    let mut s = orders();
    s.add_validation(Validation::at(0, "C2:C9", Rule::WholeNumber { min: Some(1), max: Some(99) }).unwrap());
    assert!(s.check(0, 2, 3, "12").is_ok());
    assert!(s.check(0, 2, 3, "0").is_err());
    let mut h = harness(s);
    let at = h.state().0.cell_center(2, 3).unwrap();
    click(&mut h, at);
    h.event(Event::Text("0".into()));
    h.run();
    key(&mut h, Key::Enter);
    assert_eq!(h.state().0.text(0, 2, 3), "1", "not written");
    assert_eq!(h.state().0.cursor(), (2, 3), "still on the cell");
    assert!(h.query_by_label("C2: a whole number from 1 to 99").is_some(), "the status says why");
    let refused: Vec<String> = h.state().1.iter().filter_map(|o| o.refused.clone()).collect();
    assert_eq!(refused, ["C2: a whole number from 1 to 99"]);
}

#[test]
fn a_list_rule_gives_a_dropdown() {
    let mut s = orders();
    s.add_validation(Validation::at(0, "D2:D9", Rule::List(vec!["paid".into(), "pending".into(), "refunded".into()])).unwrap());
    let mut h = harness(s);
    let at = h.state().0.cell_center(3, 4).unwrap();
    click(&mut h, at);
    let cell = h.state().0.cell_screen_rect(3, 4).unwrap();
    click(&mut h, egui::pos2(cell.max.x + 9.0, cell.center().y));
    h.get_by_label("refunded").click();
    h.run();
    assert_eq!(h.state().0.text(0, 3, 4), "refunded");
}

#[test]
fn a_table_formats_its_columns_for_what_they_hold() {
    let mut s = Spreadsheet::from_csv(
        b"id,date,item,amount,qty\n1,2026-01-05,lamp,1200.5,1500\n2,2026-02-07,hub,99.25,7\n3,2026-03-09,mouse,15,n/a\n",
        "sales.csv",
    )
    .unwrap();
    let narrow = s.column_width(0, 3);
    s.add_table(Table::at("Sales", 0, "A1:E4").unwrap()).unwrap();
    let table = s.table("Sales").unwrap().clone();
    use egui_spreadsheet::table::ColumnKind::*;
    let kinds: Vec<_> = (1..=5).map(|c| s.column_kind(&table, c)).collect();
    assert_eq!(kinds, [Identifier, Date, Text, Decimal, Whole], "an id has no thousands separator; a stray n/a does not make a number column text");
    assert_eq!(s.text(0, 2, 4), "1,200.50", "decimals: two places, thousands");
    assert_eq!(s.text(0, 4, 4), "15.00");
    assert_eq!(s.text(0, 2, 5), "1,500", "whole numbers: thousands");
    assert_eq!(s.text(0, 4, 5), "n/a", "text stays text");
    assert!(s.text(0, 2, 2).contains("2026"), "a date stays a date: {}", s.text(0, 2, 2));
    assert_eq!(s.text(0, 1, 4), "amount", "the header is not formatted");
    assert!(s.column_width(0, 3) > 0.0 && s.column_width(0, 3) != narrow || s.column_width(0, 3) >= 40.0);

    // keep_formats: as they were
    let mut plain = Spreadsheet::from_csv(b"n\n1200.5\n", "p.csv").unwrap();
    plain.add_table(Table::at("P", 0, "A1:A2").unwrap().keep_formats()).unwrap();
    assert_eq!(plain.text(0, 2, 1), "1200.5");
}

// ---- sorting ----

fn column(s: &Spreadsheet, col: i32, rows: std::ops::RangeInclusive<i32>) -> Vec<String> {
    rows.map(|r| s.text(0, r, col)).collect()
}

#[test]
fn a_table_sorts_by_a_column_and_undo_puts_it_back() {
    let mut s = orders();
    s.add_table(Table::at("Orders", 0, "A1:D9").unwrap().keep_formats()).unwrap();
    s.set_style(0, (2, 2, 2, 2), &egui_spreadsheet::style::CellStyle::new().bold()).unwrap();
    s.sort_by(0, 5, 3, true).unwrap();
    assert_eq!(column(&s, 3, 1..=9), ["qty", "8", "7", "6", "5", "4", "3", "2", "1"], "the header stays");
    assert_eq!(column(&s, 2, 2..=4), ["hub", "mouse", "lamp"], "rows move whole");
    assert!(s.model().get_cell_style(0, 9, 2).unwrap().font.b, "the bold lamp of row 2 is now row 9");
    assert_eq!(s.text(0, 12, 1), "below the data", "outside the table: untouched");

    s.sort_by(0, 2, 2, false).unwrap();
    assert_eq!(column(&s, 2, 2..=9), ["hub", "hub", "hub", "lamp", "lamp", "lamp", "mouse", "mouse"]);
    assert_eq!(column(&s, 1, 2..=4), ["8", "5", "2"], "ties keep their order");

    assert!(s.undo());
    assert_eq!(column(&s, 3, 2..=9), ["8", "7", "6", "5", "4", "3", "2", "1"], "one sort, one undo");
}

#[test]
fn blanks_go_last_and_numbers_before_text() {
    let mut s = Spreadsheet::from_csv(b"v\nb\n3\n\nA\n10\n", "v.csv").unwrap();
    s.sort(0, (2, 1, 6, 1), 1, false).unwrap();
    assert_eq!(column(&s, 1, 2..=6), ["3", "10", "A", "b", ""]);
    s.sort(0, (2, 1, 6, 1), 1, true).unwrap();
    assert_eq!(column(&s, 1, 2..=6), ["b", "A", "10", "3", ""], "blanks last both ways");
}

#[test]
fn formulas_move_with_their_row() {
    let mut s = Spreadsheet::from_csv(b"n,double\n3\n1\n2\n", "f.csv").unwrap();
    for r in 2..=4 {
        s.set(0, r, 2, &format!("=A{r}*2")).unwrap();
    }
    s.sort(0, (2, 1, 4, 2), 1, false).unwrap();
    assert_eq!(column(&s, 1, 2..=4), ["1", "2", "3"]);
    assert_eq!(column(&s, 2, 2..=4), ["2", "4", "6"]);
    assert_eq!(s.content(0, 2, 2), "=A2*2");
}

#[test]
fn a_filter_still_hides_what_it_should_after_a_sort() {
    let mut s = orders();
    s.filter(0, 4, Condition::Equals("paid".into())).unwrap();
    s.sort_by(0, 1, 3, true).unwrap();
    let shown: Vec<String> = (2..=9).filter(|r| !s.is_row_hidden(0, *r)).map(|r| s.text(0, r, 4)).collect();
    assert_eq!(shown, ["paid", "paid", "paid"]);
    assert_eq!((2..=9).filter(|r| !s.is_row_hidden(0, *r)).map(|r| s.text(0, r, 3)).collect::<Vec<_>>(), ["7", "4", "1"]);
}

#[test]
fn the_filter_menu_sorts() {
    let mut s = orders();
    s.add_table(Table::at("Orders", 0, "A1:D9").unwrap()).unwrap();
    let mut h = harness(s);
    let cell = h.state().0.cell_screen_rect(1, 3).unwrap();
    click(&mut h, egui::pos2(cell.max.x - 10.0, cell.center().y));
    // the menu's (the toolbar has one too): the last drawn
    h.get_all_by_label("Sort Z-A").last().unwrap().click();
    h.run();
    assert_eq!(column(&h.state().0, 3, 2..=4), ["8", "7", "6"]);
    assert!(h.query_by_label("OK").is_none(), "the menu closed");
}

#[test]
fn the_status_bar_adds_a_sheet() {
    let mut h = harness(orders());
    h.get_by_label("+").click();
    h.run();
    assert_eq!(h.state().0.sheet_names(), ["Sheet1", "Sheet2"]);
    assert_eq!(h.state().0.sheet(), 1);
}
