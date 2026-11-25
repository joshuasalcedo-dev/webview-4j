//! The spreadsheet as it is used: Excel's formulas through it, the mouse and keyboard, copy and paste, undo,
//! .xlsx in and out, loading in the background, and (run in release) its speed on a big sheet.

use std::time::{Duration, Instant};

use egui::{Event, Key, Modifiers, PointerButton, Pos2, Vec2};
use egui_kittest::Harness;
use egui_spreadsheet::{Loading, Output, Spreadsheet};

type State = (Spreadsheet, Vec<Output>);

fn harness(sheet: Spreadsheet) -> Harness<'static, State> {
    let mut h = Harness::builder()
        .with_size(Vec2::new(1000.0, 700.0))
        .with_step_dt(1.0 / 60.0)
        .with_max_steps(120)
        // the grid is painted, not made of widgets: there is nothing to name
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

fn filled(cells: &[(i32, i32, &str)]) -> Spreadsheet {
    let mut s = Spreadsheet::default();
    for (row, col, text) in cells {
        s.set(0, *row, *col, text).expect("a cell");
    }
    s
}

fn click(h: &mut Harness<'static, State>, pos: Pos2, modifiers: Modifiers) {
    h.hover_at(pos);
    h.step();
    for pressed in [true, false] {
        h.event(Event::PointerButton { pos, button: PointerButton::Primary, pressed, modifiers });
        h.step();
    }
    h.run();
}

fn click_cell(h: &mut Harness<'static, State>, row: i32, col: i32) {
    let pos = h.state().0.cell_center(row, col).expect("drawn");
    click(h, pos, Modifiers::NONE);
}

fn key(h: &mut Harness<'static, State>, key: Key, modifiers: Modifiers) {
    h.event(Event::Key { key, physical_key: None, pressed: true, repeat: false, modifiers });
    h.step();
    h.event(Event::Key { key, physical_key: None, pressed: false, repeat: false, modifiers });
    h.run();
}

fn type_text(h: &mut Harness<'static, State>, text: &str) {
    h.event(Event::Text(text.to_owned()));
    h.run();
}

fn changes(h: &mut Harness<'static, State>) -> Vec<(u32, i32, i32)> {
    std::mem::take(&mut h.state_mut().1).into_iter().flat_map(|o| o.changed).collect()
}

// ---- Excel's formulas ----

#[test]
fn excel_formulas_and_formats() {
    let s = filled(&[
        (1, 1, "Food"), (1, 2, "430"), (2, 1, "Rent"), (2, 2, "1200"),
        (3, 2, "=SUM(B1:B2)"), (4, 2, "=AVERAGE(B1:B2)"), (5, 2, "=VLOOKUP(\"Rent\",A1:B2,2,FALSE)"),
        (6, 2, "=TEXT(B3,\"$#,##0.00\")"), (7, 2, "=IF(B3>1000,\"big\",\"small\")"), (8, 2, "=1/0"),
        (9, 2, "=NOPE(1)"), (10, 2, "=DATE(2026,11,25)"), (11, 2, "=UPPER(A1)&\"!\""),
    ]);
    let shown: Vec<String> = (3..=11).map(|row| s.text(0, row, 2)).collect();
    assert_eq!(shown[..7], ["1630", "815", "1200", "$1,630.00", "big", "#DIV/0!", "#NAME?"]);
    assert_eq!(shown[8], "FOOD!");
    assert!(!shown[7].is_empty(), "a date: {:?}", shown[7]);
    assert_eq!(s.content(0, 3, 2), "=SUM(B1:B2)", "what was typed is kept");
}

// ---- the mouse and the keyboard ----

#[test]
fn typing_into_a_cell_commits_on_enter_and_goes_down() {
    let mut h = harness(filled(&[(1, 1, "10"), (2, 1, "=A1*2")]));
    click_cell(&mut h, 1, 1);
    changes(&mut h);
    type_text(&mut h, "21");
    key(&mut h, Key::Enter, Modifiers::NONE);
    assert_eq!(changes(&mut h), [(0, 1, 1)]);
    assert_eq!(h.state().0.text(0, 2, 1), "42", "the formula below follows");
    assert_eq!(h.state().0.cursor(), (2, 1));
}

#[test]
fn escape_gives_up_the_edit() {
    let mut h = harness(filled(&[(1, 1, "10")]));
    click_cell(&mut h, 1, 1);
    type_text(&mut h, "99");
    key(&mut h, Key::Escape, Modifiers::NONE);
    assert_eq!(h.state().0.text(0, 1, 1), "10");
    assert!(changes(&mut h).is_empty());
}

