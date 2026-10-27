//! Provider `path:` normalization — every shape a profile may ship must land under
//! `<profile>/providers/<kind>/`, relative to the mihomo runtime home.

mod common;

use serde_json::Value as JsonValue;

use common::{provider_path_from_runtime_home, temp_dir, test_request};

fn compiled_rule_provider_path(profile_yaml: &str, provider_name: &str, file_name: &str) {
    let temp_dir = temp_dir("provider-path-test");
    let profile_path = temp_dir.join("profile.yaml");
    std::fs::write(&profile_path, profile_yaml).expect("write profile yaml");

    let result = r#override::compile_request(test_request(&temp_dir, &profile_path), false)
        .expect("compile request should succeed");
    let root: JsonValue = serde_yaml::from_str(&result.final_yaml).expect("parse final yaml");
    let expected_path = provider_path_from_runtime_home(&temp_dir, file_name);
    assert_eq!(
        root["rule-providers"][provider_name]["path"].as_str(),
        Some(expected_path.as_str())
    );

    let _ = std::fs::remove_dir_all(&temp_dir);
}

#[test]
fn compile_request_preserves_existing_provider_path() {
    compiled_rule_provider_path(
        r#"
mode: rule
rules:
  - RULE-SET,geolocation-!cn,PROXY
rule-providers:
  geolocation-!cn:
    type: http
    url: https://example.com/geolocation-!cn.yaml
    path: providers/rules/geolocation-!cn.yaml
    behavior: domain
    interval: 86400
    format: yaml
"#,
        "geolocation-!cn",
        "geolocation-!cn.yaml",
    );
}

#[test]
fn compile_request_preserves_existing_dot_provider_path() {
    compiled_rule_provider_path(
        r#"
mode: rule
rules:
  - RULE-SET,ads_domain,REJECT
rule-providers:
  ads_domain:
    type: http
    url: https://example.com/ads_domain.mrs
    path: ./providers/rules/ads_domain.mrs
    behavior: domain
    interval: 86400
    format: mrs
"#,
        "ads_domain",
        "ads_domain.mrs",
    );
}

#[test]
fn compile_request_rewrites_legacy_ruleset_provider_path() {
    compiled_rule_provider_path(
        r#"
mode: rule
rules:
  - RULE-SET,advertising,REJECT
rule-providers:
  advertising:
    type: http
    url: https://example.com/advertising.yaml
    path: ./ruleset/advertising.yaml
    behavior: domain
    interval: 86400
    format: yaml
"#,
        "advertising",
        "advertising.yaml",
    );
}

#[test]
fn compile_request_normalizes_absolute_provider_path_to_profile_scope() {
    let temp_dir = temp_dir("provider-absolute-path-test");

    let provider_path = temp_dir
        .join("providers")
        .join("rules")
        .join("geolocation-!cn.yaml");
    std::fs::create_dir_all(provider_path.parent().expect("provider parent"))
        .expect("create provider dir");
    std::fs::write(&provider_path, "payload").expect("write provider file");

    let profile_path = temp_dir.join("profile.yaml");
    std::fs::write(
        &profile_path,
        format!(
            r#"
mode: rule
rules:
  - RULE-SET,geolocation-!cn,PROXY
rule-providers:
  geolocation-!cn:
    type: http
    url: https://example.com/geolocation-!cn.yaml
    path: {}
    behavior: domain
    interval: 86400
    format: yaml
"#,
            provider_path.to_string_lossy().replace('\\', "/"),
        ),
    )
    .expect("write profile yaml");

    let result = r#override::compile_request(test_request(&temp_dir, &profile_path), false)
        .expect("compile request should succeed");
    let root: JsonValue = serde_yaml::from_str(&result.final_yaml).expect("parse final yaml");
    let expected_path = provider_path_from_runtime_home(&temp_dir, "geolocation-!cn.yaml");
    assert_eq!(
        root["rule-providers"]["geolocation-!cn"]["path"].as_str(),
        Some(expected_path.as_str())
    );

    let _ = std::fs::remove_dir_all(&temp_dir);
}
