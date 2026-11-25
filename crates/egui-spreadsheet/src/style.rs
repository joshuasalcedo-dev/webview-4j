//! Cell styles: what to change ([`CellStyle`], applied through IronCalc, so it is saved in the .xlsx), and how a
//! cell looks when drawn ([`Look`], read from IronCalc's style, its colours resolved).

use egui::{Align2, Color32};
use ironcalc::base::expressions::types::Area;
use ironcalc::base::types::{HorizontalAlignment, Style, VerticalAlignment};
use ironcalc::base::UserModel;

/// Horizontal alignment of a cell's text. `General`: numbers right, text left (Excel's default).
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Align {
    General,
    Left,
    Center,
    Right,
}

/// Vertical alignment of a cell's text.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum VAlign {
    Top,
    Center,
    Bottom,
}

/// A style to apply to cells: only what is set changes, the rest of each cell's style stays.
/// ```
/// use egui_spreadsheet::style::{Align, CellStyle};
/// let header = CellStyle::new().bold().fill("#1F4E79").font_color("#FFFFFF").align(Align::Center);
/// let money = CellStyle::new().number_format("$#,##0.00");
/// ```
#[derive(Debug, Clone, Default, PartialEq)]
pub struct CellStyle {
    pub bold: Option<bool>,
    pub italic: Option<bool>,
    pub underline: Option<bool>,
    pub strike: Option<bool>,
    /// `#RRGGBB`.
    pub font_color: Option<String>,
    /// In points (Excel's default is 11).
    pub font_size: Option<i32>,
    /// The background, `#RRGGBB`.
    pub fill: Option<String>,
    pub align: Option<Align>,
    pub valign: Option<VAlign>,
    pub wrap: Option<bool>,
    /// Excel's number format: `0.00`, `#,##0`, `$#,##0.00`, `0%`, `yyyy-mm-dd`...
    pub number_format: Option<String>,
}

impl CellStyle {
    pub fn new() -> Self {
        Self::default()
    }
    pub fn bold(mut self) -> Self {
        self.bold = Some(true);
        self
    }
    pub fn italic(mut self) -> Self {
        self.italic = Some(true);
        self
    }
    pub fn underline(mut self) -> Self {
        self.underline = Some(true);
        self
    }
    pub fn strike(mut self) -> Self {
        self.strike = Some(true);
        self
    }
    pub fn font_color(mut self, hex: &str) -> Self {
        self.font_color = Some(hex.to_owned());
        self
    }
    pub fn font_size(mut self, points: i32) -> Self {
        self.font_size = Some(points);
        self
    }
    pub fn fill(mut self, hex: &str) -> Self {
        self.fill = Some(hex.to_owned());
        self
    }
    pub fn align(mut self, align: Align) -> Self {
        self.align = Some(align);
        self
    }
    pub fn valign(mut self, valign: VAlign) -> Self {
        self.valign = Some(valign);
        self
    }
    pub fn wrap(mut self) -> Self {
        self.wrap = Some(true);
        self
    }
    pub fn number_format(mut self, format: &str) -> Self {
        self.number_format = Some(format.to_owned());
        self
    }

    /// IronCalc's style paths and values for what is set.
    pub(crate) fn paths(&self) -> Result<Vec<(&'static str, String)>, String> {
        let flag = |b: bool| if b { "true" } else { "false" }.to_owned();
        let mut out = Vec::new();
        if let Some(b) = self.bold {
            out.push(("font.b", flag(b)));
        }
        if let Some(b) = self.italic {
            out.push(("font.i", flag(b)));
        }
        if let Some(b) = self.underline {
            out.push(("font.u", flag(b)));
        }
        if let Some(b) = self.strike {
            out.push(("font.strike", flag(b)));
        }
        if let Some(c) = &self.font_color {
            out.push(("font.color", hex(c)?));
        }
        if let Some(s) = self.font_size {
            out.push(("font.size", s.to_string()));
        }
        if let Some(c) = &self.fill {
            out.push(("fill.color", hex(c)?));
        }
        if let Some(a) = self.align {
            out.push((
                "alignment.horizontal",
                match a {
                    Align::General => "general",
                    Align::Left => "left",
                    Align::Center => "center",
                    Align::Right => "right",
                }
                .to_owned(),
            ));
        }
        if let Some(v) = self.valign {
            out.push((
                "alignment.vertical",
                match v {
                    VAlign::Top => "top",
                    VAlign::Center => "center",
                    VAlign::Bottom => "bottom",
                }
                .to_owned(),
            ));
        }
        if let Some(w) = self.wrap {
            out.push(("alignment.wrap_text", flag(w)));
        }
        if let Some(f) = &self.number_format {
            out.push(("num_fmt", f.clone()));
        }
        Ok(out)
    }

