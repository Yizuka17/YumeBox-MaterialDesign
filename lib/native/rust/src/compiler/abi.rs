//! C ABI exported by `liboverride.so`.
//!
//! The app itself calls the compiler through JNI (see `src/jni.rs`); this surface exists for the
//! out-of-process/raw-config callers and must keep its symbol names and ownership contract.

use std::ffi::{CStr, CString, c_char};

use crate::compiler::compile_raw_request;
use crate::compiler::result::compile_raw_error_json;
use crate::model::CompileRequest;

/// # Safety
/// Caller must pass a valid null-terminated UTF-8 JSON string.
/// Returns a CompileRawResult JSON string as a Rust-allocated CString that must
/// be freed with override_free_string.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn override_compile_raw(request_json: *const c_char) -> *mut c_char {
    if request_json.is_null() {
        return error_cstring("read raw compile request: null pointer").into_raw();
    }
    let json_str = match unsafe { CStr::from_ptr(request_json) }.to_str() {
        Ok(value) => value,
        Err(err) => {
            return error_cstring(format!("read raw compile request: {err}")).into_raw();
        }
    };
    let request: CompileRequest = match serde_json::from_str(json_str) {
        Ok(request) => request,
        Err(err) => {
            return error_cstring(format!("decode raw compile request: {err}")).into_raw();
        }
    };
    let response = match compile_raw_request(request) {
        Ok(result) => serde_json::to_string(&result)
            .unwrap_or_else(|_| compile_raw_error_json("raw compile result encode failed")),
        Err(err) => compile_raw_error_json(err),
    };
    CString::new(response).unwrap_or_default().into_raw()
}

/// # Safety
/// Caller must pass a pointer previously returned by override_compile_raw.
/// Passing any other pointer or a null pointer is undefined behavior.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn override_free_string(s: *mut c_char) {
    if !s.is_null() {
        drop(unsafe { CString::from_raw(s) });
    }
}

fn error_cstring(message: impl Into<String>) -> CString {
    CString::new(compile_raw_error_json(message)).unwrap_or_default()
}