#[test]
fn tab_commits_and_goes_right() {
    let mut h = harness(Spreadsheet::default());
    click_cell(&mut h, 3, 2);
    type_text(&mut h, "x");
    key(&mut h, Key::Tab, Modifiers::NONE);
    assert_eq!(h.state().0.text(0, 3, 2), "x");
    assert_eq!(h.state().0.cursor(), (3, 3));
}

#[test]
fn shift_arrows_choose_a_rectangle_and_the_status_counts_it() {
    let mut h = harness(filled(&[(1, 1, "1"), (2, 1, "2"), (1, 2, "3"), (2, 2, "text")]));
    click_cell(&mut h, 1, 1);
    key(&mut h, Key::ArrowDown, Modifiers::SHIFT);
    key(&mut h, Key::ArrowRight, Modifiers::SHIFT);
    assert_eq!(h.state().0.selection(), (1, 1, 2, 2));
    assert_eq!(h.state().0.stats(), "Average 2   Count 4   Sum 6");
}

#[test]
fn delete_clears_the_rectangle() {
    let mut h = harness(filled(&[(1, 1, "1"), (2, 1, "2"), (3, 1, "keep")]));
    click_cell(&mut h, 1, 1);
    key(&mut h, Key::ArrowDown, Modifiers::SHIFT);
    key(&mut h, Key::Delete, Modifiers::NONE);
    assert_eq!((h.state().0.text(0, 1, 1), h.state().0.text(0, 2, 1)), (String::new(), String::new()));
    assert_eq!(h.state().0.text(0, 3, 1), "keep");
}

#[test]
fn a_copied_formula_pastes_with_its_references_moved() {
    let mut h = harness(filled(&[(1, 1, "5"), (2, 1, "7"), (1, 2, "=A1*2")]));
    click_cell(&mut h, 1, 2);
    h.event(Event::Copy);
    h.run();
    click_cell(&mut h, 2, 2);
    h.event(Event::Paste("=A1*2".to_owned()));
    h.run();
    assert_eq!(h.state().0.content(0, 2, 2), "=A2*2");
    assert_eq!(h.state().0.text(0, 2, 2), "14");
}

#[test]
fn text_from_elsewhere_pastes_as_a_table() {
    let mut h = harness(Spreadsheet::default());
    click_cell(&mut h, 2, 2);
    h.event(Event::Paste("1\t2\n3\t=B2+C3".to_owned()));
    h.run();
    let s = &h.state().0;
    assert_eq!([s.text(0, 2, 2), s.text(0, 2, 3), s.text(0, 3, 2), s.text(0, 3, 3)], ["1", "2", "3", "#CIRC!"].map(str::to_owned));
}

#[test]
fn undo_and_redo() {
    let mut h = harness(Spreadsheet::default());
    click_cell(&mut h, 1, 1);
    type_text(&mut h, "1");
    key(&mut h, Key::Enter, Modifiers::NONE);
    // back up with the keyboard (a second click on the same cell this soon is a double-click: an edit)
    key(&mut h, Key::ArrowUp, Modifiers::NONE);
    key(&mut h, Key::Z, Modifiers::COMMAND);
    assert_eq!(h.state().0.text(0, 1, 1), "");
    key(&mut h, Key::Y, Modifiers::COMMAND);
    assert_eq!(h.state().0.text(0, 1, 1), "1");
}

#[test]
fn read_only_changes_nothing() {
    let mut sheet = filled(&[(1, 1, "10")]);
    sheet.read_only = true;
    let mut h = harness(sheet);
    click_cell(&mut h, 1, 1);
    type_text(&mut h, "99");
    key(&mut h, Key::Enter, Modifiers::NONE);
    key(&mut h, Key::Delete, Modifiers::NONE);
    h.event(Event::Paste("5".to_owned()));
    h.run();
    assert_eq!(h.state().0.text(0, 1, 1), "10");
    assert!(changes(&mut h).is_empty());
}

#[test]
fn far_away_cells_are_reached() {
    let mut h = harness(filled(&[(9000, 40, "far")]));
    h.state_mut().0.select(9000, 40);
    h.run();
    let at = h.state().0.cell_center(9000, 40).expect("drawn");
    assert!(egui::Rect::from_min_size(Pos2::ZERO, Vec2::new(1000.0, 700.0)).contains(at), "scrolled to it: {at:?}");
    click(&mut h, at, Modifiers::NONE);
    assert_eq!(h.state().0.cursor(), (9000, 40));
}

