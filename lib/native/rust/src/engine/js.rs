//! The embedded JavaScript override engine (boa).
//!
//! * [`runtime`] owns the realm reused across a chain of JS overrides;
//! * [`natives`] exposes the Rust-backed globals the [`prelude`] script builds its API on;
//! * [`log`] writes the per-override `.log` file the app shows to the user;
//! * [`fetch`] is the minimal HTTP client behind the `fetch()` helper;
//! * [`base64`] backs `b64d` / `b64e` / `Buffer`.

pub mod base64;
pub mod fetch;
pub mod log;
pub mod natives;
pub mod prelude;
pub mod runtime;

pub use runtime::{JsOverrideOutcome, JsRuntime, apply_js_override};
