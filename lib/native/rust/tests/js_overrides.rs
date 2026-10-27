//! JS override documents: the helper API, async `main`, logging, failure handling and the realm
//! shared by a chain of scripts.
//!
//! Note: the overrides identified by a bare file name (`a.js`, `step-0.js`, …) write their `.log`
//! next to themselves, i.e. into the crate root while tests run — see `.gitignore`.

mod common;

use std::io::{Read, Write};
use std::net::TcpListener;
use std::thread;

use boa_engine::{Context, Source};
use serde_json::{Value as JsonValue, json};

use r#override::engine;
use r#override::model::LoadedOverride;

use common::temp_dir;

#[test]
fn js_override_can_use_yaml_helpers() {
    let root = json!({ "mode": "rule" });
    let overrides = vec![LoadedOverride {
        path: "test.js".to_string(),
        ext: "js".to_string(),
        content: r#"
function main(profile) {
  const tun = yaml.parse("tun:\n  enable: false\n  stack: mixed\n");
  return deepMerge(profile, tun, true);
}
"#
        .to_string(),
    }];
    let result = engine::apply_overrides(root, &overrides, false).expect("apply js override");
    assert_eq!(result.root["tun"]["enable"], JsonValue::Bool(false));
    assert_eq!(
        result.root["tun"]["stack"],
        JsonValue::String("mixed".to_string())
    );
}

#[test]
fn js_override_supports_async_main_and_writes_log_file() {
    let temp_dir = temp_dir("js-async-test");

    let override_path = temp_dir.join("example.js");
    let overrides = vec![LoadedOverride {
        path: override_path.to_string_lossy().into_owned(),
        ext: "js".to_string(),
        content: r#"
async function main(profile) {
  console.info("boot", { mode: profile.mode });
  await Promise.resolve();
  console.debug("after-await");
  profile.extra = "ok";
  return profile;
}
"#
        .to_string(),
    }];

    let result = engine::apply_overrides(json!({ "mode": "rule" }), &overrides, false)
        .expect("apply async js override");
    assert!(
        result.warnings.is_empty(),
        "unexpected warnings: {:?}",
        result.warnings
    );
    assert_eq!(result.root["extra"], JsonValue::String("ok".to_string()));

    let log_path = override_path.with_extension("log");
    let log_content = std::fs::read_to_string(&log_path).expect("read js override log");
    assert!(log_content.contains("[info] 开始执行脚本"));
    assert!(log_content.contains("[info] \"boot\" {\"mode\":\"rule\"}"));
    assert!(log_content.contains("[debug] \"after-await\""));
    assert!(log_content.contains("[info] 脚本执行成功"));

    let _ = std::fs::remove_dir_all(&temp_dir);
}

/// Environment-dependent: binds a loopback TCP listener on an ephemeral port.
#[test]
fn js_override_fetch_helper_reads_http_payload() {
    let listener = TcpListener::bind(("127.0.0.1", 0)).expect("bind test listener");
    let address = listener.local_addr().expect("listener address");
    let server = thread::spawn(move || {
        let (mut stream, _) = listener.accept().expect("accept request");
        let mut buffer = [0u8; 1024];
        let _ = stream.read(&mut buffer);
        let body = r#"{"port":7890}"#;
        let response = format!(
            "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: {}\r\nConnection: close\r\n\r\n{}",
            body.len(),
            body
        );
        stream
            .write_all(response.as_bytes())
            .expect("write response");
    });

    let temp_dir = temp_dir("js-fetch-test");
    let override_path = temp_dir.join("fetch.js");
    let overrides = vec![LoadedOverride {
        path: override_path.to_string_lossy().into_owned(),
        ext: "js".to_string(),
        content: format!(
            r#"
async function main(profile) {{
  const response = await fetch("http://{address}/override");
  const payload = await response.json();
  console.log("status", response.status);
  profile.port = payload.port;
  return profile;
}}
"#
        ),
    }];

    let result = engine::apply_overrides(json!({ "mode": "rule" }), &overrides, false)
        .expect("apply js fetch override");
    assert!(
        result.warnings.is_empty(),
        "unexpected warnings: {:?}",
        result.warnings
    );
    assert_eq!(result.root["port"], JsonValue::from(7890));

    server.join().expect("join http server");
    let log_content =
        std::fs::read_to_string(override_path.with_extension("log")).expect("read fetch log");
    assert!(log_content.contains("[log] \"status\" 200"));

    let _ = std::fs::remove_dir_all(&temp_dir);
}

#[test]
fn js_override_failure_hard_fails_compile_chain() {
    let temp_dir = temp_dir("js-failure-test");

    let root = json!({ "mode": "rule", "port": 7890 });
    let override_path = temp_dir.join("broken.js");
    let overrides = vec![LoadedOverride {
        path: override_path.to_string_lossy().into_owned(),
        ext: "js".to_string(),
        content: "function main(profile) { throw new Error('boom'); }".to_string(),
    }];

    let error = engine::apply_overrides(root.clone(), &overrides, false)
        .expect_err("broken js override should hard-fail");
    assert!(error.contains("JS override"));
    assert!(error.contains("boom"));

    let log_content =
        std::fs::read_to_string(override_path.with_extension("log")).expect("read failure log");
    assert!(log_content.contains("[exception] 脚本执行失败"));

    let _ = std::fs::remove_dir_all(&temp_dir);
}

