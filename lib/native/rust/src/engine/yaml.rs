//! YAML ⇄ JSON conversion for override documents and CLI helpers.

pub mod short_id;

use serde_json::Value as JsonValue;
use serde_yaml::Value as YamlValue;

pub use short_id::add_yaml_tags_to_proxies_short_id;

/// Where a YAML → JSON conversion failed. Callers phrase the message for their own context.
#[derive(Debug)]
pub enum YamlToJsonError {
    Parse(serde_yaml::Error),
    Merge(serde_yaml::Error),
    Convert(serde_json::Error),
}

impl std::fmt::Display for YamlToJsonError {
    fn fmt(&self, formatter: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            YamlToJsonError::Parse(error) | YamlToJsonError::Merge(error) => error.fmt(formatter),
            YamlToJsonError::Convert(error) => error.fmt(formatter),
        }
    }
}

/// Parses a YAML document into a JSON tree.
///
/// Fast path: deserialize straight into a `serde_json::Value`. The obvious implementation builds a
/// `serde_yaml::Value` first and re-serializes it into JSON, which walks and reallocates the whole
/// document a second time — for a subscription with thousands of proxies that is one of the most
/// expensive steps of a compile.
///
/// Documents the JSON model cannot take directly (non-string mapping keys) and documents using
/// merge keys (`<<`, which only `serde_yaml::Value::apply_merge` can expand) fall back to the
/// original path, so behavior is unchanged.
pub fn yaml_to_json(content: &str) -> Result<JsonValue, YamlToJsonError> {
    let has_merge_keys = content.contains("<<:");
    if !has_merge_keys && let Ok(value) = serde_yaml::from_str::<JsonValue>(content) {
        return Ok(value);
    }
    yaml_to_json_via_yaml_value(content, has_merge_keys)
}

fn yaml_to_json_via_yaml_value(
    content: &str,
    has_merge_keys: bool,
) -> Result<JsonValue, YamlToJsonError> {
    let mut value: YamlValue = serde_yaml::from_str(content).map_err(YamlToJsonError::Parse)?;
    // serde_yaml resolves `&`/`*` aliases but not YAML merge keys (`<<`). Applying merges walks
    // the full document, so avoid it entirely unless the source can actually contain one.
    if has_merge_keys {
        value.apply_merge().map_err(YamlToJsonError::Merge)?;
    }
    serde_json::to_value(value).map_err(YamlToJsonError::Convert)
}

pub fn parse_yaml_override(content: &str) -> Result<JsonValue, String> {
    let processed_content = add_yaml_tags_to_proxies_short_id(content, false);
    yaml_to_json(&processed_content).map_err(|err| err.to_string())
}

pub fn parse_yaml_to_json_string(content: &str) -> Result<String, String> {
    let json_value = parse_yaml_override(content)?;
    serde_json::to_string(&json_value).map_err(|err| err.to_string())
}

pub fn stringify_json_to_yaml_string(content: &str) -> Result<String, String> {
    let json_value: JsonValue = serde_json::from_str(content).map_err(|err| err.to_string())?;
    serde_yaml::to_string(&json_value).map_err(|err| err.to_string())
}

#[cfg(test)]
mod tests {
    use super::*;

    const CORPUS: &[&str] = &[
        "mode: rule\nmixed-port: 7890\nipv6: false\nsecret: ~\n",
        "proxies:\n  - name: a\n    port: 443\n    reality-opts:\n      short-id: !!str 0123\n",
        "rules:\n  - MATCH,PROXY\nempty-list: []\nempty-map: {}\nfloat: 1.5\n",
        "anchor: &base {type: select}\ngroup:\n  <<: *base\n  name: G\n",
        "alias: &item value\nuse: *item\n",
        "hosts:\n  \"example.com\": 1.2.3.4\nnested:\n  deep:\n    deeper: [1, 2, 3]\n",
        "multiline: |\n  line one\n  line two\nquoted: \"7890\"\n",
        "negative: -1\nzero: 0\ntruthy: yes\nempty-string: \"\"\n",
    ];

    #[test]
    fn fast_path_matches_yaml_value_path() {
        for content in CORPUS {
            let fast = yaml_to_json(content).expect("fast path parse");
            let slow = yaml_to_json_via_yaml_value(content, content.contains("<<:"))
                .expect("yaml value path parse");
            assert_eq!(fast, slow, "mismatch for document:\n{content}");
        }
    }

    #[test]
    fn non_string_keys_fall_back_to_the_yaml_value_path() {
        // serde_json::Value cannot deserialize an integer mapping key directly; the fallback
        // stringifies it exactly like the original implementation did.
        let parsed = yaml_to_json("1: one\n2: two\n").expect("integer keys are supported");
        assert_eq!(parsed["1"], JsonValue::String("one".to_string()));
    }

    #[test]
    fn parse_errors_are_reported() {
        let error = parse_yaml_override("proxy-groups:\n  -\n  name: Proxy\n")
            .expect_err("broken yaml must fail");
        assert!(!error.is_empty());
    }

    #[test]
    fn merge_keys_are_expanded() {
        let parsed = yaml_to_json("base: &base {type: select}\ngroup:\n  <<: *base\n  name: G\n")
            .expect("merge");
        assert_eq!(
            parsed["group"]["type"],
            JsonValue::String("select".to_string())
        );
        assert!(parsed["group"].get("<<").is_none());
    }
}
