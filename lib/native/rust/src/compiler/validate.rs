//! Checks the compiler runs before handing a config to the core, so an invalid value fails here
//! with a readable message instead of killing mihomo at parse time.

use serde_json::{Map as JsonMap, Value as JsonValue};

use crate::model::{CompileRequest, REQUEST_SCHEMA_VERSION};

pub fn validate_request_schema(request: &CompileRequest) -> Result<(), String> {
    if request.schema_version != REQUEST_SCHEMA_VERSION {
        return Err(format!(
            "unsupported schema version: {}",
            request.schema_version
        ));
    }
    Ok(())
}

pub fn validate_root_config(object: &JsonMap<String, JsonValue>) -> Result<(), String> {
    validate_geosite_matcher(object)?;
    Ok(())
}

fn validate_geosite_matcher(object: &JsonMap<String, JsonValue>) -> Result<(), String> {
    let Some(value) = object.get("geosite-matcher") else {
        return Ok(());
    };
    let Some(value) = value.as_str() else {
        return Err(
            "geosite-matcher must be a string (supported values: mph, succinct)".to_string(),
        );
    };
    if matches!(value, "mph" | "succinct") {
        return Ok(());
    }
    Err(format!(
        "geosite-matcher must be one of: mph, succinct (got {value})"
    ))
}

#[cfg(test)]
mod tests {
    use super::*;
    use serde_json::json;

    fn root(value: JsonValue) -> JsonMap<String, JsonValue> {
        value.as_object().expect("object").clone()
    }

    #[test]
    fn geosite_matcher_accepts_known_values() {
        assert!(validate_root_config(&root(json!({ "geosite-matcher": "mph" }))).is_ok());
        assert!(validate_root_config(&root(json!({ "geosite-matcher": "succinct" }))).is_ok());
        assert!(validate_root_config(&root(json!({ "mode": "rule" }))).is_ok());
    }

    #[test]
    fn geosite_matcher_rejects_unknown_and_non_string_values() {
        let error = validate_root_config(&root(json!({ "geosite-matcher": "nope" })))
            .expect_err("unknown matcher must fail");
        assert!(error.contains("must be one of"));
        let error = validate_root_config(&root(json!({ "geosite-matcher": 1 })))
            .expect_err("non-string matcher must fail");
        assert!(error.contains("must be a string"));
    }
}
