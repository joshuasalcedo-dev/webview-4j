//! Spelling and grammar, offline: [Harper](https://github.com/Automattic/harper) checking English text as it is
//! typed (about a millisecond for a chat message, 16 ms for a page).
//!
//! ```
//! let checker = grammar::Checker::new(grammar::Dialect::American);
//! let mistakes = checker.check("This is an test.");
//! assert_eq!(mistakes[0].suggestions, ["a"]);
//! ```
//!
//! Positions are in UTF-16 code units, as Java's `String` and the page's JavaScript count them, so a mistake's
//! `from..to` can be underlined or replaced there as is. [`Checker::check_json`] gives the same as JSON, for the
//! native library's C functions.

use harper_core::linting::{LintGroup, Suggestion};
use harper_core::spell::FstDictionary;
use harper_core::{Dialect as HarperDialect, Document};
use serde::Serialize;
use std::panic::{AssertUnwindSafe, catch_unwind};
use std::sync::Mutex;

/// Which English: spellings (colour, color) and words differ.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Default)]
pub enum Dialect {
    #[default]
    American,
    British,
    Australian,
    Canadian,
    Indian,
}

impl Dialect {
    /// From its number across the C boundary: 0 American, 1 British, 2 Australian, 3 Canadian, 4 Indian.
    pub fn from_code(code: u32) -> Self {
        match code {
            1 => Self::British,
            2 => Self::Australian,
            3 => Self::Canadian,
            4 => Self::Indian,
            _ => Self::American,
        }
    }

    /// From a language tag: `en-GB`, `en_AU`, `en-CA`, `en-IN`; anything else is American.
    pub fn from_tag(tag: &str) -> Self {
        match tag.to_ascii_uppercase().replace('_', "-").as_str() {
            "EN-GB" | "EN-UK" | "EN-IE" | "EN-NZ" => Self::British,
            "EN-AU" => Self::Australian,
            "EN-CA" => Self::Canadian,
            "EN-IN" => Self::Indian,
            _ => Self::American,
        }
    }

    fn harper(self) -> HarperDialect {
        match self {
            Self::American => HarperDialect::American,
            Self::British => HarperDialect::British,
            Self::Australian => HarperDialect::Australian,
            Self::Canadian => HarperDialect::Canadian,
            Self::Indian => HarperDialect::Indian,
        }
    }
}

/// One thing to fix: where it is (UTF-16, end exclusive), which rule found it, what kind it is, what it says, and
/// what could go in its place (each the whole text for `from..to`; an empty one removes it).
#[derive(Debug, Clone, PartialEq, Eq, Serialize)]
pub struct Mistake {
    pub from: usize,
    pub to: usize,
    pub rule: String,
    /// Spelling, Grammar, Punctuation, Style...: Harper's kind of mistake.
    pub kind: String,
    pub message: String,
    pub suggestions: Vec<String>,
}

/// A checker for one dialect. Making one loads the dictionary (a few ms); keep it, and check with it from any
/// thread.
pub struct Checker {
    dialect: Dialect,
    linter: Mutex<LintGroup>,
}

impl Checker {
    pub fn new(dialect: Dialect) -> Self {
        Self { dialect, linter: Mutex::new(LintGroup::new_curated(FstDictionary::curated(), dialect.harper())) }
    }

    pub fn dialect(&self) -> Dialect {
        self.dialect
    }

    /// The mistakes in `text`, in the order they appear. A panic inside Harper gives no mistakes, not a crash.
    pub fn check(&self, text: &str) -> Vec<Mistake> {
        catch_unwind(AssertUnwindSafe(|| self.check_inner(text))).unwrap_or_default()
    }

    /// [`check`](Self::check) as a JSON array.
    pub fn check_json(&self, text: &str) -> String {
        serde_json::to_string(&self.check(text)).unwrap_or_else(|_| "[]".into())
    }

