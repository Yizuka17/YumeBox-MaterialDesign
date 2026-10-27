//! Generic JSON value helpers used while applying override documents.

use std::collections::HashSet;

use serde_json::{Map as JsonMap, Value as JsonValue};

use crate::compiler::patch::keys::{is_escaped_literal_key, unescape_literal_key};

/// Rewrites `<escaped>` keys back to their literal form, in place and without reallocating when
/// the subtree contains no escaped key (the overwhelmingly common case).
pub fn unescape_literal_keys(value: &mut JsonValue) {
    match value {
        JsonValue::Object(object) => {
            if object.keys().any(|key| is_escaped_literal_key(key)) {
                let previous = std::mem::take(object);
                *object = previous
                    .into_iter()
                    .map(|(key, value)| (unescape_literal_key(key), value))
                    .collect();
            }
            for field in object.values_mut() {
                unescape_literal_keys(field);
            }
        }
        JsonValue::Array(items) => {
            for item in items {
                unescape_literal_keys(item);
            }
        }
        _ => {}
    }
}

/// Takes a patch value as-is, only undoing key escaping.
pub fn into_raw_value(mut value: JsonValue) -> JsonValue {
    unescape_literal_keys(&mut value);
    value
}

/// Interprets a patch value as list items: `null` contributes nothing, a list contributes its
/// items, anything else contributes itself.
pub fn collect_array_items(value: JsonValue) -> Vec<JsonValue> {
    match value {
        JsonValue::Null => Vec::new(),
        JsonValue::Array(mut items) => {
            for item in &mut items {
                unescape_literal_keys(item);
            }
            items
        }
        other => vec![into_raw_value(other)],
    }
}

pub fn take_array_field(
    target_object: &mut JsonMap<String, JsonValue>,
    key: &str,
) -> Vec<JsonValue> {
    match target_object.remove(key) {
        Some(JsonValue::Array(items)) => items,
        _ => Vec::new(),
    }
}

/// Recursive map merge that treats every key literally (no modifier parsing).
pub fn merge_raw_map(target: &mut JsonValue, patch: JsonValue) {
    let patch_object = match patch {
        JsonValue::Object(object) => object,
        other => {
            *target = into_raw_value(other);
            return;
        }
    };

    if !target.is_object() {
        *target = JsonValue::Object(JsonMap::new());
    }

    let target_object = target.as_object_mut().expect("raw map target");
    for (key, value) in patch_object {
        let key = unescape_literal_key(key);
        let value_is_object = value.is_object();
        match (target_object.get_mut(&key), value_is_object) {
            (Some(existing), true) if existing.is_object() => merge_raw_map(existing, value),
            _ => {
                target_object.insert(key, into_raw_value(value));
            }
        }
    }
}

/// Keeps the last item per `name`, at that last item's position. Items without a usable `name`
/// are always kept.
///
/// Two linear passes over borrowed names: no per-item `String` is allocated, which matters for
/// proxy lists with thousands of entries.
pub fn dedup_named_items(mut items: Vec<JsonValue>) -> Vec<JsonValue> {
    if items.len() < 2 {
        return items;
    }

    let mut keep = vec![true; items.len()];
    {
        let mut seen = HashSet::<&str>::with_capacity(items.len());
        for (index, item) in items.iter().enumerate().rev() {
            if let Some(name) = item_name(item)
                && !seen.insert(name)
            {
                keep[index] = false;
            }
        }
    }

    let mut index = 0;
    items.retain(|_| {
        let retain = keep[index];
        index += 1;
        retain
    });
    items
}

fn item_name(value: &JsonValue) -> Option<&str> {
    value
        .as_object()
        .and_then(|map| map.get("name"))
        .and_then(JsonValue::as_str)
        .map(str::trim)
        .filter(|name| !name.is_empty())
}

pub fn ensure_object_field<'a>(
    object: &'a mut JsonMap<String, JsonValue>,
    key: &str,
) -> &'a mut JsonMap<String, JsonValue> {
    if !object.get(key).map(JsonValue::is_object).unwrap_or(false) {
        object.insert(key.to_string(), JsonValue::Object(JsonMap::new()));
    }
    object
        .get_mut(key)
        .and_then(JsonValue::as_object_mut)
        .expect("object field")
}

pub fn ensure_array_field<'a>(
    object: &'a mut JsonMap<String, JsonValue>,
    key: &str,
) -> &'a mut Vec<JsonValue> {
    if !object.get(key).map(JsonValue::is_array).unwrap_or(false) {
        object.insert(key.to_string(), JsonValue::Array(Vec::new()));
    }
    object
        .get_mut(key)
        .and_then(JsonValue::as_array_mut)
        .expect("array field")
}

pub fn has_non_empty_string(value: Option<&JsonValue>) -> bool {
    value
        .and_then(JsonValue::as_str)
        .map(|value| !value.trim().is_empty())
        .unwrap_or(false)
}

#[cfg(test)]
mod tests {
    use super::*;
    use serde_json::json;

    #[test]
    fn dedup_named_items_keeps_last_occurrence_in_place() {
        let items = vec![
            json!({ "name": "A", "tag": 1 }),
            json!({ "name": "B", "tag": 2 }),
            json!("plain"),
            json!({ "name": "A", "tag": 3 }),
            json!({ "name": "  ", "tag": 4 }),
        ];
        let deduped = dedup_named_items(items);
        assert_eq!(
            deduped,
            vec![
                json!({ "name": "B", "tag": 2 }),
                json!("plain"),
                json!({ "name": "A", "tag": 3 }),
                json!({ "name": "  ", "tag": 4 }),
            ]
        );
    }

    #[test]
    fn unescape_literal_keys_rewrites_nested_keys_only_when_needed() {
        let mut value = json!({ "<rules+>": [ { "<a>": 1, "b": 2 } ], "keep": "<value>" });
        unescape_literal_keys(&mut value);
        assert_eq!(
            value,
            json!({ "rules+": [ { "a": 1, "b": 2 } ], "keep": "<value>" })
        );
    }
}
