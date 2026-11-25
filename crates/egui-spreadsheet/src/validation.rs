//! Validation, as Excel's "Data Validation": rules on a rectangle of cells, checked when a value is typed or picked.
//! A refused value is not written: the editor stays open and the status line says why. A list rule gives its cells
//! a dropdown of its values. (Rules are kept with the spreadsheet, not written into the .xlsx: IronCalc has none.)

use crate::Spreadsheet;

/// What a cell may hold.
#[derive(Debug, Clone, PartialEq)]
pub enum Rule {
    /// One of these (ignoring case); the cell gets a dropdown of them.
    List(Vec<String>),
    /// A whole number, within the bounds given.
    WholeNumber { min: Option<i64>, max: Option<i64> },
    /// A number, within the bounds given.
    Decimal { min: Option<f64>, max: Option<f64> },
    /// Text of a length within the bounds given.
    TextLength { min: Option<usize>, max: Option<usize> },
}

impl Rule {
    /// Whether `text` may be written, and why not.
    pub fn check(&self, text: &str) -> Result<(), String> {
        let t = text.trim();
        let bounds = |min: Option<String>, max: Option<String>| match (min, max) {
            (Some(a), Some(b)) => format!(" from {a} to {b}"),
            (Some(a), None) => format!(" of at least {a}"),
            (None, Some(b)) => format!(" of at most {b}"),
            (None, None) => String::new(),
        };
        match self {
            Rule::List(values) => {
                if values.iter().any(|v| v.eq_ignore_ascii_case(t)) {
                    Ok(())
                } else {
                    Err(format!("pick one of: {}", values.join(", ")))
                }
            }
            Rule::WholeNumber { min, max } => {
                let ok = t.parse::<i64>().ok().filter(|n| min.is_none_or(|m| *n >= m) && max.is_none_or(|m| *n <= m));
                ok.map(|_| ()).ok_or_else(|| format!("a whole number{}", bounds(min.map(|m| m.to_string()), max.map(|m| m.to_string()))))
            }
            Rule::Decimal { min, max } => {
                let ok = t.parse::<f64>().ok().filter(|n| n.is_finite() && min.is_none_or(|m| *n >= m) && max.is_none_or(|m| *n <= m));
                ok.map(|_| ()).ok_or_else(|| format!("a number{}", bounds(min.map(|m| m.to_string()), max.map(|m| m.to_string()))))
            }
            Rule::TextLength { min, max } => {
                let n = text.chars().count();
                if min.is_none_or(|m| n >= m) && max.is_none_or(|m| n <= m) {
                    Ok(())
                } else {
                    Err(format!("text{} characters long", bounds(min.map(|m| m.to_string()), max.map(|m| m.to_string()))))
                }
            }
        }
    }

    /// A list rule's values (what its dropdown offers).
    pub fn choices(&self) -> Option<&[String]> {
        match self {
            Rule::List(values) => Some(values),
            _ => None,
        }
    }
}

/// A rule on a rectangle of a sheet (1-based, both corners included).
#[derive(Debug, Clone, PartialEq)]
pub struct Validation {
    pub sheet: u32,
    pub first_row: i32,
    pub first_col: i32,
    pub last_row: i32,
    pub last_col: i32,
    pub rule: Rule,
    /// An empty cell is allowed (on at first, as in Excel).
    pub allow_blank: bool,
    /// What a refusal says instead of the rule's own words.
    pub message: Option<String>,
}

impl Validation {
    /// A rule on `range` = (first row, first column, last row, last column).
    pub fn new(sheet: u32, range: (i32, i32, i32, i32), rule: Rule) -> Validation {
        let (r0, c0, r1, c1) = range;
        Validation { sheet, first_row: r0.min(r1), first_col: c0.min(c1), last_row: r0.max(r1), last_col: c0.max(c1), rule, allow_blank: true, message: None }
    }

    /// From an A1 reference: `Validation::at(0, "G2:G5001", Rule::List(...))`.
    pub fn at(sheet: u32, reference: &str, rule: Rule) -> Result<Validation, String> {
        let range = crate::parse_range(reference).ok_or_else(|| format!("not a range: {reference:?} (expected e.g. A1:D10)"))?;
        Ok(Validation::new(sheet, range, rule))
    }

    pub fn required(mut self) -> Self {
        self.allow_blank = false;
        self
    }

    pub fn message(mut self, text: &str) -> Self {
        self.message = Some(text.to_owned());
        self
    }

    pub fn covers(&self, sheet: u32, row: i32, col: i32) -> bool {
        sheet == self.sheet && row >= self.first_row && row <= self.last_row && col >= self.first_col && col <= self.last_col
    }

    /// Whether `text` may go into one of its cells, and why not.
    pub fn check(&self, text: &str) -> Result<(), String> {
        if text.trim().is_empty() {
            return if self.allow_blank { Ok(()) } else { Err(self.message.clone().unwrap_or_else(|| "a value is required".to_owned())) };
        }
        self.rule.check(text).map_err(|why| self.message.clone().unwrap_or(why))
    }
}

impl Spreadsheet {
    /// Adds a rule (the latest one added wins where rules overlap).
    pub fn add_validation(&mut self, validation: Validation) {
        self.validations.push(validation);
    }

    pub fn validations(&self) -> &[Validation] {
        &self.validations
    }

    /// The rule on a cell.
    pub fn validation_at(&self, sheet: u32, row: i32, col: i32) -> Option<&Validation> {
        self.validations.iter().rev().find(|v| v.covers(sheet, row, col))
    }

    /// Whether `text` may be written into a cell, and why not.
    pub fn check(&self, sheet: u32, row: i32, col: i32, text: &str) -> Result<(), String> {
        self.validation_at(sheet, row, col).map_or(Ok(()), |v| v.check(text))
    }

    pub fn remove_validations(&mut self, sheet: u32, row: i32, col: i32) {
        self.validations.retain(|v| !v.covers(sheet, row, col));
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn rules() {
        let list = Rule::List(vec!["paid".into(), "pending".into()]);
        assert!(list.check("PAID").is_ok());
        assert_eq!(list.check("lost").unwrap_err(), "pick one of: paid, pending");
        let qty = Rule::WholeNumber { min: Some(1), max: Some(99) };
        assert!(qty.check("5").is_ok());
        assert!(qty.check("0").is_err());
        assert!(qty.check("2.5").is_err());
        assert_eq!(qty.check("x").unwrap_err(), "a whole number from 1 to 99");
        assert!(Rule::Decimal { min: Some(0.0), max: None }.check("12.5").is_ok());
        assert!(Rule::Decimal { min: Some(0.0), max: None }.check("-1").is_err());
        assert!(Rule::TextLength { min: None, max: Some(3) }.check("abcd").is_err());
    }

    #[test]
    fn blanks_and_messages() {
        let v = Validation::new(0, (2, 5, 10, 5), Rule::WholeNumber { min: Some(1), max: None });
        assert!(v.check("").is_ok());
        assert!(v.clone().required().check("").is_err());
        assert_eq!(v.message("1 or more, please").check("0").unwrap_err(), "1 or more, please");
    }
}
