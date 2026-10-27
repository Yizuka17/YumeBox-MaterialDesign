//! YAML override documents: parsing, merge semantics and error reporting.

mod common;

use serde_json::{Value as JsonValue, json};

use r#override::engine;
use r#override::engine::yaml::add_yaml_tags_to_proxies_short_id;
use r#override::model::LoadedOverride;

use common::{temp_dir, test_request};

#[test]
fn yaml_short_id_is_tagged_as_string() {
    let source = r#"
proxies:
  - name: example
    reality-opts:
      short-id: abc123
"#;
    let processed = add_yaml_tags_to_proxies_short_id(source, false);
    assert!(processed.contains("short-id: !!str abc123"));
}

fn compile_raw_from_yaml(source_yaml: &str) -> Result<JsonValue, String> {
    let temp_dir = temp_dir("merge-key-test");
    let profile_path = temp_dir.join("config.yaml");
    std::fs::write(&profile_path, source_yaml).expect("write profile yaml");

    let result = r#override::compile_raw_request(test_request(&temp_dir, &profile_path));
    let _ = std::fs::remove_dir_all(&temp_dir);
    result.map(|raw| serde_json::from_str(&raw.config_raw).expect("parse config_raw json"))
}

#[test]
fn yaml_merge_keys_are_expanded_in_compiled_config() {
    // Anchors supply `type`/`behavior`/`format` via `<<: *anchor`, exactly like a real profile.
    let source = r#"
mode: rule
anchors:
  proxy_first: &proxy_first {type: select, proxies: [DIRECT]}
  domain: &domain {type: http, interval: 86400, behavior: domain, format: mrs}
proxy-groups:
  - {name: YouTube, <<: *proxy_first}
  - {name: Apple, type: url-test, <<: *proxy_first}
rule-providers:
  youtube_domain: {<<: *domain, url: "https://example.invalid/youtube.mrs"}
"#;
    let root = compile_raw_from_yaml(source).expect("compile profile with merge keys");

    let groups = root
        .get("proxy-groups")
        .and_then(JsonValue::as_array)
        .expect("proxy-groups array");

    // Merge supplies `type` when the group has no explicit type.
    let youtube = &groups[0];
    assert_eq!(
        youtube.get("type").and_then(JsonValue::as_str),
        Some("select"),
        "YouTube must inherit type from the anchor"
    );
    assert!(
        youtube.get("<<").is_none(),
        "merge key must be expanded, not left as a literal `<<` key"
    );

    // Explicit field wins over the merged value.
    let apple = &groups[1];
    assert_eq!(
        apple.get("type").and_then(JsonValue::as_str),
        Some("url-test"),
        "explicit type must win over the merged anchor type"
    );

    // Merge also applies to rule-provider mapping values.
    let provider = root
        .get("rule-providers")
        .and_then(|providers| providers.get("youtube_domain"))
        .expect("rule provider");
    assert_eq!(
        provider.get("behavior").and_then(JsonValue::as_str),
        Some("domain"),
        "rule provider must inherit behavior from the anchor"
    );
    assert_eq!(
        provider.get("format").and_then(JsonValue::as_str),
        Some("mrs")
    );
    assert!(provider.get("<<").is_none());
}

#[test]
fn yaml_override_is_applied_with_merge_order() {
    let root = json!({
        "mode": "rule",
        "proxies": [{ "name": "A", "type": "http", "server": "one", "port": 80 }]
    });
    let overrides = vec![LoadedOverride {
        path: "test.yaml".to_string(),
        ext: "yaml".to_string(),
        content: "proxies-end:\n  - name: B\n    type: http\n    server: two\n    port: 81\n"
            .to_string(),
    }];
    let result = engine::apply_overrides(root, &overrides, false).expect("apply yaml override");
    let proxies = result
        .root
        .get("proxies")
        .and_then(JsonValue::as_array)
        .expect("proxies");
    assert_eq!(proxies.len(), 2);
    assert_eq!(
        proxies[1].get("name").and_then(JsonValue::as_str),
        Some("B")
    );
}

#[test]
fn yaml_override_parse_error_includes_override_path() {
    let root = json!({ "mode": "rule" });
    let overrides = vec![LoadedOverride {
        path: "/tmp/custom-routing.yaml".to_string(),
        ext: "yaml".to_string(),
        content: "proxy-groups:\n  -\n  name: Proxy\n".to_string(),
    }];

    let error = engine::apply_overrides(root, &overrides, false)
        .expect_err("broken yaml override should fail");
    assert!(
        error.contains("/tmp/custom-routing.yaml"),
        "unexpected error message: {error}"
    );
}

#[test]
fn map_merge_is_applied_after_same_document_replacement() {
    let root = json!({
        "proxy-providers": {
            "old": { "type": "file", "path": "old.yaml" }
        }
    });
    let overrides = vec![LoadedOverride {
        path: "providers.yaml".to_string(),
        ext: "yaml".to_string(),
        content: r#"
proxy-providers:
  base:
    type: http
    url: https://example.com/base.yaml
proxy-providers-merge:
  base:
    interval: 3600
  extra:
    type: file
    path: extra.yaml
"#
        .to_string(),
    }];

    let result = engine::apply_overrides(root, &overrides, false).expect("apply provider merge");
    let providers = result.root["proxy-providers"]
        .as_object()
        .expect("proxy providers");
    assert!(!providers.contains_key("old"));
    assert_eq!(
        providers["base"]["type"],
        JsonValue::String("http".to_string())
    );
    assert_eq!(providers["base"]["interval"], JsonValue::from(3600));
    assert_eq!(
        providers["extra"]["path"],
        JsonValue::String("extra.yaml".to_string())
    );
}

#[test]
fn empty_override_list_is_noop() {
    let root = json!({ "mode": "rule", "port": 9 });
    let result = engine::apply_overrides(root.clone(), &[], false).expect("empty overrides");
    assert_eq!(result.root, root);
    assert!(result.warnings.is_empty());
}
