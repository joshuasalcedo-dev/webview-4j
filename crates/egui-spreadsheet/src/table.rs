//! Tables, as Excel's "Format as Table": a named rectangle with a header row, banded rows, and filter buttons on its
//! header. The look is drawn (a file keeps a table's look as a table style, not on its cells), so tables read from
//! an .xlsx and tables added here look the same. Its filter is the sheet's ([`crate::filter`]), over its rows only.
//!
//! Each of its columns is formatted for what it holds, as an Excel column is: whole numbers `#,##0`, other numbers
//! `#,##0.00`, dates kept as dates, text as it is; and its columns are sized to their text.

use egui::Color32;

use crate::Spreadsheet;
use crate::filter::AutoFilter;

/// How a table looks.
#[derive(Debug, Clone, PartialEq)]
pub struct TableStyle {
    pub header_fill: Color32,
    pub header_text: Color32,
    /// Every other data row (None: no bands).
    pub band_fill: Option<Color32>,
}

impl Default for TableStyle {
    /// Excel's default table ("Blue, Table Style Medium 2").
    fn default() -> Self {
        TableStyle {
            header_fill: Color32::from_rgb(0x44, 0x72, 0xC4),
            header_text: Color32::WHITE,
            band_fill: Some(Color32::from_rgb(0xD9, 0xE1, 0xF2)),
        }
    }
}

/// A table: a name, a sheet, a rectangle (1-based, both corners included), its header row (the first), its look.
#[derive(Debug, Clone, PartialEq)]
pub struct Table {
    pub name: String,
    pub sheet: u32,
    pub first_row: i32,
    pub first_col: i32,
    pub last_row: i32,
    pub last_col: i32,
    /// Its first row is a header (with the filter buttons).
    pub header: bool,
    /// Filter buttons on its header.
    pub filter: bool,
    /// Its columns formatted for what they hold, and sized to it (on at first).
    pub format_columns: bool,
    pub style: TableStyle,
}

/// What a table's column holds, read from its data.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum ColumnKind {
    Whole,
    /// Whole numbers that are names, not amounts (an id, a code, a year): no thousands separator.
    Identifier,
    Decimal,
    Date,
    Text,
    Empty,
}

impl ColumnKind {
    /// Its number format (None: leave the cells' own).
    pub fn format(self) -> Option<&'static str> {
        match self {
            ColumnKind::Whole => Some("#,##0"),
            ColumnKind::Identifier => Some("0"),
            ColumnKind::Decimal => Some("#,##0.00"),
            _ => None,
        }
    }
}

impl Table {
    /// A table over a rectangle `(first row, first column, last row, last column)`, with a header and filters.
    pub fn new(name: &str, sheet: u32, range: (i32, i32, i32, i32)) -> Table {
        let (r0, c0, r1, c1) = range;
        Table {
            name: name.to_owned(),
            sheet,
            first_row: r0.min(r1),
            first_col: c0.min(c1),
            last_row: r0.max(r1),
            last_col: c0.max(c1),
            header: true,
            filter: true,
            format_columns: true,
            style: TableStyle::default(),
        }
    }

    /// From an A1 reference: `Table::at("Orders", 0, "A1:H5001")`.
    pub fn at(name: &str, sheet: u32, reference: &str) -> Result<Table, String> {
        let range = crate::parse_range(reference).ok_or_else(|| format!("not a range: {reference:?} (expected e.g. A1:D10)"))?;
        Ok(Table::new(name, sheet, range))
    }

    pub fn no_header(mut self) -> Self {
        self.header = false;
        self.filter = false;
        self
    }

    /// Leaves its cells' formats and its columns' widths as they are.
    pub fn keep_formats(mut self) -> Self {
        self.format_columns = false;
        self
    }

    pub fn no_filter(mut self) -> Self {
        self.filter = false;
        self
    }

    pub fn style(mut self, style: TableStyle) -> Self {
        self.style = style;
        self
    }

