//! Rewriting of the compiled config tree.
//!
//! Two independent jobs live here:
//! * [`document`] applies user override documents (YAML patches with key modifiers);
//! * [`runtime`] applies the non-negotiable runtime patches the app needs (listeners, providers,
//!   DNS backfill, tun handling), with [`paths`] normalizing provider paths into profile scope.

pub mod document;
pub mod keys;
pub mod paths;
pub mod runtime;
pub mod values;

pub use document::apply_override_document;
pub use runtime::{
    disable_ebpf_tun_entrypoint, patch_preview_runtime, patch_providers, patch_static_runtime,
    validate_provider_paths,
};