    /// Applies it to every cell of `area`.
    pub(crate) fn apply(&self, model: &mut UserModel<'static>, area: &Area) -> Result<(), String> {
        for (path, value) in self.paths()? {
            model.update_range_style(area, path, &value)?;
        }
        Ok(())
    }
}

/// `#RRGGBB` (or `RRGGBB`), checked and upper-cased the way IronCalc takes it.
fn hex(text: &str) -> Result<String, String> {
    let t = text.trim().trim_start_matches('#');
    if t.len() == 6 && t.bytes().all(|b| b.is_ascii_hexdigit()) {
        Ok(format!("#{}", t.to_ascii_uppercase()))
    } else {
        Err(format!("not a colour: {text:?} (expected #RRGGBB)"))
    }
}

/// A colour IronCalc resolved (`#RRGGBB`, or empty for none).
pub(crate) fn color(text: &str) -> Option<Color32> {
    let t = text.trim().trim_start_matches('#');
    if t.len() < 6 {
        return None;
    }
    let n = u32::from_str_radix(&t[..6], 16).ok()?;
    Some(Color32::from_rgb((n >> 16) as u8, (n >> 8) as u8, n as u8))
}

/// How a cell looks, from its style: what the grid draws.
#[derive(Debug, Clone, PartialEq)]
pub struct Look {
    pub fill: Option<Color32>,
    pub color: Option<Color32>,
    pub bold: bool,
    pub italic: bool,
    pub underline: bool,
    pub strike: bool,
    /// Relative to Excel's 11 points.
    pub scale: f32,
    /// None: by the value (numbers right, the rest left).
    pub align: Option<Align2>,
    pub wrap: bool,
    /// Each edge's colour, when it has a border: left, top, right, bottom.
    pub borders: [Option<Color32>; 4],
}

impl Default for Look {
    fn default() -> Self {
        Look { fill: None, color: None, bold: false, italic: false, underline: false, strike: false, scale: 1.0, align: None, wrap: false, borders: [None; 4] }
    }
}

impl Look {
    pub(crate) fn of(model: &UserModel<'static>, style: &Style) -> Look {
        let resolve = |c: &ironcalc::base::types::Color| color(&model.resolve_color(c));
        let (h, v) = style.alignment.as_ref().map_or((HorizontalAlignment::General, VerticalAlignment::Bottom), |a| (a.horizontal.clone(), a.vertical.clone()));
        let row = match v {
            VerticalAlignment::Top => 0,
            VerticalAlignment::Center | VerticalAlignment::Distributed | VerticalAlignment::Justify => 1,
            _ => 2,
        };
        let col = match h {
            HorizontalAlignment::Left => Some(0),
            HorizontalAlignment::Center | HorizontalAlignment::CenterContinuous | HorizontalAlignment::Distributed | HorizontalAlignment::Justify => Some(1),
            HorizontalAlignment::Right => Some(2),
            _ => None,
        };
        // Excel's default is bottom; for one line, the middle reads better and is what spreadsheets on screen show
        let row = if row == 2 && style.alignment.is_none() { 1 } else { row };
        let steps = [egui::Align::Min, egui::Align::Center, egui::Align::Max];
        let align = col.map(|c| Align2([steps[c], steps[row]]));
        let edge = |b: &Option<ironcalc::base::types::BorderItem>| b.as_ref().map(|i| resolve(&i.color).unwrap_or(Color32::BLACK));
        let border = &style.border;
        Look {
            fill: resolve(&style.fill.color),
            color: resolve(&style.font.color).filter(|c| *c != Color32::BLACK),
            bold: style.font.b,
            italic: style.font.i,
            underline: style.font.u,
            strike: style.font.strike,
            scale: if style.font.sz > 0 { style.font.sz as f32 / 11.0 } else { 1.0 },
            align,
            wrap: style.alignment.as_ref().is_some_and(|a| a.wrap_text),
            borders: [edge(&border.left), edge(&border.top), edge(&border.right), edge(&border.bottom)],
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn only_what_is_set_is_changed() {
        let paths = CellStyle::new().bold().fill("#1f4e79").number_format("0.00").paths().unwrap();
        assert_eq!(paths, [("font.b", "true".to_owned()), ("fill.color", "#1F4E79".to_owned()), ("num_fmt", "0.00".to_owned())]);
        assert!(CellStyle::new().paths().unwrap().is_empty());
    }

    #[test]
    fn a_colour_must_be_one() {
        assert!(CellStyle::new().fill("blue").paths().is_err());
        assert!(CellStyle::new().font_color("#12345").paths().is_err());
        assert_eq!(color("#FF8000"), Some(Color32::from_rgb(255, 128, 0)));
        assert_eq!(color(""), None);
    }
}
