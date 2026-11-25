//! The checker in the page, as WebAssembly: `new Checker("en-GB").check(text)` gives the mistakes as JSON
//! (positions in UTF-16, as JavaScript's strings count them).
use wasm_bindgen::prelude::*;

#[wasm_bindgen(js_name = Checker)]
pub struct WebChecker(crate::Checker);

#[wasm_bindgen(js_class = Checker)]
impl WebChecker {
    /// A checker for a language tag: en-US (the default), en-GB, en-AU, en-CA, en-IN.
    #[wasm_bindgen(constructor)]
    pub fn new(tag: &str) -> WebChecker {
        WebChecker(crate::Checker::new(crate::Dialect::from_tag(tag)))
    }

    /// The mistakes in `text`, as a JSON array of {from, to, rule, kind, message, suggestions}.
    pub fn check(&self, text: &str) -> String {
        self.0.check_json(text)
    }
}