// ---- .xlsx ----

#[test]
fn a_workbook_survives_xlsx() {
    let s = filled(&[(1, 1, "Food"), (1, 2, "430"), (2, 2, "=B1*2")]);
    let bytes = s.to_xlsx().expect("written");
    let back = Spreadsheet::from_xlsx(&bytes, "back").expect("read");
    assert_eq!(back.text(0, 1, 1), "Food");
    assert_eq!(back.content(0, 2, 2), "=B1*2");
    assert_eq!(back.text(0, 2, 2), "860");
}

#[test]
fn a_workbook_loads_in_the_background() {
    let bytes = filled(&[(1, 1, "hello")]).to_xlsx().expect("written");
    let mut loading = Loading::start(bytes, "hello.xlsx");
    let started = Instant::now();
    let sheet = loop {
        if let Some(done) = loading.poll() {
            break done.expect("read");
        }
        assert!(started.elapsed() < Duration::from_secs(20), "still reading");
        std::thread::sleep(Duration::from_millis(5));
    };
    assert_eq!(sheet.text(0, 1, 1), "hello");
}

#[test]
fn something_that_is_not_a_workbook_says_so() {
    assert!(Spreadsheet::from_xlsx(b"not a zip", "x").is_err());
    // a broken zip: an .xlsx that is not one (bytes that are not a zip at all are read as a CSV)
    let mut loading = Loading::start(b"PKbroken".to_vec(), "nope.xlsx");
    let done = loop {
        if let Some(d) = loading.poll() {
            break d;
        }
        std::thread::sleep(Duration::from_millis(5));
    };
    assert!(done.is_err());
}

#[test]
fn survives_no_room() {
    let mut h = harness(filled(&[(1, 1, "1")]));
    for size in [Vec2::ZERO, Vec2::new(3.0, 3.0), Vec2::new(4000.0, 40.0), Vec2::new(1000.0, 700.0)] {
        h.set_size(size);
        h.run();
    }
}

// ---- speed (cargo test -p egui-spreadsheet --release -- --ignored speed --nocapture) ----

#[test]
#[ignore = "a measurement: run in release"]
fn speed_on_a_million_cells() {
    let mut s = Spreadsheet::default();
    let started = Instant::now();
    s.model().pause_evaluation();
    for row in 1..=100_000 {
        s.set(0, row, 1, &format!("row {row}")).unwrap();
        for col in 2..=9 {
            s.set(0, row, col, &((row * 31 + col * 7) % 1000).to_string()).unwrap();
        }
        s.set(0, row, 10, &format!("=SUM(B{row}:I{row})")).unwrap();
    }
    s.model().resume_evaluation();
    s.model().evaluate();
    s.changed();
    println!("filled and evaluated 1,000,000 cells: {:.2} s", started.elapsed().as_secs_f32());

    let mut h = Harness::builder()
        .with_size(Vec2::new(1600.0, 1000.0))
        .with_accessibility_check(false)
        .build_ui_state(|ui, sheet: &mut Spreadsheet| {
            sheet.show(ui);
        }, s);
    // first frame: every visible cell formatted
    let t = Instant::now();
    h.step();
    println!("first frame: {:.2} ms", t.elapsed().as_secs_f64() * 1000.0);
    // frames with nothing new: from the cache
    let t = Instant::now();
    for _ in 0..60 {
        h.step();
    }
    println!("a frame, nothing changed: {:.2} ms", t.elapsed().as_secs_f64() * 1000.0 / 60.0);
    // jumping about the sheet: each frame new cells come into view
    let mut worst = 0.0f64;
    let t = Instant::now();
    for i in 0..60 {
        h.state_mut().select(1 + (i * 1667) % 100_000, 1 + (i % 10));
        let f = Instant::now();
        h.step();
        h.step();
        worst = worst.max(f.elapsed().as_secs_f64() * 1000.0 / 2.0);
    }
    println!("a frame, jumping to new rows: {:.2} ms on average, {worst:.2} ms at worst", t.elapsed().as_secs_f64() * 1000.0 / 120.0);
    println!("a cell typed into, the 100,000 formulas evaluated again:");
    let t = Instant::now();
    h.state_mut().set(0, 1, 2, "999").unwrap();
    h.step();
    println!("  {:.2} ms", t.elapsed().as_secs_f64() * 1000.0);
}
