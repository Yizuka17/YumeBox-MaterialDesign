//! The compile pipeline: read the profile source, apply the override chain, patch the result for
//! the runtime, validate it, and encode it for the caller.

pub mod abi;
pub mod decrypt;
pub mod fingerprint;
pub mod legacy_groups;
pub mod normalize;
pub mod patch;
pub mod result;
pub mod schema;
pub mod validate;

use std::fs;
use std::path::Path;
use std::sync::Mutex;

use serde_json::Value as JsonValue;

use crate::engine;
use crate::engine::yaml::YamlToJsonError;
use crate::io::{load_overrides, write_atomic};
use crate::model::{CompileRawResult, CompileRequest, CompileResult};

pub use abi::{override_compile_raw, override_free_string};

use decrypt::{decrypt_age_source, is_age_encrypted};
use fingerprint::fingerprint_for;
use validate::{validate_request_schema, validate_root_config};

struct CompiledRoot {
    root: JsonValue,
    warnings: Vec<String>,
}

// Boa uses interior mutability while constructing and collecting a JS realm. Compiles can be
// requested concurrently by start/reload, config preview, and group inspection; serialize the
// full transaction so separate JNI/ABI callers never enter the engine at the same time.
static COMPILER_LOCK: Mutex<()> = Mutex::new(());

fn with_compile_lock<T>(compile: impl FnOnce() -> Result<T, String>) -> Result<T, String> {
    // A caught panic poisons std::sync::Mutex. The failed context is dropped during unwinding, so
    // later compiles can safely acquire the lock and report their own result.
    let _guard = COMPILER_LOCK
        .lock()
        .unwrap_or_else(|poisoned| poisoned.into_inner());
    compile()
}

/// Compiles to the YAML document the core consumes. Rejected for encrypted profiles: writing a
/// decrypted profile back out as YAML would defeat the encryption.
pub fn compile_request(
    request: CompileRequest,
    write_output: bool,
) -> Result<CompileResult, String> {
    with_compile_lock(|| compile_request_unlocked(request, write_output))
}

fn compile_request_unlocked(
    request: CompileRequest,
    write_output: bool,
) -> Result<CompileResult, String> {
    validate_request_schema(&request)?;
    let source_bytes =
        fs::read(&request.profile_path).map_err(|err| format!("read profile yaml: {err}"))?;
    if is_age_encrypted(&source_bytes) {
        return Err(
            "encrypted profiles must use native compile raw output; YAML output is disabled"
                .to_string(),
        );
    }
    let source_yaml = String::from_utf8(source_bytes)
        .map_err(|err| format!("source yaml is not utf-8: {err}"))?;
    let CompiledRoot { root, warnings } = compile_root_from_source(&request, source_yaml, false)?;

    let final_yaml = serde_yaml::to_string(&normalize::normalize_root(root))
        .map_err(|err| format!("encode final yaml: {err}"))?;
    let fingerprint = fingerprint_for(request.profile_uuid.as_bytes(), final_yaml.as_bytes());

    if write_output {
        let output_path = request.output_path.trim();
        if output_path.is_empty() {
            return Err("compile mode requires outputPath".to_string());
        }
        write_atomic(Path::new(output_path), final_yaml.as_bytes())
            .map_err(|err| format!("write runtime yaml: {err}"))?;
    }

    Ok(CompileResult {
        success: true,
        fingerprint,
        final_yaml,
        warnings,
        error: None,
    })
}

/// Compiles to the raw JSON config the core can be fed directly, which is the only supported
/// output for age-encrypted profiles.
pub fn compile_raw_request(request: CompileRequest) -> Result<CompileRawResult, String> {
    with_compile_lock(|| compile_raw_request_unlocked(request))
}

fn compile_raw_request_unlocked(request: CompileRequest) -> Result<CompileRawResult, String> {
    let compiled = compile_root(&request)?;
    let config_raw = serde_json::to_string(&compiled.root)
        .map_err(|err| format!("encode raw config json: {err}"))?;
    let fingerprint = fingerprint_for(request.profile_uuid.as_bytes(), config_raw.as_bytes());
    Ok(CompileRawResult {
        success: true,
        fingerprint,
        config_raw,
        warnings: compiled.warnings,
        error: None,
    })
}

