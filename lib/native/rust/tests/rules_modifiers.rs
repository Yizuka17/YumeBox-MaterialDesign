//! List/rules key modifiers (`-start`, `-end`, `-merge`, `+key`, `key+`) and their ordering
//! guarantees across a chain of override documents.

mod common;

use serde_json::{Value as JsonValue, json};

use r#override::engine;
use r#override::model::LoadedOverride;

use common::{override_spec, temp_dir, test_request};

fn rule_texts(root: &JsonValue) -> Vec<&str> {
    root["rules"]
        .as_array()
        .expect("rules")
        .iter()
        .map(|value| value.as_str().unwrap_or_default())
        .collect()
}

#[test]
fn yaml_plus_rules_modifier_matches_rules_start() {
    let root = json!({
        "mode": "rule",
        "rules": ["MATCH,PROXY"]
    });
    let overrides = vec![LoadedOverride {
        path: "plus.yaml".to_string(),
        ext: "yaml".to_string(),
        content: "+rules:\n  - DOMAIN-SUFFIX,baidu.com,DIRECT\n".to_string(),
    }];
    let result = engine::apply_overrides(root, &overrides, false).expect("apply +rules");
    assert_eq!(
        rule_texts(&result.root),
        vec!["DOMAIN-SUFFIX,baidu.com,DIRECT", "MATCH,PROXY"]
    );
}

#[test]
fn multiple_yaml_overrides_accumulate_list_start_end() {
    let root = json!({
        "mode": "rule",
        "rules": ["MATCH,PROXY"]
    });
    let overrides = vec![
        LoadedOverride {
            path: "a.yaml".to_string(),
            ext: "yaml".to_string(),
            content: "rules-start:\n  - DOMAIN-SUFFIX,a.com,DIRECT\n".to_string(),
        },
        LoadedOverride {
            path: "b.yaml".to_string(),
            ext: "yaml".to_string(),
            content: "rules-end:\n  - DOMAIN-SUFFIX,b.com,DIRECT\n".to_string(),
        },
    ];
    let result = engine::apply_overrides(root, &overrides, false).expect("apply two yaml");
    assert_eq!(
        rule_texts(&result.root),
        vec![
            "DOMAIN-SUFFIX,a.com,DIRECT",
            "DOMAIN-SUFFIX,b.com,DIRECT",
            "MATCH,PROXY"
        ]
    );
}

#[test]
fn later_rules_start_applies_after_rules_replacement() {
    let root = json!({
        "mode": "rule",
        "rules": ["MATCH,ORIGINAL"]
    });
    let overrides = vec![
        LoadedOverride {
            path: "replacement.yaml".to_string(),
            ext: "yaml".to_string(),
            content: "rules:\n  - MATCH,REPLACED\n".to_string(),
        },
        LoadedOverride {
            path: "prepend.yaml".to_string(),
            ext: "yaml".to_string(),
            content: "rules-start:\n  - DOMAIN-SUFFIX,example.com,DIRECT\n".to_string(),
        },
    ];

    let result = engine::apply_overrides(root, &overrides, false).expect("apply ordered chain");
    assert_eq!(
        rule_texts(&result.root),
        vec!["DOMAIN-SUFFIX,example.com,DIRECT", "MATCH,REPLACED"]
    );
}

#[test]
fn later_rules_end_is_inserted_before_global_match_rule() {
    let root = json!({
        "mode": "rule",
        "rules": ["MATCH,ORIGINAL"]
    });
    let overrides = vec![
        LoadedOverride {
            path: "global.yaml".to_string(),
            ext: "yaml".to_string(),
            content: r#"
rule-providers:
  global:
    type: http
    url: https://example.com/global.yaml
rules:
  - RULE-SET,global,PROXY
  - MATCH,PROXY
"#
            .to_string(),
        },
        LoadedOverride {
            path: "append.yaml".to_string(),
            ext: "yaml".to_string(),
            content: "rules-end:\n  - DOMAIN-SUFFIX,example.com,DIRECT\n".to_string(),
        },
    ];

    let result = engine::apply_overrides(root, &overrides, false).expect("apply ordered chain");
    assert_eq!(
        rule_texts(&result.root),
        vec![
            "RULE-SET,global,PROXY",
            "DOMAIN-SUFFIX,example.com,DIRECT",
            "MATCH,PROXY",
        ]
    );
}

