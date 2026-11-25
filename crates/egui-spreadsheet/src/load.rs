//! Loading data: an .xlsx workbook or a CSV file (comma, semicolon, tab or pipe separated), from its bytes. Which one
//! it is comes from the bytes (an .xlsx is a zip), not the name, so a misnamed file still opens as what it is.
//!
//! A CSV is data, not a program: a field that starts with `=` stays text (a formula in a CSV someone sent you is
//! how "CSV injection" works). Numbers are numbers, the rest text, as Excel reads a CSV.

use egui::Ui;
use ironcalc::base::types::Workbook;
use ironcalc::base::{Model, UserModel};

use crate::Spreadsheet;
use crate::grid::{MAX_COL, MAX_ROW};

/// What a file is.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Kind {
    Xlsx,
    Csv,
}

/// What the bytes are: an .xlsx starts as every zip does (`PK\x03\x04`); anything else is read as text.
pub fn kind_of(bytes: &[u8]) -> Kind {
    if bytes.starts_with(b"PK\x03\x04") { Kind::Xlsx } else { Kind::Csv }
}

/// The text of a CSV file: UTF-8 (its byte-order mark dropped), or, when it is not UTF-8 (Excel on Windows writes
/// its CSVs in Windows-1252), each byte as the character it is in Latin-1.
pub fn csv_text(bytes: &[u8]) -> String {
    let bytes = bytes.strip_prefix(b"\xEF\xBB\xBF").unwrap_or(bytes);
    match std::str::from_utf8(bytes) {
        Ok(text) => text.to_owned(),
        Err(_) => bytes.iter().map(|b| char::from(*b)).collect(),
    }
}

/// The separator a CSV uses: of `,` `;` tab `|`, the one the most records of its first 64 KB use (outside quotes,
/// which may span lines), then the one used most; `,` when none is. A short record using none does not count against
/// one: ragged files are common.
pub fn separator(text: &str) -> u8 {
    let sample = &text[..text.char_indices().nth(64 * 1024).map_or(text.len(), |(i, _)| i)];
    let candidates = [',', ';', '\t', '|'];
    // per candidate: (records that use it, times used)
    let mut score = [(0usize, 0usize); 4];
    let mut in_record = [0usize; 4];
    let mut quoted = false;
    let end_record = |in_record: &mut [usize; 4], score: &mut [(usize, usize); 4]| {
        for k in 0..4 {
            if in_record[k] > 0 {
                score[k].0 += 1;
                score[k].1 += in_record[k];
            }
            in_record[k] = 0;
        }
    };
    for c in sample.chars() {
        match c {
            '"' => quoted = !quoted,
            '\n' if !quoted => end_record(&mut in_record, &mut score),
            _ if !quoted => {
                if let Some(k) = candidates.iter().position(|x| *x == c) {
                    in_record[k] += 1;
                }
            }
            _ => {}
        }
    }
    end_record(&mut in_record, &mut score);
    (0..4).filter(|k| score[*k].0 > 0).max_by_key(|k| score[*k]).map_or(b',', |k| candidates[k] as u8)
}

/// The rows of a CSV file, its fields as they are (quotes taken off). Rows may be of any length.
pub fn read_csv(bytes: &[u8]) -> Result<Vec<Vec<String>>, String> {
    let text = csv_text(bytes);
    let mut reader = csv::ReaderBuilder::new().has_headers(false).flexible(true).delimiter(separator(&text)).from_reader(text.as_bytes());
    let mut rows = Vec::new();
    for (i, record) in reader.records().enumerate() {
        let record = record.map_err(|e| format!("line {}: {e}", i + 1))?;
        rows.push(record.iter().map(str::to_owned).collect::<Vec<_>>());
    }
    if rows.len() > MAX_ROW as usize {
        return Err(format!("{} rows: more than a sheet holds ({MAX_ROW})", rows.len()));
    }
    if let Some(wide) = rows.iter().map(Vec::len).max().filter(|w| *w > MAX_COL as usize) {
        return Err(format!("{wide} columns: more than a sheet holds ({MAX_COL})"));
    }
    Ok(rows)
}

/// What a CSV field is typed as: a field that would be a formula stays text (`'` before it, Excel's way).
fn as_data(field: &str) -> String {
    if field.starts_with('=') { format!("'{field}") } else { field.to_owned() }
}

/// A workbook of one sheet from a CSV's rows, its columns as wide as their text (up to a point).
pub fn csv_model(rows: Vec<Vec<String>>, name: &str) -> Result<Model<'static>, String> {
    // the name is the workbook's own copy (IronCalc ties a model to the name it is made with)
    let mut model = Model::new_empty("Book1", "en", "UTC", "en")?;
    model.workbook.name = name.to_owned();
    let mut widest: Vec<usize> = Vec::new();
    for (r, row) in rows.into_iter().enumerate() {
        for (c, field) in row.into_iter().enumerate() {
            if field.is_empty() {
                continue;
            }
            if r < 2000 {
                if widest.len() <= c {
                    widest.resize(c + 1, 0);
                }
                widest[c] = widest[c].max(field.chars().count());
            }
            model.set_user_input(0, r as i32 + 1, c as i32 + 1, as_data(&field))?;
        }
    }
    for (c, chars) in widest.into_iter().enumerate() {
        let px = (chars as f64 * 7.5 + 18.0).clamp(60.0, 320.0);
        model.set_column_width(0, c as i32 + 1, px)?;
    }
    model.evaluate();
    Ok(model)
}

