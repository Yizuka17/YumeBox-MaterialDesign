//! The JavaScript prelude installed into every override realm.
//!
//! Kept in a real `.js` file so it stays readable, lintable and syntax-highlighted; it is compiled
//! into the binary and evaluated once per realm.

pub const PRELUDE_SCRIPT: &str = include_str!("prelude.js");
