//! The JS realm a chain of overrides runs in.

use std::path::Path;

use boa_engine::builtins::promise::PromiseState;
use boa_engine::object::builtins::JsPromise;
use boa_engine::{Context, JsValue, Source, js_string};
use serde_json::Value as JsonValue;

use crate::engine::js::log::{append_override_log, override_log_path, reset_override_log};
use crate::engine::js::natives::{js_string_value, register_native_helpers};
use crate::engine::js::prelude::PRELUDE_SCRIPT;
use crate::model::LoadedOverride;

const MAX_PROMISE_JOB_PASSES: usize = 1024;

pub struct JsOverrideOutcome {
    pub root: JsonValue,
    pub warnings: Vec<String>,
}

/// Reused JS realm for one compile/override chain.
///
/// Creating a `boa_engine::Context` and evaluating the prelude dominates multi-JS chains.
/// Keep one realm, pass profiles via `JsValue::from_json`, and call `main` without JSON string
/// round-trips.
pub struct JsRuntime {
    context: Context,
    encrypted: bool,
}

impl JsRuntime {
    pub fn new(encrypted: bool) -> Result<Self, String> {
        let mut context = Context::default();
        register_native_helpers(&mut context)?;
        set_global(
            &mut context,
            "__encrypted",
            js_string_value(if encrypted { "true" } else { "false" }),
        )?;
        evaluate(&mut context, PRELUDE_SCRIPT, "override helper")?;
        Ok(Self { context, encrypted })
    }

    pub fn apply(
        &mut self,
        root: JsonValue,
        override_item: &LoadedOverride,
    ) -> Result<JsOverrideOutcome, String> {
        let log_path = override_log_path(&override_item.path);
        let mut warnings = Vec::new();
        if let Err(err) = reset_override_log(&log_path) {
            let warning = if self.encrypted {
                format!("initialize JS override log failed for encrypted profile: {err}")
            } else {
                format!(
                    "initialize JS override log {} failed: {err}",
                    log_path.to_string_lossy()
                )
            };
            warnings.push(warning);
        }
        let _ = append_override_log(&log_path, "info", "开始执行脚本");

        match self.try_apply(root, override_item, &log_path) {
            Ok(next_root) => {
                let _ = append_override_log(&log_path, "info", "脚本执行成功");
                Ok(JsOverrideOutcome {
                    root: next_root,
                    warnings,
                })
            }
            Err(err) => {
                let failure_message = if self.encrypted {
                    "脚本执行失败：(redacted, encrypted profile)".to_string()
                } else {
                    format!("脚本执行失败：{err}")
                };
                let _ = append_override_log(&log_path, "exception", &failure_message);
                if self.encrypted {
                    Err("JS override failed for encrypted profile".to_string())
                } else {
                    Err(format!("JS override {}: {err}", override_item.path))
                }
            }
        }
    }

    fn try_apply(
        &mut self,
        root: JsonValue,
        override_item: &LoadedOverride,
        log_path: &Path,
    ) -> Result<JsonValue, String> {
        let profile = JsValue::from_json(&root, &mut self.context)
            .map_err(|err| format!("convert profile to js value: {err}"))?;
        // Profile was deep-copied into the JS heap; free the Rust tree before running user code.
        drop(root);

        set_global(&mut self.context, "__profile", profile)?;
        set_global(
            &mut self.context,
            "__overridePath",
            js_string_value(&override_item.path),
        )?;
        set_global(
            &mut self.context,
            "__overrideLogPath",
            js_string_value(log_path.to_string_lossy().as_ref()),
        )?;
        // Clear any previous main so a script that forgets to define one cannot reuse the last.
        set_global(&mut self.context, "main", JsValue::undefined())?;

        let main_fn = evaluate(
            &mut self.context,
            &wrap_override_script(&override_item.content),
            &override_item.path,
        )?;
        let main_callable = main_fn
            .as_callable()
            .ok_or_else(|| "JS override must define main(profile)".to_string())?;

        let profile_arg = self
            .context
            .global_object()
            .clone()
            .get(js_string!("__profile"), &mut self.context)
            .map_err(|err| format!("read __profile failed: {err}"))?;

        let result = main_callable
            .call(&JsValue::undefined(), &[profile_arg], &mut self.context)
            .map_err(|err| format!("invoke main(profile) failed: {err}"))?;

        let resolved = resolve_main_result(result, &mut self.context)?;
        let result_json = resolved
            .to_json(&mut self.context)
            .map_err(|err| format!("convert JS override result to json: {err}"))?
            .ok_or_else(|| "JS override result cannot be converted to json".to_string())?;
        if !result_json.is_object() {
            return Err("JS override result must be an object".to_string());
        }

        // Drop large temporary values from the shared realm before the next script.
        let _ = set_global(&mut self.context, "main", JsValue::undefined());
        let _ = set_global(&mut self.context, "__profile", JsValue::undefined());

        Ok(result_json)
    }
}

/// One-shot convenience wrapper used by tests and isolated callers.
pub fn apply_js_override(
    root: JsonValue,
    override_item: &LoadedOverride,
    encrypted: bool,
) -> Result<JsOverrideOutcome, String> {
    let mut runtime = JsRuntime::new(encrypted)?;
    runtime.apply(root, override_item)
}

fn set_global(context: &mut Context, key: &str, value: JsValue) -> Result<(), String> {
    context
        .global_object()
        .clone()
        .set(js_string!(key), value, true, context)
        .map_err(|err| format!("set global {key} failed: {err}"))?;
    Ok(())
}

fn evaluate(context: &mut Context, source: &str, label: &str) -> Result<JsValue, String> {
    context
        .eval(Source::from_bytes(source))
        .map_err(|err| format!("{label} failed: {err}"))
}

/// Evaluates the user script inside an IIFE so `function main` / `var` do not leak across reused
/// realms, then returns the main function for a direct native call.
fn wrap_override_script(content: &str) -> String {
    let mut wrapped = String::with_capacity(content.len().saturating_add(160));
    wrapped.push_str("(() => {\n");
    wrapped.push_str(content);
    wrapped.push_str(
        "\n;if (typeof main !== \"function\") { throw new Error(\"JS override must define main(profile)\"); } return main;\n})()",
    );
    wrapped
}

/// Drives the microtask queue until an `async main` settles.
fn resolve_main_result(result: JsValue, context: &mut Context) -> Result<JsValue, String> {
    let Some(object) = result.as_object() else {
        return Ok(result);
    };
    let promise = match JsPromise::from_object(object) {
        Ok(promise) => promise,
        Err(_) => return Ok(result),
    };
    for _ in 0..MAX_PROMISE_JOB_PASSES {
        context
            .run_jobs()
            .map_err(|err| format!("run JS promise jobs failed: {err}"))?;
        match promise.state() {
            PromiseState::Pending => continue,
            PromiseState::Fulfilled(value) => return Ok(value),
            PromiseState::Rejected(reason) => {
                let message = reason
                    .to_string(context)
                    .map_err(|err| format!("stringify JS rejection failed: {err}"))?
                    .to_std_string_escaped();
                return Err(format!("JS override rejected: {message}"));
            }
        }
    }
    Err("async main(profile) did not settle".to_string())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn wrapped_script_returns_main_and_isolates_declarations() {
        let wrapped = wrap_override_script("function main(p) { return p; }");
        assert!(wrapped.starts_with("(() => {\n"));
        assert!(wrapped.ends_with("return main;\n})()"));
    }
}
