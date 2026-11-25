//! Loading data: CSV files as they come (separators, quotes, encodings, ragged rows, formulas that must stay text),
//! .xlsx workbooks (values, formulas and styles through a file), in the background, and (run in release) the speed
//! of a big CSV.

use std::time::{Duration, Instant};

use egui_spreadsheet::style::{Align, CellStyle};
use egui_spreadsheet::{Kind, Loading, Spreadsheet};

fn wait(mut loading: Loading) -> Result<Spreadsheet, String> {
    let started = Instant::now();
    loop {
        if let Some(done) = loading.poll() {
            return done;
        }
        assert!(started.elapsed() < Duration::from_secs(60), "still reading");
        std::thread::sleep(Duration::from_millis(5));
    }
}

// ---- CSV ----

#[test]
fn a_csv_becomes_a_sheet_numbers_as_numbers() {
    let s = Spreadsheet::from_csv(b"item,qty,price\nmilk,2,1.25\nbread,1,2.5\n", "shop.csv").unwrap();
    assert_eq!(s.text(0, 1, 1), "item");
    assert_eq!(s.text(0, 2, 2), "2");
    assert_eq!(s.text(0, 3, 3), "2.5");
    // numbers are numbers: a formula over them works
    let mut s = s;
    s.set(0, 4, 3, "=SUMPRODUCT(B2:B3,C2:C3)").unwrap();
    assert_eq!(s.text(0, 4, 3), "5");
}

#[test]
fn a_formula_in_a_csv_stays_text() {
    let s = Spreadsheet::from_csv(b"name,cmd\nx,=1+1\ny,\"=HYPERLINK(\"\"http://evil\"\",\"\"click\"\")\"\n", "data.csv").unwrap();
    assert_eq!(s.text(0, 2, 2), "=1+1", "shown as typed, not 2");
    assert_eq!(s.text(0, 3, 2), "=HYPERLINK(\"http://evil\",\"click\")");
}

#[test]
fn european_csv_semicolons_and_decimal_commas() {
    let s = Spreadsheet::from_csv("Name;Betrag\nMüller;1,5\n".as_bytes(), "de.csv").unwrap();
    assert_eq!(s.text(0, 2, 1), "Müller");
    assert_eq!(s.text(0, 2, 2), "1,5", "kept as written (as Excel does under an English locale)");
}

#[test]
fn quoted_fields_tabs_ragged_rows_and_windows_1252() {
    let s = Spreadsheet::from_csv(b"a\tb\n\"x\ty\"\t\"two\nlines\"\nshort\n", "t.tsv").unwrap();
    assert_eq!(s.text(0, 2, 1), "x\ty");
    assert_eq!(s.text(0, 2, 2), "two\nlines");
    assert_eq!(s.text(0, 3, 1), "short");
    let latin = Spreadsheet::from_csv(b"name\ncaf\xE9\n", "win.csv").unwrap();
    assert_eq!(latin.text(0, 2, 1), "café");
}

#[test]
fn columns_are_as_wide_as_their_text() {
    let mut s = Spreadsheet::from_csv(b"id,a much longer heading than the rest\n1,x\n", "w.csv").unwrap();
    let narrow = s.model().get_column_width(0, 1).unwrap();
    let wide = s.model().get_column_width(0, 2).unwrap();
    assert!(narrow >= 60.0 && wide > narrow * 2.0, "{narrow} {wide}");
}

#[test]
fn an_empty_csv_is_an_empty_sheet() {
    let s = Spreadsheet::from_csv(b"", "empty.csv").unwrap();
    assert_eq!(s.text(0, 1, 1), "");
}

#[test]
fn a_csv_wider_than_a_sheet_is_refused() {
    let line = vec!["x"; 16_385].join(",");
    let err = Spreadsheet::from_csv(line.as_bytes(), "wide.csv").err().expect("refused");
    assert!(err.contains("16385 columns"), "{err}");
}

// ---- .xlsx ----

#[test]
fn an_xlsx_keeps_its_values_formulas_and_styles() {
    let mut s = Spreadsheet::default();
    s.set(0, 1, 1, "Total").unwrap();
    s.set(0, 1, 2, "1234.5").unwrap();
    s.set(0, 2, 2, "=B1*2").unwrap();
    s.set_style(0, (1, 1, 1, 2), &CellStyle::new().bold().fill("#1F4E79").font_color("#FFFFFF").align(Align::Center)).unwrap();
    s.set_style(0, (1, 2, 2, 2), &CellStyle::new().number_format("$#,##0.00")).unwrap();
    let bytes = s.to_xlsx().unwrap();

    let mut back = Spreadsheet::open(&bytes, "styled.xlsx").unwrap();
    assert_eq!(back.text(0, 1, 2), "$1,234.50");
    assert_eq!(back.content(0, 2, 2), "=B1*2");
    assert_eq!(back.text(0, 2, 2), "$2,469.00");
    let style = back.model().get_cell_style(0, 1, 1).unwrap();
    assert!(style.font.b, "bold");
    assert_eq!(back.model().resolve_color(&style.fill.color).to_uppercase(), "#1F4E79");
}

#[test]
fn what_the_bytes_are_decides_not_the_name() {
    let xlsx = Spreadsheet::default().to_xlsx().unwrap();
    assert_eq!(egui_spreadsheet::load::kind_of(&xlsx), Kind::Xlsx);
    // a workbook named .csv still opens as a workbook
    assert!(Spreadsheet::open(&xlsx, "misnamed.csv").is_ok());
    assert!(Spreadsheet::open(b"PK\x03\x04broken", "x.xlsx").is_err());
}

// ---- in the background ----

#[test]
fn both_load_in_the_background() {
    let csv = wait(Loading::start(b"a,b\n1,2\n".to_vec(), "small.csv")).unwrap();
    assert_eq!(csv.text(0, 2, 2), "2");
    let mut book = Spreadsheet::default();
    book.set(0, 1, 1, "from xlsx").unwrap();
    let xlsx = wait(Loading::start(book.to_xlsx().unwrap(), "book.xlsx")).unwrap();
    assert_eq!(xlsx.text(0, 1, 1), "from xlsx");
}

// ---- speed (cargo test -p egui-spreadsheet --release --test load -- --ignored --nocapture) ----

#[test]
#[ignore = "a measurement: run in release"]
fn speed_of_a_big_csv() {
    let mut csv = String::from("id,name,a,b,c,d,e,f,g,h\n");
    for row in 1..=100_000 {
        csv.push_str(&format!("{row},item {row}"));
        for col in 0..8 {
            csv.push_str(&format!(",{}", (row * 31 + col * 7) % 1000));
        }
        csv.push('\n');
    }
    println!("CSV: {:.1} MB, 1,000,010 fields", csv.len() as f64 / 1e6);
    let started = Instant::now();
    let s = wait(Loading::start(csv.into_bytes(), "big.csv")).unwrap();
    println!("read and loaded: {:.2} s", started.elapsed().as_secs_f32());
    assert_eq!(s.text(0, 100_001, 2), "item 100000");
    let bytes = s.to_xlsx().unwrap();
    println!("as .xlsx: {:.1} MB", bytes.len() as f64 / 1e6);
    let started = Instant::now();
    let back = wait(Loading::start(bytes, "big.xlsx")).unwrap();
    println!("that .xlsx read and loaded: {:.2} s", started.elapsed().as_secs_f32());
    assert_eq!(back.text(0, 100_001, 2), "item 100000");
}