#[test]
fn built_in_direct_rules_stack_after_built_in_global_rules() {
    let root = json!({
        "mode": "rule",
        "rules": ["MATCH,ORIGINAL"]
    });
    let overrides = vec![
        LoadedOverride {
            path: "builtin-pudding-dog.yaml".to_string(),
            ext: "yaml".to_string(),
            content: include_str!("../../../../data/assets/overrides/builtin/pudding_dog.yaml")
                .to_string(),
        },
        LoadedOverride {
            path: "builtin-add-direct-rules.yaml".to_string(),
            ext: "yaml".to_string(),
            content: include_str!(
                "../../../../data/assets/overrides/builtin/add_direct_rules.yaml"
            )
            .to_string(),
        },
    ];

    let result = engine::apply_overrides(root, &overrides, false).expect("apply built-in chain");
    let rules = result.root["rules"].as_array().expect("rules");
    assert_eq!(rules[0].as_str(), Some("DOMAIN-SUFFIX,baidu.com,DIRECT"));
    assert_eq!(rules[1].as_str(), Some("DOMAIN-SUFFIX,tencent.com,DIRECT"));
    assert_eq!(
        rules.last().and_then(JsonValue::as_str),
        Some("MATCH,PROXY")
    );
    assert!(
        rules
            .iter()
            .any(|rule| rule.as_str() == Some("RULE-SET,geolocation-!cn,PROXY"))
    );
}

#[test]
fn one_yaml_override_combines_rules_replacement_and_all_list_modifiers() {
    let root = json!({
        "mode": "rule",
        "rules": ["MATCH,ORIGINAL"]
    });
    let overrides = vec![LoadedOverride {
        path: "combined.yaml".to_string(),
        ext: "yaml".to_string(),
        content: r#"
rules:
  - MATCH,REPLACED
+rules:
  - DOMAIN-SUFFIX,plus-start.example,DIRECT
rules-start:
  - DOMAIN-SUFFIX,start.example,DIRECT
rules-merge:
  - DOMAIN-SUFFIX,merge.example,DIRECT
rules+:
  - DOMAIN-SUFFIX,plus-end.example,DIRECT
rules-end:
  - DOMAIN-SUFFIX,end.example,DIRECT
"#
        .to_string(),
    }];

    let result = engine::apply_overrides(root, &overrides, false).expect("apply combined rules");
    assert_eq!(
        rule_texts(&result.root),
        vec![
            "DOMAIN-SUFFIX,plus-start.example,DIRECT",
            "DOMAIN-SUFFIX,start.example,DIRECT",
            "DOMAIN-SUFFIX,merge.example,DIRECT",
            "DOMAIN-SUFFIX,plus-end.example,DIRECT",
            "DOMAIN-SUFFIX,end.example,DIRECT",
            "MATCH,REPLACED",
        ]
    );
}

#[test]
fn compile_request_preserves_two_override_file_chain_order() {
    let temp_dir = temp_dir("two-override-chain-test");

    let profile_path = temp_dir.join("profile.yaml");
    std::fs::write(
        &profile_path,
        "mode: rule\nmixed-port: 7890\nrules:\n  - MATCH,ORIGINAL\n",
    )
    .expect("write profile yaml");
    let global_path = temp_dir.join("global.yaml");
    std::fs::write(
        &global_path,
        "mixed-port: 7891\nrules:\n  - DOMAIN-SUFFIX,global.example,PROXY\n  - MATCH,PROXY\n",
    )
    .expect("write global override");
    let append_path = temp_dir.join("append.yaml");
    std::fs::write(
        &append_path,
        "rules-start:\n  - DOMAIN-SUFFIX,first.example,DIRECT\nrules-end:\n  - DOMAIN-SUFFIX,last.example,DIRECT\n",
    )
    .expect("write append override");

    let mut request = test_request(&temp_dir, &profile_path);
    request.skip_runtime_patches = true;
    request.overrides = vec![
        override_spec(&global_path, "yaml"),
        override_spec(&append_path, "yaml"),
    ];

    let result =
        r#override::compile_request(request, false).expect("compile ordered override files");
    let root: JsonValue = serde_yaml::from_str(&result.final_yaml).expect("parse final yaml");
    assert_eq!(root["mixed-port"], JsonValue::from(7891));
    assert_eq!(
        rule_texts(&root),
        vec![
            "DOMAIN-SUFFIX,first.example,DIRECT",
            "DOMAIN-SUFFIX,global.example,PROXY",
            "DOMAIN-SUFFIX,last.example,DIRECT",
            "MATCH,PROXY",
        ]
    );

    let _ = std::fs::remove_dir_all(&temp_dir);
}
