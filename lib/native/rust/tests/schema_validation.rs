//! Root config validation surfaced through a full compile.

mod common;

use serde_json::{Value as JsonValue, json};

use r#override::compiler::normalize::normalize_root;
use r#override::model::CompileResult;

use common::{temp_dir, test_request};

fn compile_root_with_geosite_matcher(value: Option<JsonValue>) -> Result<CompileResult, String> {
    let temp_dir = temp_dir("compiler-test");

    let mut root = serde_json::Map::new();
    root.insert("mode".to_string(), JsonValue::String("rule".to_string()));
    if let Some(value) = value {
        root.insert("geosite-matcher".to_string(), value);
    }

    let profile_path = temp_dir.join("profile.yaml");
    let yaml = serde_yaml::to_string(&normalize_root(JsonValue::Object(root)))
        .expect("serialize profile yaml");
    std::fs::write(&profile_path, yaml).expect("write profile yaml");

    let result = r#override::compile_request(test_request(&temp_dir, &profile_path), false);
    let _ = std::fs::remove_dir_all(&temp_dir);
    result
}

#[test]
fn compile_request_accepts_explicit_geosite_matcher() {
    let result = compile_root_with_geosite_matcher(Some(json!("mph")))
        .expect("compile request should succeed");
    assert!(result.success);
    let root: JsonValue = serde_yaml::from_str(&result.final_yaml).expect("parse final yaml");
    assert_eq!(
        root.get("geosite-matcher").and_then(JsonValue::as_str),
        Some("mph")
    );
}

#[test]
fn compile_request_rejects_invalid_geosite_matcher() {
    let err = compile_root_with_geosite_matcher(Some(json!("invalid")))
        .expect_err("compile request should fail for invalid geosite-matcher");
    assert!(
        err.contains("geosite-matcher must be one of"),
        "unexpected error message: {err}"
    );
}