fn compile_root(request: &CompileRequest) -> Result<CompiledRoot, String> {
    validate_request_schema(request)?;
    let (source_yaml, encrypted) = load_source_yaml(request)?;
    compile_root_from_source(request, source_yaml, encrypted)
}

fn compile_root_from_source(
    request: &CompileRequest,
    source_yaml: String,
    encrypted: bool,
) -> Result<CompiledRoot, String> {
    let mut root = engine::yaml::yaml_to_json(&source_yaml).map_err(|err| match err {
        YamlToJsonError::Parse(err) => format!("parse source yaml: {err}"),
        YamlToJsonError::Merge(err) => format!("apply yaml merge keys: {err}"),
        YamlToJsonError::Convert(err) => format!("convert source yaml to json: {err}"),
    })?;
    drop(source_yaml);

    let loaded_overrides = load_overrides(&request.overrides, encrypted)?;
    let mut warnings = loaded_overrides.warnings;
    let apply_result = engine::apply_overrides(root, &loaded_overrides.items, encrypted)?;
    root = apply_result.root;
    warnings.extend(apply_result.warnings);
    warnings.extend(legacy_groups::sanitize(&mut root));

    let profile_dir = Path::new(&request.profile_dir);
    if !root.is_object() {
        return Err("compiled root config must be an object".to_string());
    }
    // Native eBPF is profile-owned: never inject the VPN/Tun runtime patch set even if an older
    // caller forgets to set skip_runtime_patches.
    let apply_runtime_patches =
        !request.skip_runtime_patches && request.run_mode != crate::model::RunMode::Ebpf;
    if apply_runtime_patches {
        patch::patch_static_runtime(&mut root, profile_dir, request.run_mode);
    }
    if request.run_mode == crate::model::RunMode::Ebpf {
        patch::disable_ebpf_tun_entrypoint(&mut root);
    }

    if request.preview {
        patch::patch_preview_runtime(&mut root);
    }

    let object = root
        .as_object_mut()
        .ok_or_else(|| "compiled root config must be an object".to_string())?;
    validate_root_config(object)?;
    if apply_runtime_patches {
        patch::validate_provider_paths(object, profile_dir)?;
    }

    Ok(CompiledRoot { root, warnings })
}

fn load_source_yaml(request: &CompileRequest) -> Result<(String, bool), String> {
    let source_bytes =
        fs::read(&request.profile_path).map_err(|err| format!("read profile yaml: {err}"))?;
    let encrypted = is_age_encrypted(&source_bytes);
    let plaintext = if encrypted {
        decrypt_age_source(&source_bytes, request.age_secret_key.as_deref())?
    } else {
        source_bytes
    };
    let source_yaml =
        String::from_utf8(plaintext).map_err(|err| format!("source yaml is not utf-8: {err}"))?;
    Ok((source_yaml, encrypted))
}

#[cfg(test)]
mod tests {
    use std::sync::atomic::{AtomicUsize, Ordering};
    use std::sync::{Arc, Barrier};
    use std::thread;
    use std::time::Duration;

    use super::with_compile_lock;

    #[test]
    fn compile_lock_serializes_concurrent_requests() {
        let ready = Arc::new(Barrier::new(8));
        let active = Arc::new(AtomicUsize::new(0));
        let peak = Arc::new(AtomicUsize::new(0));
        let mut workers = Vec::new();

        for _ in 0..8 {
            let ready = Arc::clone(&ready);
            let active = Arc::clone(&active);
            let peak = Arc::clone(&peak);
            workers.push(thread::spawn(move || {
                ready.wait();
                with_compile_lock(|| {
                    let current = active.fetch_add(1, Ordering::SeqCst) + 1;
                    peak.fetch_max(current, Ordering::SeqCst);
                    thread::sleep(Duration::from_millis(5));
                    active.fetch_sub(1, Ordering::SeqCst);
                    Ok(())
                })
                .expect("serialized compile work");
            }));
        }

        for worker in workers {
            worker.join().expect("join compile worker");
        }
        assert_eq!(peak.load(Ordering::SeqCst), 1);
    }

    #[test]
    fn compile_lock_recovers_after_panic() {
        let panic = std::panic::catch_unwind(|| {
            let _ = with_compile_lock::<()>(|| panic!("compile panic"));
        });
        assert!(panic.is_err());
        assert_eq!(with_compile_lock(|| Ok("recovered")), Ok("recovered"));
    }
}
