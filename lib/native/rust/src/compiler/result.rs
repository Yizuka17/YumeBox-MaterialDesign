//! JSON encoding of compile results, shared by the JNI, C ABI and CLI entry points.
//!
//! Every entry point must answer with a well-formed result document even when encoding itself
//! fails, hence the hard-coded fallbacks.

use crate::model::{CompileRawResult, CompileResult};

const COMPILE_ENCODE_FALLBACK: &str = "{\"success\":false,\"fingerprint\":\"\",\"finalYaml\":\"\",\"warnings\":[],\"error\":\"override result encode failed\"}";
const COMPILE_ERROR_FALLBACK: &str = "{\"success\":false,\"fingerprint\":\"\",\"finalYaml\":\"\",\"warnings\":[],\"error\":\"override processor failed\"}";
const COMPILE_RAW_ERROR_FALLBACK: &str = "{\"success\":false,\"fingerprint\":\"\",\"configRaw\":\"\",\"warnings\":[],\"error\":\"raw compile failed\"}";

pub fn encode_compile_result(result: CompileResult) -> String {
    serde_json::to_string(&result).unwrap_or_else(|_| COMPILE_ENCODE_FALLBACK.to_string())
}

pub fn compile_error_json(message: impl Into<String>) -> String {
    serde_json::to_string(&CompileResult {
        success: false,
        fingerprint: String::new(),
        final_yaml: String::new(),
        warnings: Vec::new(),
        error: Some(message.into()),
    })
    .unwrap_or_else(|_| COMPILE_ERROR_FALLBACK.to_string())
}

pub fn compile_raw_error_json(message: impl Into<String>) -> String {
    serde_json::to_string(&CompileRawResult {
        success: false,
        fingerprint: String::new(),
        config_raw: String::new(),
        warnings: Vec::new(),
        error: Some(message.into()),
    })
    .unwrap_or_else(|_| COMPILE_RAW_ERROR_FALLBACK.to_string())
}

#[cfg(test)]
mod tests {
    use super::*;
    use serde_json::Value as JsonValue;

    #[test]
    fn error_documents_are_well_formed() {
        for payload in [
            compile_error_json("boom"),
            compile_raw_error_json("boom"),
            COMPILE_ENCODE_FALLBACK.to_string(),
            COMPILE_ERROR_FALLBACK.to_string(),
            COMPILE_RAW_ERROR_FALLBACK.to_string(),
        ] {
            let parsed: JsonValue = serde_json::from_str(&payload).expect("valid json");
            assert_eq!(parsed["success"], JsonValue::Bool(false));
            assert!(parsed["error"].is_string());
        }
    }
}
