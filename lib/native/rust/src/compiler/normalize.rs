//! Converts the compiled JSON tree into the YAML document handed to the core.
//!
//! Beyond the type mapping this fixes the key order: fields the schema knows about are emitted in
//! the documented order, everything else follows in map order.
//!
//! The tree is consumed rather than borrowed — strings move into the YAML document instead of
//! being cloned, which for a large subscription saves one full copy of the config.

use serde_json::{Map as JsonMap, Value as JsonValue};
use serde_yaml::{Mapping as YamlMapping, Value as YamlValue};

use crate::compiler::schema::ordered_keys;
use crate::model::SchemaId;

pub fn normalize_root(value: JsonValue) -> YamlValue {
    normalize_object_with_schema(value, SchemaId::Root)
}

fn normalize_object_with_schema(value: JsonValue, schema: SchemaId) -> YamlValue {
    let mut object = match value {
        JsonValue::Object(object) => object,
        other => return normalize_generic_value(other),
    };

    let mut mapping = YamlMapping::new();
    // Pulling schema keys out first leaves exactly the unknown keys behind, so the tail loop needs
    // no membership test against the schema list.
    for key in ordered_keys(schema) {
        if let Some(field_value) = object.remove(*key) {
            mapping.insert(
                YamlValue::String((*key).to_string()),
                normalize_field_value(schema, key, field_value),
            );
        }
    }

    // serde_json's default Map is already key-ordered.
    for (key, field_value) in object {
        mapping.insert(YamlValue::String(key), normalize_generic_value(field_value));
    }

    YamlValue::Mapping(mapping)
}

fn normalize_field_value(schema: SchemaId, key: &str, value: JsonValue) -> YamlValue {
    match (schema, key) {
        (SchemaId::Root, "dns") => normalize_object_with_schema(value, SchemaId::Dns),
        (SchemaId::Root, "external-controller-cors") => {
            normalize_object_with_schema(value, SchemaId::ExternalControllerCors)
        }
        (SchemaId::Root, "profile") => normalize_object_with_schema(value, SchemaId::Profile),
        (SchemaId::Root, "tun") => normalize_object_with_schema(value, SchemaId::Tun),
        (SchemaId::Root, "sniffer") => normalize_object_with_schema(value, SchemaId::Sniffer),
        (SchemaId::Root, "geox-url") => normalize_object_with_schema(value, SchemaId::GeoxUrl),
        (SchemaId::Root, "clash-for-android") => normalize_object_with_schema(value, SchemaId::App),
        (SchemaId::Dns, "fallback-filter") => {
            normalize_object_with_schema(value, SchemaId::DnsFallbackFilter)
        }
        (SchemaId::Sniffer, "sniff") => normalize_object_with_schema(value, SchemaId::Sniff),
        (SchemaId::Sniff, "HTTP") | (SchemaId::Sniff, "TLS") | (SchemaId::Sniff, "QUIC") => {
            normalize_object_with_schema(value, SchemaId::Protocol)
        }
        (SchemaId::Root, "proxies") => normalize_object_list(value, SchemaId::ProxyItem),
        (SchemaId::Root, "proxy-groups") => normalize_object_list(value, SchemaId::ProxyGroupItem),
        (SchemaId::Root, "rule-providers") | (SchemaId::Root, "proxy-providers") => {
            normalize_object_map(value, Some(SchemaId::ProviderItem))
        }
        (SchemaId::Root, "hosts") | (SchemaId::Root, "sub-rules") => {
            normalize_object_map(value, None)
        }
        _ => normalize_generic_value(value),
    }
}

fn normalize_object_list(value: JsonValue, item_schema: SchemaId) -> YamlValue {
    let items = match value {
        JsonValue::Array(items) => items,
        other => return normalize_generic_value(other),
    };
    YamlValue::Sequence(
        items
            .into_iter()
            .map(|item| normalize_object_with_schema(item, item_schema))
            .collect(),
    )
}

fn normalize_object_map(value: JsonValue, item_schema: Option<SchemaId>) -> YamlValue {
    let object = match value {
        JsonValue::Object(object) => object,
        other => return normalize_generic_value(other),
    };

    let mut mapping = YamlMapping::new();
    for (key, field_value) in object {
        let normalized = match item_schema {
            Some(schema) if field_value.is_object() => {
                normalize_object_with_schema(field_value, schema)
            }
            _ => normalize_generic_value(field_value),
        };
        mapping.insert(YamlValue::String(key), normalized);
    }
    YamlValue::Mapping(mapping)
}

pub fn normalize_generic_value(value: JsonValue) -> YamlValue {
    match value {
        JsonValue::Null => YamlValue::Null,
        JsonValue::Bool(value) => YamlValue::Bool(value),
        JsonValue::Number(value) => normalize_number(&value),
        JsonValue::String(value) => YamlValue::String(value),
        JsonValue::Array(items) => {
            YamlValue::Sequence(items.into_iter().map(normalize_generic_value).collect())
        }
        JsonValue::Object(object) => normalize_generic_object(object),
    }
}

fn normalize_generic_object(object: JsonMap<String, JsonValue>) -> YamlValue {
    let mut mapping = YamlMapping::new();
    for (key, field_value) in object {
        mapping.insert(YamlValue::String(key), normalize_generic_value(field_value));
    }
    YamlValue::Mapping(mapping)
}

fn normalize_number(value: &serde_json::Number) -> YamlValue {
    if let Some(number) = value.as_i64() {
        return YamlValue::Number(number.into());
    }
    if let Some(number) = value.as_u64() {
        return YamlValue::Number(number.into());
    }
    if let Some(number) = value.as_f64() {
        return YamlValue::Number(number.into());
    }
    YamlValue::Null
}
