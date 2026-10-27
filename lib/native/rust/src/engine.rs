//! Override execution: runs a chain of YAML/JS override documents against the config tree.

pub mod js;
pub mod yaml;

use serde_json::Value as JsonValue;

use crate::compiler::patch::apply_override_document;
use crate::model::LoadedOverride;

#[derive(Debug)]
pub struct ApplyOverridesResult {
    pub root: JsonValue,
    pub warnings: Vec<String>,
}

pub fn apply_overrides(
    mut root: JsonValue,
    overrides: &[LoadedOverride],
    encrypted: bool,
) -> Result<ApplyOverridesResult, String> {
    if overrides.is_empty() {
        return Ok(ApplyOverridesResult {
            root,
            warnings: Vec::new(),
        });
    }

    let mut warnings = Vec::new();
    // One JS realm per compile chain: helpers are installed once and profile objects are passed
    // natively instead of JSON-string round trips on every script. The realm is only created if
    // the chain actually contains a JS override.
    let mut js_runtime: Option<js::JsRuntime> = None;

    for override_item in overrides {
        match override_item.ext.as_str() {
            "yaml" | "yml" => {
                let patch = yaml::parse_yaml_override(&override_item.content).map_err(|error| {
                    format!("parse yaml override {}: {}", override_item.path, error)
                })?;
                apply_override_document(&mut root, patch);
            }
            "js" => {
                if js_runtime.is_none() {
                    js_runtime = Some(js::JsRuntime::new(encrypted)?);
                }
                let runtime = js_runtime
                    .as_mut()
                    .expect("js runtime initialized for js override");
                let outcome = runtime.apply(root, override_item)?;
                root = outcome.root;
                warnings.extend(outcome.warnings);
            }
            other => {
                return Err(format!("unsupported override extension: {other}"));
            }
        }
    }
    Ok(ApplyOverridesResult { root, warnings })
}
