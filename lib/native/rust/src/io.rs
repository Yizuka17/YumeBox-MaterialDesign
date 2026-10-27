//! Filesystem access: reading the override chain and publishing the compiled config.

pub mod atomic;
pub mod overrides;

pub use atomic::write_atomic;
pub use overrides::{LoadedOverrides, load_overrides};
