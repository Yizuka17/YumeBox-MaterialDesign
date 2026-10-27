//! JNI surface bound to the Kotlin `Compiler` object.
//!
//! The exported symbol names are part of the app's contract — see
//! `core/src/core/bridge/Compiler.kt`. Do not rename them.

use age::secrecy::ExposeSecret;
use jni::errors::ThrowRuntimeExAndDefault;
use jni::objects::{JObject, JString};
use jni::sys::jstring;
use jni::{Env, EnvUnowned};
use std::any::Any;
use std::panic::{AssertUnwindSafe, catch_unwind};

use crate::compiler::compile_request;
use crate::compiler::result::{compile_error_json, encode_compile_result};
use crate::model::CompileRequest;

// Age x25519 keygen, moved off the (deleted) Go core path. Bound to the Kotlin `Compiler` object.
#[unsafe(no_mangle)]
pub extern "system" fn Java_com_github_yumelira_yumebox_core_bridge_Compiler_nativeGenAgeKey<
    'local,
>(
    mut env: EnvUnowned<'local>,
    _compiler: JObject<'local>,
) -> jstring {
    env.with_env(|env| {
        let identity = age::x25519::Identity::generate();
        let secret = identity.to_string().expose_secret().to_string();
        let public = identity.to_public().to_string();
        let json = serde_json::json!({ "secretKey": secret, "publicKey": public }).to_string();
        Ok::<_, jni::errors::Error>(new_java_string(env, json))
    })
    .resolve::<ThrowRuntimeExAndDefault>()
}

/// Derives the age public key for a secret key, or "" when it does not parse.
#[unsafe(no_mangle)]
pub extern "system" fn Java_com_github_yumelira_yumebox_core_bridge_Compiler_nativeAgePublicKey<
    'local,
>(
    mut env: EnvUnowned<'local>,
    _compiler: JObject<'local>,
    secret: JString<'local>,
) -> jstring {
    env.with_env(|env| {
        let secret_str = match secret.try_to_string(env) {
            Ok(value) => value,
            Err(_) => {
                env.exception_clear();
                return Ok::<_, jni::errors::Error>(new_java_string(env, String::new()));
            }
        };
        let public = secret_str
            .trim()
            .parse::<age::x25519::Identity>()
            .map(|identity| identity.to_public().to_string())
            .unwrap_or_default();
        Ok::<_, jni::errors::Error>(new_java_string(env, public))
    })
    .resolve::<ThrowRuntimeExAndDefault>()
}

// Full compile (write_output = false): returns CompileResult{finalYaml,...}. This is the compiler
// seam for the out-of-process core — the app compiles here and streams finalYaml to the core; there
// is no in-process load path. Bound to the Kotlin `Compiler` object.
#[unsafe(no_mangle)]
pub extern "system" fn Java_com_github_yumelira_yumebox_core_bridge_Compiler_nativeCompile<
    'local,
>(
    mut env: EnvUnowned<'local>,
    _compiler: JObject<'local>,
    request_json: JString<'local>,
) -> jstring {
    env.with_env(|env| Ok::<_, jni::errors::Error>(handle_compile_request(env, request_json)))
        .resolve::<ThrowRuntimeExAndDefault>()
}

fn handle_compile_request(env: &mut Env, request_json: JString) -> jstring {
    let payload = match request_json.try_to_string(env) {
        Ok(value) => value,
        Err(err) => {
            env.exception_clear();
            return new_java_string(env, compile_error_json(format!("read JNI request: {err}")));
        }
    };

    let result = catch_compiler_panic(|| match serde_json::from_str::<CompileRequest>(&payload) {
        Ok(request) => compile_request(request, false),
        Err(err) => Err(format!("decode override request: {err}")),
    });

    let response_json = match result {
        Ok(result) => encode_compile_result(result),
        Err(err) => compile_error_json(err),
    };
    new_java_string(env, response_json)
}

/// Keep an engine panic from unwinding across JNI and killing the hosting Android process.
fn catch_compiler_panic<T>(compile: impl FnOnce() -> Result<T, String>) -> Result<T, String> {
    catch_unwind(AssertUnwindSafe(compile)).unwrap_or_else(|panic| {
        Err(format!(
            "native override compiler panicked: {}",
            panic_message(panic)
        ))
    })
}

fn panic_message(panic: Box<dyn Any + Send>) -> String {
    panic
        .downcast_ref::<&str>()
        .map(|message| (*message).to_string())
        .or_else(|| panic.downcast_ref::<String>().cloned())
        .unwrap_or_else(|| "unknown panic".to_string())
}

fn new_java_string(env: &mut Env, content: String) -> jstring {
    env.new_string(content)
        .expect("create JNI response string")
        .into_raw()
}

#[cfg(test)]
mod tests {
    use super::catch_compiler_panic;

    #[test]
    fn compiler_panic_becomes_a_compile_error() {
        let result = catch_compiler_panic::<()>(|| panic!("Boa collector failure"));
        assert_eq!(
            result,
            Err("native override compiler panicked: Boa collector failure".to_string())
        );
    }
}
