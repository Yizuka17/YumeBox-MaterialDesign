//! age-encrypted profile sources: only the raw JSON output is allowed, and nothing derived from
//! an encrypted profile may leak into messages or files.

mod common;

use age::secrecy::ExposeSecret;
use serde_json::Value as JsonValue;

use common::{encrypt_age, override_spec, temp_dir, test_request};

#[test]
fn compile_raw_request_decrypts_age_source_to_config_raw_json() {
    let temp_dir = temp_dir("age-raw-test");

    let identity = age::x25519::Identity::generate();
    let plaintext = b"mode: rule\nmixed-port: 7890\n";
    let profile_path = temp_dir.join("config.yaml");
    std::fs::write(&profile_path, encrypt_age(plaintext, &identity))
        .expect("write encrypted profile");

    let mut request = test_request(&temp_dir, &profile_path);
    request.age_secret_key = Some(identity.to_string().expose_secret().to_string());
    let override_path = temp_dir.join("override.yaml");
    std::fs::write(&override_path, "mixed-port: 7891\n").expect("write yaml override");
    request.overrides = vec![override_spec(&override_path, "yaml")];

    let result = r#override::compile_raw_request(request).expect("compile encrypted raw config");
    assert!(result.success);
    assert!(result.config_raw.trim_start().starts_with('{'));
    assert!(!result.config_raw.contains("mode: rule"));

    let raw: JsonValue = serde_json::from_str(&result.config_raw).expect("parse raw config json");
    assert_eq!(raw["mode"], JsonValue::String("rule".to_string()));
    assert_eq!(raw["mixed-port"], JsonValue::from(7891));

    let _ = std::fs::remove_dir_all(&temp_dir);
}

#[test]
fn encrypted_empty_override_warning_redacts_override_path() {
    let temp_dir = temp_dir("age-empty-override-test");

    let identity = age::x25519::Identity::generate();
    let profile_path = temp_dir.join("config.yaml");
    std::fs::write(&profile_path, encrypt_age(b"mode: rule\n", &identity))
        .expect("write encrypted profile");
    let empty_override_path = temp_dir.join("empty.js");
    std::fs::write(&empty_override_path, "  \n").expect("write empty override");

    let mut request = test_request(&temp_dir, &profile_path);
    request.age_secret_key = Some(identity.to_string().expose_secret().to_string());
    request.overrides = vec![override_spec(&empty_override_path, "js")];

    let result = r#override::compile_raw_request(request).expect("compile encrypted raw config");
    assert_eq!(result.warnings.len(), 1);
    assert!(result.warnings[0].contains("skip empty override file"));
    assert!(!result.warnings[0].contains(&empty_override_path.to_string_lossy().to_string()));

    let _ = std::fs::remove_dir_all(&temp_dir);
}

#[test]
fn compile_raw_request_requires_age_secret_key_for_encrypted_source() {
    let temp_dir = temp_dir("age-missing-key-test");

    let identity = age::x25519::Identity::generate();
    let profile_path = temp_dir.join("config.yaml");
    std::fs::write(&profile_path, encrypt_age(b"mode: rule\n", &identity))
        .expect("write encrypted profile");

    let request = test_request(&temp_dir, &profile_path);
    let error = r#override::compile_raw_request(request).expect_err("missing key should fail");
    assert!(error.contains("requires ageSecretKey"));

    let _ = std::fs::remove_dir_all(&temp_dir);
}

#[test]
fn compile_request_rejects_yaml_output_for_encrypted_source() {
    let temp_dir = temp_dir("age-yaml-output-test");

    let identity = age::x25519::Identity::generate();
    let profile_path = temp_dir.join("config.yaml");
    std::fs::write(&profile_path, encrypt_age(b"mode: rule\n", &identity))
        .expect("write encrypted profile");

    let output_path = temp_dir.join("runtime.yaml");
    let mut request = test_request(&temp_dir, &profile_path);
    request.output_path = output_path.to_string_lossy().into_owned();
    request.age_secret_key = Some(identity.to_string().expose_secret().to_string());
    let override_path = temp_dir.join("override.js");
    std::fs::write(&override_path, "console.log('must-not-run');\n").expect("write js override");
    request.overrides = vec![override_spec(&override_path, "js")];

    let error =
        r#override::compile_request(request, true).expect_err("encrypted yaml output should fail");
    assert!(error.contains("YAML output is disabled"));
    assert!(!output_path.exists());
    assert!(!override_path.with_extension("log").exists());

    let _ = std::fs::remove_dir_all(&temp_dir);
}
