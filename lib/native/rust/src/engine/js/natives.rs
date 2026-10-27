//! Rust-backed globals installed into the realm.
//!
//! Everything here is an implementation detail of `prelude.js`; user scripts see the friendly
//! wrappers (`yaml`, `console`, `fetch`, `Buffer`) rather than these `__…Native` functions.

use boa_engine::object::FunctionObjectBuilder;
use boa_engine::property::Attribute;
use boa_engine::{Context, JsNativeError, JsResult, JsValue, NativeFunction, js_string};

use crate::engine::js::base64::{base64_decode_string, base64_encode_string};
use crate::engine::js::fetch::{FetchRequest, execute_fetch};
use crate::engine::js::log::append_override_log_from_context;

pub fn js_string_value(value: &str) -> JsValue {
    JsValue::new(js_string!(value))
}

pub fn js_error(message: String) -> boa_engine::JsError {
    JsNativeError::typ().with_message(message).into()
}

fn arg_to_string(args: &[JsValue], index: usize, context: &mut Context) -> JsResult<String> {
    Ok(args
        .get(index)
        .cloned()
        .unwrap_or_default()
        .to_string(context)?
        .to_std_string_escaped())
}

fn register_global_function(
    context: &mut Context,
    name: &'static str,
    length: usize,
    function: NativeFunction,
) -> Result<(), String> {
    let object = FunctionObjectBuilder::new(context.realm(), function)
        .name(name)
        .length(length)
        .build();
    context
        .register_global_property(js_string!(name), object, Attribute::all())
        .map_err(|err| format!("register {name} failed: {err}"))
}

pub fn register_native_helpers(context: &mut Context) -> Result<(), String> {
    register_global_function(
        context,
        "__yamlParseNative",
        1,
        NativeFunction::from_copy_closure(|_, args, context| {
            let content = arg_to_string(args, 0, context)?;
            let payload =
                crate::engine::yaml::parse_yaml_to_json_string(&content).map_err(js_error)?;
            Ok(js_string_value(&payload))
        }),
    )?;

    register_global_function(
        context,
        "__yamlStringifyNative",
        1,
        NativeFunction::from_copy_closure(|_, args, context| {
            let content = arg_to_string(args, 0, context)?;
            let payload =
                crate::engine::yaml::stringify_json_to_yaml_string(&content).map_err(js_error)?;
            Ok(js_string_value(&payload))
        }),
    )?;

    register_global_function(
        context,
        "__b64dNative",
        1,
        NativeFunction::from_copy_closure(|_, args, context| {
            let content = arg_to_string(args, 0, context)?;
            let decoded = base64_decode_string(&content).map_err(js_error)?;
            Ok(js_string_value(&decoded))
        }),
    )?;

    register_global_function(
        context,
        "__b64eNative",
        1,
        NativeFunction::from_copy_closure(|_, args, context| {
            let content = arg_to_string(args, 0, context)?;
            Ok(js_string_value(&base64_encode_string(content.as_bytes())))
        }),
    )?;

    register_global_function(
        context,
        "__consoleLogNative",
        2,
        NativeFunction::from_copy_closure(|_, args, context| {
            let level = arg_to_string(args, 0, context)?;
            let message = arg_to_string(args, 1, context)?;
            let _ = append_override_log_from_context(context, &level, &message);
            Ok(JsValue::undefined())
        }),
    )?;

    register_global_function(
        context,
        "__fetchNative",
        1,
        NativeFunction::from_copy_closure(|_, args, context| {
            let request_json = arg_to_string(args, 0, context)?;
            let request: FetchRequest =
                serde_json::from_str(&request_json).map_err(|err| js_error(err.to_string()))?;
            let payload = execute_fetch(request).map_err(js_error)?;
            let payload_json =
                serde_json::to_string(&payload).map_err(|err| js_error(err.to_string()))?;
            Ok(js_string_value(&payload_json))
        }),
    )?;

    Ok(())
}
