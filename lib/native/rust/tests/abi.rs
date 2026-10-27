//! The C ABI exported by `liboverride`.

mod common;

use std::ffi::{CStr, CString};

use serde_json::Value as JsonValue;

use r#override::{override_compile_raw, override_free_string};

use common::{encrypt_age, temp_dir, test_request};

#[test]
fn override_compile_raw_returns_structured_error_result() {
    let temp_dir = temp_dir("age-raw-abi-error-test");

    let identity = age::x25519::Identity::generate();
    let profile_path = temp_dir.join("config.yaml");
    std::fs::write(&profile_path, encrypt_age(b"mode: rule\n", &identity))
        .expect("write encrypted profile");

    let request = test_request(&temp_dir, &profile_path);
    let request_json = serde_json::to_string(&request).expect("encode raw request");
    let request_c = CString::new(request_json).expect("request has no nul bytes");

    let ptr = unsafe { override_compile_raw(request_c.as_ptr()) };
    assert!(!ptr.is_null());
    let response = unsafe { CStr::from_ptr(ptr).to_string_lossy().into_owned() };
    unsafe { override_free_string(ptr) };

    let result: JsonValue = serde_json::from_str(&response).expect("parse raw abi result");
    assert_eq!(result["success"], JsonValue::Bool(false));
    assert!(
        result["error"]
            .as_str()
            .expect("error should be present")
            .contains("requires ageSecretKey")
    );
    assert_eq!(result["configRaw"], JsonValue::String(String::new()));

    let _ = std::fs::remove_dir_all(&temp_dir);
}

#[test]
fn override_compile_raw_rejects_a_null_request() {
    let ptr = unsafe { override_compile_raw(std::ptr::null()) };
    assert!(!ptr.is_null());
    let response = unsafe { CStr::from_ptr(ptr).to_string_lossy().into_owned() };
    unsafe { override_free_string(ptr) };

    let result: JsonValue = serde_json::from_str(&response).expect("parse raw abi result");
    assert_eq!(result["success"], JsonValue::Bool(false));
    assert!(
        result["error"]
            .as_str()
            .expect("error should be present")
            .contains("null pointer")
    );
}

#[test]
fn override_free_string_ignores_null() {
    unsafe { override_free_string(std::ptr::null_mut()) };
}
