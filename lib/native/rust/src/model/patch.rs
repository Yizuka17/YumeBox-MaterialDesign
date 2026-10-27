//! Vocabulary of the override-document patch language.
//!
//! An override key may carry a modifier suffix/prefix (`rules-end`, `+rules`, `dns-merge`, …);
//! [`PatchOperations`] is the set of operations collected for one base key inside a single
//! override document.

use serde_json::Value as JsonValue;

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum PatchModifier {
    Replace,
    Start,
    End,
    Merge,
    Force,
}

#[derive(Clone, Copy, Debug)]
pub struct ParsedKey<'a> {
    pub base: &'a str,
    pub modifier: PatchModifier,
}

/// Operations for one base key, owning the patch values.
///
/// The values are moved out of the parsed override document rather than deep-cloned out of it:
/// a rules/proxies list from a large subscription override is handed over without copying.
#[derive(Debug, Default)]
pub struct PatchOperations {
    pub replace: Option<JsonValue>,
    pub start: Vec<JsonValue>,
    pub end: Vec<JsonValue>,
    pub merge: Vec<JsonValue>,
    pub force: Option<JsonValue>,
}