    pub fn contains(&self, row: i32, col: i32) -> bool {
        row >= self.first_row && row <= self.last_row && col >= self.first_col && col <= self.last_col
    }

    /// Its A1 reference: `A1:H5001`.
    pub fn reference(&self) -> String {
        format!("{}:{}", crate::cell_name(self.first_row, self.first_col), crate::cell_name(self.last_row, self.last_col))
    }

    fn overlaps(&self, other: &Table) -> bool {
        self.sheet == other.sheet
            && self.first_row <= other.last_row
            && other.first_row <= self.last_row
            && self.first_col <= other.last_col
            && other.first_col <= self.last_col
    }

    /// How a cell of it is drawn: the header's fill and text, or a band (None: as the cell is).
    pub(crate) fn look_at(&self, row: i32) -> Option<(Color32, Option<Color32>, bool)> {
        if self.header && row == self.first_row {
            return Some((self.style.header_fill, Some(self.style.header_text), true));
        }
        let first_data = self.first_row + i32::from(self.header);
        let band = self.style.band_fill?;
        ((row - first_data) % 2 == 0).then_some((band, None, false))
    }
}

impl Spreadsheet {
    /// Adds a table. Its name must be new (ignoring case), and it must not overlap another; with filter buttons, it
    /// becomes its sheet's filter (one per sheet, as the grid has it).
    pub fn add_table(&mut self, table: Table) -> Result<(), String> {
        if table.name.trim().is_empty() {
            return Err("a table needs a name".to_owned());
        }
        if let Some(other) = self.tables.iter().find(|t| t.name.eq_ignore_ascii_case(&table.name)) {
            return Err(format!("there is a table {:?} already", other.name));
        }
        if let Some(other) = self.tables.iter().find(|t| t.overlaps(&table)) {
            return Err(format!("{} overlaps the table {:?} ({})", table.reference(), other.name, other.reference()));
        }
        if table.filter {
            self.clear_filters(table.sheet)?;
            self.filters.insert(
                table.sheet,
                AutoFilter {
                    header_row: table.first_row,
                    first_col: table.first_col,
                    last_col: table.last_col,
                    last_row: Some(table.last_row),
                    conditions: Default::default(),
                },
            );
        }
        if table.format_columns {
            self.format_table_columns(&table)?;
        }
        self.tables.push(table);
        self.changed();
        Ok(())
    }

    /// What a column of a table holds (from its first 1000 data rows): numbers, dates or text.
    pub fn column_kind(&self, table: &Table, col: i32) -> ColumnKind {
        let first = table.first_row + i32::from(table.header);
        let last = table.last_row.min(first + 999);
        let model = self.model.get_model();
        let (mut whole, mut decimal, mut date, mut text) = (0, 0, 0, 0);
        for row in first..=last {
            match model.get_cell_value_by_index(table.sheet, row, col) {
                Ok(ironcalc::base::cell::CellValue::Number(n)) => {
                    let format = self.model.get_cell_style(table.sheet, row, col).map(|s| s.num_fmt.to_lowercase()).unwrap_or_default();
                    // a date is a number shown as one (its format has days, months or years)
                    if format.contains('y') || format.contains('d') || (format.contains('m') && !format.contains("0.0")) {
                        date += 1;
                    } else if n.fract() == 0.0 {
                        whole += 1;
                    } else {
                        decimal += 1;
                    }
                }
                Ok(ironcalc::base::cell::CellValue::None) | Err(_) => {}
                Ok(_) => text += 1,
            }
        }
        // the kind most of its cells are; numbers win over a few stray texts (a "n/a", a note)
        let numbers = whole + decimal;
        match () {
            _ if numbers + date + text == 0 => ColumnKind::Empty,
            _ if date > numbers && date >= text => ColumnKind::Date,
            _ if numbers > text && decimal > 0 => ColumnKind::Decimal,
            _ if numbers > text && table.header && is_identifier(&self.text(table.sheet, table.first_row, col)) => ColumnKind::Identifier,
            _ if numbers > text => ColumnKind::Whole,
            _ => ColumnKind::Text,
        }
    }

