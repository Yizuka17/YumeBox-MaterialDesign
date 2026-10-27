//! Static knowledge about the mihomo config shape: per-field merge behavior, key ordering for the
//! emitted YAML, and the runtime defaults the compiler backfills.

pub mod behavior;
pub mod defaults;
pub mod order;

pub use behavior::field_behavior;
pub use defaults::{DEFAULT_FAKE_IP_FILTER, DEFAULT_FAKE_IP_RANGE, DEFAULT_NAME_SERVERS};
pub use order::ordered_keys;
