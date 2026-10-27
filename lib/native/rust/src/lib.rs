//! `liboverride` — the mihomo profile override/compile layer.
//!
//! A compile takes a profile source (plain or age-encrypted YAML) plus an ordered chain of
//! YAML/JS override documents, applies them, patches the result for the app's runtime, and emits
//! either the final YAML or the raw JSON config.
//!
//! Entry points, all consumed from outside this crate:
//! * JNI — [`jni`], bound to the Kotlin `Compiler` object;
//! * C ABI — [`compiler::override_compile_raw`] / [`compiler::override_free_string`];
//! * CLI — [`run_cli`].

pub mod cli;
pub mod compiler;
pub mod engine;
pub mod io;
pub mod jni;
pub mod model;

pub use cli::run_cli;
pub use compiler::{
    compile_raw_request, compile_request, override_compile_raw, override_free_string,
};
