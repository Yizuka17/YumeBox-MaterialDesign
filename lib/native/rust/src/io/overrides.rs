//! Reads the override files the app bound to a profile.
//!
//! For encrypted profiles no path may reach the caller, so messages are redacted there.

use std::fs;

use crate::model::{LoadedOverride, OverrideSpec};

pub struct LoadedOverrides {
    pub items: Vec<LoadedOverride>,
    pub warnings: Vec<String>,
}

pub fn load_overrides(
    overrides: &[OverrideSpec],
    encrypted: bool,
) -> Result<LoadedOverrides, String> {
    let mut items = Vec::with_capacity(overrides.len());
    let mut warnings = Vec::new();
    // Read override files sequentially but fail fast; order must match binding application order.
    for override_spec in overrides {
        let content = fs::read_to_string(&override_spec.path).map_err(|err| {
            if encrypted {
                format!("read override file failed for encrypted profile: {err}")
            } else {
                format!("read override file {}: {err}", override_spec.path)
            }
        })?;
        if content.trim().is_empty() {
            warnings.push(if encrypted {
                format!(
                    "skip empty override file for encrypted profile: ext={}",
                    override_spec.ext
                )
            } else {
                format!("skip empty override file: {}", override_spec.path)
            });
            continue;
        }
        items.push(LoadedOverride {
            path: override_spec.path.clone(),
            ext: override_spec.ext.trim().to_ascii_lowercase(),
            content,
        });
    }
    Ok(LoadedOverrides { items, warnings })
}