#[test]
fn js_override_logs_are_redacted_for_encrypted_profiles() {
    let temp_dir = temp_dir("js-encrypted-log-test");

    let override_path = temp_dir.join("encrypted.js");
    let overrides = vec![LoadedOverride {
        path: override_path.to_string_lossy().into_owned(),
        ext: "js".to_string(),
        content: r#"
function main(profile) {
  console.log("profile", profile);
  throw new Error(`secret mode ${profile.mode}`);
}
"#
        .to_string(),
    }];

    let error = engine::apply_overrides(json!({ "mode": "secret-rule" }), &overrides, true)
        .expect_err("encrypted js override failure should hard-fail");
    assert!(error.contains("JS override failed for encrypted profile"));
    assert!(!error.contains("secret-rule"));
    assert!(!error.contains(&override_path.to_string_lossy().to_string()));

    let log_content =
        std::fs::read_to_string(override_path.with_extension("log")).expect("read encrypted log");
    assert!(log_content.contains("(redacted, encrypted profile)"));
    assert!(!log_content.contains("secret-rule"));
    assert!(!log_content.contains("secret mode"));

    let _ = std::fs::remove_dir_all(&temp_dir);
}

#[test]
fn yaml_then_js_override_chain_applies_both() {
    let root = json!({
        "mode": "rule",
        "port": 1,
        "rules": ["MATCH,PROXY"]
    });
    let overrides = vec![
        LoadedOverride {
            path: "a.yaml".to_string(),
            ext: "yaml".to_string(),
            content: "rules-start:\n  - DOMAIN-SUFFIX,a.com,DIRECT\n".to_string(),
        },
        LoadedOverride {
            path: "b.js".to_string(),
            ext: "js".to_string(),
            content: r#"
function main(profile) {
  profile.port = 2;
  return profile;
}
"#
            .to_string(),
        },
    ];
    let result = engine::apply_overrides(root, &overrides, false).expect("yaml+js");
    assert_eq!(result.root.get("port").and_then(JsonValue::as_i64), Some(2));
    let rules = result
        .root
        .get("rules")
        .and_then(JsonValue::as_array)
        .expect("rules");
    assert_eq!(rules[0].as_str(), Some("DOMAIN-SUFFIX,a.com,DIRECT"));
}

#[test]
fn two_js_overrides_apply_in_order() {
    let root = json!({ "mode": "rule", "port": 1 });
    let overrides = vec![
        LoadedOverride {
            path: "a.js".to_string(),
            ext: "js".to_string(),
            content: "function main(p) { p.port = 2; return p; }".to_string(),
        },
        LoadedOverride {
            path: "b.js".to_string(),
            ext: "js".to_string(),
            content: "function main(p) { p.port = p.port + 3; return p; }".to_string(),
        },
    ];
    let result = engine::apply_overrides(root, &overrides, false).expect("js+js");
    assert_eq!(result.root.get("port").and_then(JsonValue::as_i64), Some(5));
}

#[test]
fn js_runtime_reuses_realm_without_leaking_previous_main() {
    let root = json!({ "mode": "rule", "port": 1 });
    let overrides = vec![
        LoadedOverride {
            path: "first.js".to_string(),
            ext: "js".to_string(),
            content: "function main(p) { p.port = 7; return p; }".to_string(),
        },
        LoadedOverride {
            path: "second-missing-main.js".to_string(),
            ext: "js".to_string(),
            // Intentionally no main(); reused realm must not call first.js's main again.
            content: "const unused = 1;".to_string(),
        },
    ];
    let error = engine::apply_overrides(root, &overrides, false)
        .expect_err("second script without main must hard-fail");
    assert!(
        error.contains("must define main(profile)"),
        "unexpected error: {error}"
    );
}

#[test]
fn many_js_overrides_reuse_runtime_and_accumulate() {
    let mut overrides = Vec::new();
    for index in 0..8 {
        overrides.push(LoadedOverride {
            path: format!("step-{index}.js"),
            ext: "js".to_string(),
            content:
                "function main(profile) { profile.port = (profile.port || 0) + 1; return profile; }"
                    .to_string(),
        });
    }
    let result = engine::apply_overrides(json!({ "mode": "rule", "port": 0 }), &overrides, false)
        .expect("multi js chain");
    assert_eq!(result.root.get("port").and_then(JsonValue::as_i64), Some(8));
}

#[test]
fn map_and_set_iterator_finalizers_survive_forced_gc() {
    let mut context = Context::default();
    for _ in 0..128 {
        context
            .eval(Source::from_bytes(
                r#"
{
  const map = new Map();
  for (let index = 0; index < 128; index++) map.set(index, index);
  for (const [key] of map.entries()) {
    if (key % 2 === 0) map.delete(key);
  }
  const set = new Set(map.keys());
  for (const value of set.values()) {
    if (value % 3 === 0) set.delete(value);
  }
}
"#,
            ))
            .expect("Map/Set iterator script");
        boa_engine::gc::force_collect();
    }
}