impl Spreadsheet {
    /// A spreadsheet from a CSV file's bytes.
    pub fn from_csv(bytes: &[u8], name: &str) -> Result<Self, String> {
        Ok(Self::new(UserModel::from_model(csv_model(read_csv(bytes)?, name)?)))
    }

    /// A spreadsheet from a file's bytes: an .xlsx workbook, or a CSV.
    pub fn open(bytes: &[u8], name: &str) -> Result<Self, String> {
        match kind_of(bytes) {
            Kind::Xlsx => Self::from_xlsx(bytes, name),
            Kind::Csv => Self::from_csv(bytes, name),
        }
    }
}

/// What the background read leaves: a workbook read, or a CSV's rows (the model is made on the caller's thread).
enum Read {
    Workbook(Box<Workbook>),
    Rows(Vec<Vec<String>>),
}

fn read(bytes: &[u8], name: &str) -> Result<Read, String> {
    match kind_of(bytes) {
        Kind::Xlsx => ironcalc::import::load_from_xlsx_bytes(bytes, name, "en", "UTC")
            .map(|w| Read::Workbook(Box::new(w)))
            .map_err(|e| format!("not a workbook: {e:?}")),
        Kind::Csv => read_csv(bytes).map(Read::Rows),
    }
}

/// A file (.xlsx or CSV) being read in the background: natively on a thread of its own; in the page, which has
/// none, at once.
pub struct Loading {
    #[cfg(not(target_arch = "wasm32"))]
    receiver: std::sync::mpsc::Receiver<Result<Read, String>>,
    #[cfg(target_arch = "wasm32")]
    done: Option<Result<Read, String>>,
    pub name: String,
}

impl Loading {
    /// Starts reading a file's bytes.
    pub fn start(bytes: Vec<u8>, name: &str) -> Loading {
        let file = name.to_owned();
        #[cfg(not(target_arch = "wasm32"))]
        {
            let (sender, receiver) = std::sync::mpsc::channel();
            std::thread::spawn(move || {
                let _ = sender.send(read(&bytes, &file));
            });
            Loading { receiver, name: name.to_owned() }
        }
        #[cfg(target_arch = "wasm32")]
        {
            Loading { done: Some(read(&bytes, &file)), name: name.to_owned() }
        }
    }

    /// The spreadsheet, once read (None while it is not yet).
    pub fn poll(&mut self) -> Option<Result<Spreadsheet, String>> {
        #[cfg(not(target_arch = "wasm32"))]
        let read = match self.receiver.try_recv() {
            Ok(r) => r,
            Err(std::sync::mpsc::TryRecvError::Empty) => return None,
            Err(std::sync::mpsc::TryRecvError::Disconnected) => Err("the reading stopped".to_owned()),
        };
        #[cfg(target_arch = "wasm32")]
        let read = self.done.take()?;
        let model = read.and_then(|r| match r {
            Read::Workbook(workbook) => Model::from_workbook(*workbook, "en"),
            Read::Rows(rows) => csv_model(rows, &self.name),
        });
        Some(model.map(|m| Spreadsheet::new(UserModel::from_model(m))))
    }

    /// What shows while it reads: a spinner and the file's name.
    pub fn show(&self, ui: &mut Ui) {
        ui.centered_and_justified(|ui| {
            ui.horizontal(|ui| {
                ui.spinner();
                ui.label(format!("Opening {}…", self.name));
            });
        });
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn the_separator_is_the_one_on_every_line() {
        assert_eq!(separator("a,b,c\n1,2,3"), b',');
        assert_eq!(separator("a;b;c\n1,5;2,5;3"), b';', "a European CSV: decimal commas, semicolons between");
        assert_eq!(separator("a\tb\n1\t2"), b'\t');
        assert_eq!(separator("a|b\n1|2"), b'|');
        assert_eq!(separator("\"x, y\";z\n1;2"), b';', "commas inside quotes do not count");
        assert_eq!(separator("one column\nonly"), b',');
    }

    #[test]
    fn text_that_is_not_utf8_still_reads() {
        assert_eq!(csv_text(b"\xEF\xBB\xBFname"), "name", "the byte-order mark goes");
        assert_eq!(csv_text(b"caf\xE9"), "café", "Windows-1252 / Latin-1");
    }

    #[test]
    fn fields_are_read_as_csv_reads_them() {
        let rows = read_csv(b"name,note\n\"Ana, M.\",\"two\nlines\"\nBen\n\"say \"\"hi\"\"\",x,extra").unwrap();
        assert_eq!(rows[1], ["Ana, M.", "two\nlines"]);
        assert_eq!(rows[2], ["Ben"], "a short row");
        assert_eq!(rows[3], ["say \"hi\"", "x", "extra"], "a long row, doubled quotes");
    }

    #[test]
    fn what_the_bytes_are_decides() {
        assert_eq!(kind_of(b"PK\x03\x04rest"), Kind::Xlsx);
        assert_eq!(kind_of(b"a,b"), Kind::Csv);
        assert_eq!(as_data("=1+1"), "'=1+1");
        assert_eq!(as_data("-5"), "-5");
    }
}