    fn check_inner(&self, text: &str) -> Vec<Mistake> {
        let chars: Vec<char> = text.chars().collect();
        // utf16[i]: where char i starts in UTF-16; one more for the end
        let mut utf16 = Vec::with_capacity(chars.len() + 1);
        let mut at = 0usize;
        for c in &chars {
            utf16.push(at);
            at += c.len_utf16();
        }
        utf16.push(at);

        let document = Document::new_plain_english_curated(text);
        let organized = {
            let mut linter = self.linter.lock().unwrap_or_else(|poisoned| poisoned.into_inner());
            linter.organized_lints(&document)
        };
        let mut found: Vec<(Mistake, u8)> = Vec::new();
        for (rule, lints) in organized {
            for lint in lints {
                let start = lint.span.start.min(chars.len());
                let end = lint.span.end.min(chars.len()).max(start);
                let original: String = chars[start..end].iter().collect();
                let suggestions = lint
                    .suggestions
                    .iter()
                    .map(|s| match s {
                        Suggestion::ReplaceWith(with) => with.iter().collect(),
                        Suggestion::InsertAfter(add) => format!("{original}{}", add.iter().collect::<String>()),
                        Suggestion::Remove => String::new(),
                    })
                    .collect();
                let mistake = Mistake {
                    from: utf16[start],
                    to: utf16[end],
                    rule: rule.clone(),
                    kind: format!("{:?}", lint.lint_kind),
                    message: lint.message.clone(),
                    suggestions,
                };
                found.push((mistake, lint.priority));
            }
        }
        found.sort_by(|a, b| a.0.from.cmp(&b.0.from).then(a.1.cmp(&b.1)));
        found.into_iter().map(|(m, _)| m).collect()
    }
}

/// `text` with one mistake fixed by `suggestion`; the positions are UTF-16, as in [`Mistake`].
pub fn apply(text: &str, mistake: &Mistake, suggestion: &str) -> String {
    let units: Vec<u16> = text.encode_utf16().collect();
    let from = mistake.from.min(units.len());
    let to = mistake.to.min(units.len()).max(from);
    let mut out = String::from_utf16_lossy(&units[..from]);
    out.push_str(suggestion);
    out.push_str(&String::from_utf16_lossy(&units[to..]));
    out
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn a_or_an() {
        let c = Checker::new(Dialect::American);
        let m = c.check("This is an test.");
        assert_eq!(m.len(), 1, "{m:?}");
        assert_eq!((m[0].from, m[0].to), (8, 10));
        assert_eq!(m[0].suggestions, ["a"]);
        assert_eq!(apply("This is an test.", &m[0], "a"), "This is a test.");
    }

    #[test]
    fn a_clean_sentence_has_none() {
        let c = Checker::new(Dialect::American);
        assert!(c.check("The quick brown fox jumps over the lazy dog.").is_empty());
        assert!(c.check("").is_empty());
    }

    #[test]
    fn a_misspelling_with_suggestions() {
        let c = Checker::new(Dialect::American);
        let m = c.check("I recieved the package.");
        let it = m.iter().find(|m| m.from == 2).expect("recieved");
        assert_eq!(it.to, 10);
        assert!(it.suggestions.iter().any(|s| s == "received"), "{it:?}");
    }

    #[test]
    fn positions_count_utf16_as_java_does() {
        // the emoji is two UTF-16 units: "an" starts at 11, not 10
        let text = "\u{1F600} This is an test.";
        let c = Checker::new(Dialect::American);
        let m = c.check(text);
        let it = m.iter().find(|m| m.suggestions.iter().any(|s| s == "a")).expect("an");
        assert_eq!((it.from, it.to), (11, 13));
        assert_eq!(apply(text, it, "a"), "\u{1F600} This is a test.");
    }

    #[test]
    fn dialects_spell_differently() {
        let us = Checker::new(Dialect::American);
        let gb = Checker::new(Dialect::from_tag("en-GB"));
        assert_eq!(gb.dialect(), Dialect::British);
        let text = "The colour of the centre.";
        assert!(gb.check(text).is_empty(), "{:?}", gb.check(text));
        assert!(!us.check(text).is_empty(), "American flags colour and centre");
    }

    #[test]
    fn json_for_the_c_side() {
        let c = Checker::new(Dialect::from_code(0));
        let v: serde_json::Value = serde_json::from_str(&c.check_json("This is an test.")).unwrap();
        assert_eq!(v[0]["from"], 8);
        assert_eq!(v[0]["suggestions"][0], "a");
        assert_eq!(c.check_json(""), "[]");
    }

    #[test]
    fn many_threads_one_checker() {
        let c = std::sync::Arc::new(Checker::new(Dialect::American));
        let handles: Vec<_> = (0..8)
            .map(|_| {
                let c = c.clone();
                std::thread::spawn(move || c.check("This is an test.").len())
            })
            .collect();
        for h in handles {
            assert_eq!(h.join().unwrap(), 1);
        }
    }
}
