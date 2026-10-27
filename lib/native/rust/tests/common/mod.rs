//! Shared fixtures for the integration tests.

#![allow(dead_code)]

use std::fs;
use std::io::Write;
use std::path::{Path, PathBuf};
use std::sync::atomic::{AtomicU64, Ordering};

use r#override::model::{CompileRequest, OverrideSpec, REQUEST_SCHEMA_VERSION, RunMode};

static NEXT_TEMP_ID: AtomicU64 = AtomicU64::new(0);

/// Creates a fresh directory under the system temp dir. Unique per process and per call so test
/// binaries running in parallel cannot collide.
pub fn temp_dir(prefix: &str) -> PathBuf {
    let path = std::env::temp_dir().join(format!(
        "yumebox-{prefix}-{}-{}-{}",
        std::process::id(),
        NEXT_TEMP_ID.fetch_add(1, Ordering::Relaxed),
        std::time::SystemTime::now()
            .duration_since(std::time::UNIX_EPOCH)
            .expect("system time before unix epoch")
            .as_nanos()
    ));
    fs::create_dir_all(&path).expect("create temp dir");
    path
}

pub fn test_request(profile_dir: &Path, profile_path: &Path) -> CompileRequest {
    CompileRequest {
        schema_version: REQUEST_SCHEMA_VERSION,
        profile_uuid: "test-profile".to_string(),
        profile_dir: profile_dir.to_string_lossy().into_owned(),
        profile_path: profile_path.to_string_lossy().into_owned(),
        overrides: Vec::new(),
        output_path: String::new(),
        age_secret_key: None,
        run_mode: RunMode::default(),
        skip_runtime_patches: false,
        preview: false,
    }
}

pub fn override_spec(path: &Path, ext: &str) -> OverrideSpec {
    OverrideSpec {
        path: path.to_string_lossy().into_owned(),
        ext: ext.to_string(),
    }
}

/// The provider path the compiler is expected to emit: under the profile's `providers/rules`
/// directory, expressed relative to the mihomo runtime home.
pub fn provider_path_from_runtime_home(profile_dir: &Path, file_name: &str) -> String {
    let runtime_home = profile_dir
        .parent()
        .and_then(Path::parent)
        .map(|files_dir| files_dir.join("mihomo"))
        .unwrap_or_else(|| profile_dir.to_path_buf());
    let provider_path = profile_dir.join("providers").join("rules").join(file_name);
    relative_path_from(&provider_path, &runtime_home)
        .unwrap_or(provider_path)
        .to_string_lossy()
        .replace('\\', "/")
}

pub fn relative_path_from(path: &Path, base: &Path) -> Option<PathBuf> {
    use std::path::Component;
    let path_components = path.components().collect::<Vec<_>>();
    let base_components = base.components().collect::<Vec<_>>();

    let mut common_count = 0;
    while common_count < path_components.len()
        && common_count < base_components.len()
        && path_components[common_count] == base_components[common_count]
    {
        common_count += 1;
    }
    if common_count == 0 {
        return None;
    }

    let mut result = PathBuf::new();
    for component in &base_components[common_count..] {
        if matches!(component, Component::Normal(_)) {
            result.push("..");
        }
    }
    for component in &path_components[common_count..] {
        result.push(component.as_os_str());
    }
    Some(result)
}

pub fn encrypt_age(plaintext: &[u8], identity: &age::x25519::Identity) -> Vec<u8> {
    let recipient = identity.to_public();
    let mut ciphertext = Vec::new();
    let mut writer = age::Encryptor::with_recipients(std::iter::once(&recipient as _))
        .expect("create age encryptor")
        .wrap_output(&mut ciphertext)
        .expect("wrap age output");
    writer.write_all(plaintext).expect("write age plaintext");
    writer.finish().expect("finish age encryption");
    ciphertext
}
