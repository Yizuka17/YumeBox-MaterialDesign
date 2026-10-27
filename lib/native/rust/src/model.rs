//! Data types shared across the override compiler.
//!
//! Split by concern: the request/response envelope exchanged with the app, the patch engine's
//! key-modifier vocabulary, and the config schema identifiers used for ordering and merge rules.

pub mod patch;
pub mod request;
pub mod schema;

pub use patch::{ParsedKey, PatchModifier, PatchOperations};
pub use request::{
    CliMode, CompileRawResult, CompileRequest, CompileResult, LoadedOverride, OverrideSpec,
    REQUEST_SCHEMA_VERSION, RunMode,
};
pub use schema::{FieldBehavior, ListStyle, SchemaId};