    /// Formats each column of a table for what it holds, and sizes it to its text.
    fn format_table_columns(&mut self, table: &Table) -> Result<(), String> {
        let first = table.first_row + i32::from(table.header);
        for col in table.first_col..=table.last_col {
            if first <= table.last_row
                && let Some(format) = self.column_kind(table, col).format()
            {
                self.set_style(table.sheet, (first, col, table.last_row, col), &crate::style::CellStyle::new().number_format(format))?;
            }
            self.autofit_column(table.sheet, col)?;
        }
        Ok(())
    }

    pub fn tables(&self) -> &[Table] {
        &self.tables
    }

    pub fn table(&self, name: &str) -> Option<&Table> {
        self.tables.iter().find(|t| t.name.eq_ignore_ascii_case(name))
    }

    /// The table a cell is in.
    pub fn table_at(&self, sheet: u32, row: i32, col: i32) -> Option<&Table> {
        self.tables.iter().find(|t| t.sheet == sheet && t.contains(row, col))
    }

    /// Takes a table off (its cells stay, its filter goes and its rows show again).
    pub fn remove_table(&mut self, name: &str) -> Result<(), String> {
        let at = self.tables.iter().position(|t| t.name.eq_ignore_ascii_case(name)).ok_or_else(|| format!("no table {name:?}"))?;
        let table = self.tables.remove(at);
        if self.filters.get(&table.sheet).is_some_and(|f| f.header_row == table.first_row && f.first_col == table.first_col) {
            self.clear_filters(table.sheet)?;
            self.filters.remove(&table.sheet);
        }
        self.changed();
        Ok(())
    }

    /// The tables a workbook read from an .xlsx has (with its filter buttons when they had them).
    pub(crate) fn tables_from_file(&mut self) {
        let model = self.model.get_model();
        let sheets: Vec<String> = model.workbook.worksheets.iter().map(|w| w.name.clone()).collect();
        let found: Vec<Table> = model
            .workbook
            .tables
            .values()
            .filter_map(|t| {
                let sheet = sheets.iter().position(|n| n == &t.sheet_name)? as u32;
                // a table from a file keeps the formats it has
                let mut table = Table::at(&t.name, sheet, &t.reference).ok()?.keep_formats();
                table.header = t.header_row_count > 0;
                table.filter = table.header && t.has_filters;
                Some(table)
            })
            .collect();
        for table in found {
            let _ = self.add_table(table);
        }
    }
}

/// Whether a column's header names identifiers (an id, a code, a number, a year, a zip), not amounts.
fn is_identifier(header: &str) -> bool {
    let words: Vec<String> = header
        .split(|c: char| !c.is_ascii_alphanumeric())
        .flat_map(|w| {
            // camelCase too: orderId is order, id
            let mut parts = Vec::new();
            let mut part = String::new();
            let mut after_lower = false;
            for ch in w.chars() {
                // a capital after a small letter starts a word (orderId); SKU stays one
                if ch.is_ascii_uppercase() && after_lower {
                    parts.push(std::mem::take(&mut part));
                }
                after_lower = ch.is_ascii_lowercase();
                part.push(ch.to_ascii_lowercase());
            }
            parts.push(part);
            parts
        })
        .filter(|w| !w.is_empty())
        .collect();
    words.iter().any(|w| matches!(w.as_str(), "id" | "code" | "no" | "nr" | "number" | "num" | "year" | "zip" | "postcode" | "sku" | "ref"))
}

#[cfg(test)]
mod tests {
    use super::is_identifier;

    #[test]
    fn identifiers_by_their_header() {
        for h in ["id", "order_id", "OrderId", "Customer No.", "SKU", "zip code", "Year"] {
            assert!(is_identifier(h), "{h}");
        }
        for h in ["qty", "amount", "unit_price", "idea", "notice"] {
            assert!(!is_identifier(h), "{h}");
        }
    }
}
