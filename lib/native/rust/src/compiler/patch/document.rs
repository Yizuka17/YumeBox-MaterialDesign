//! Application of a user override document onto the config tree.
//!
//! The document is consumed: every patch value is moved into the target instead of being
//! deep-cloned, so applying a rules/proxies override costs one `Vec` move rather than a full
//! copy of the list.

use serde_json::{Map as JsonMap, Value as JsonValue};

use crate::compiler::patch::keys::group_patch_keys;
use crate::compiler::patch::values::{
    collect_array_items, dedup_named_items, into_raw_value, merge_raw_map, take_array_field,
};
use crate::compiler::schema::field_behavior;
use crate::model::{FieldBehavior, ListStyle, PatchOperations, SchemaId};

pub fn apply_override_document(target: &mut JsonValue, patch: JsonValue) {
    let patch_object = match patch {
        JsonValue::Object(object) => object,
        other => {
            *target = into_raw_value(other);
            return;
        }
    };
    if patch_object.is_empty() {
        return;
    }

    if !target.is_object() {
        *target = JsonValue::Object(JsonMap::new());
    }

    let target_object = target.as_object_mut().expect("root target object");
    apply_grouped_keys(target_object, patch_object, SchemaId::Root);
}

fn apply_nested_object_with_schema(target: &mut JsonValue, patch: JsonValue, schema: SchemaId) {
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
    let target_object = target.as_object_mut().expect("nested target object");
    apply_grouped_keys(target_object, patch_object, schema);
}

fn apply_grouped_keys(
    target_object: &mut JsonMap<String, JsonValue>,
    patch_object: JsonMap<String, JsonValue>,
    schema: SchemaId,
) {
    for (base_key, operations) in group_patch_keys(patch_object) {
        match field_behavior(schema, &base_key) {
            Some(behavior) => apply_field(target_object, &base_key, behavior, operations),
            None => apply_generic_field(target_object, &base_key, operations),
        }
    }
}

fn apply_field(
    target_object: &mut JsonMap<String, JsonValue>,
    base_key: &str,
    behavior: FieldBehavior,
    operations: PatchOperations,
) {
    if let Some(force) = operations.force {
        target_object.insert(base_key.to_string(), into_raw_value(force));
        return;
    }

    match behavior {
        FieldBehavior::Scalar => {
            if let Some(replace) = operations.replace {
                target_object.insert(base_key.to_string(), into_raw_value(replace));
            }
        }
        FieldBehavior::List(style) => apply_list_field(target_object, base_key, style, operations),
        FieldBehavior::Map => {
            if let Some(replace) = operations.replace {
                target_object.insert(base_key.to_string(), into_raw_value(replace));
            }
            for merge in operations.merge {
                let entry = target_object
                    .entry(base_key.to_string())
                    .or_insert_with(|| JsonValue::Object(JsonMap::new()));
                merge_raw_map(entry, merge);
            }
        }
        FieldBehavior::Object(schema) => {
            if let Some(replace) = operations.replace {
                let entry = target_object
                    .entry(base_key.to_string())
                    .or_insert_with(|| JsonValue::Object(JsonMap::new()));
                if replace.is_object() {
                    apply_nested_object_with_schema(entry, replace, schema);
                } else {
                    *entry = into_raw_value(replace);
                }
            }
            for merge in operations.merge {
                let entry = target_object
                    .entry(base_key.to_string())
                    .or_insert_with(|| JsonValue::Object(JsonMap::new()));
                merge_raw_map(entry, merge);
            }
        }
        FieldBehavior::Rules => apply_rules_field(target_object, base_key, operations),
    }
}

/// Fallback for keys outside the known schema: objects merge recursively, scalars replace, and
/// list modifiers still work.
fn apply_generic_field(
    target_object: &mut JsonMap<String, JsonValue>,
    base_key: &str,
    operations: PatchOperations,
) {
    if let Some(force) = operations.force {
        target_object.insert(base_key.to_string(), into_raw_value(force));
        return;
    }
    if let Some(replace) = operations.replace {
        if replace.is_object() {
            let entry = target_object
                .entry(base_key.to_string())
                .or_insert_with(|| JsonValue::Object(JsonMap::new()));
            apply_override_document(entry, replace);
        } else {
            target_object.insert(base_key.to_string(), into_raw_value(replace));
        }
    }
    for merge in operations.merge {
        let entry = target_object
            .entry(base_key.to_string())
            .or_insert_with(|| JsonValue::Object(JsonMap::new()));
        merge_raw_map(entry, merge);
    }
    if !operations.start.is_empty() || !operations.end.is_empty() {
        let mut items = Vec::<JsonValue>::new();
        append_items(&mut items, operations.start);
        items.append(&mut take_array_field(target_object, base_key));
        append_items(&mut items, operations.end);
        target_object.insert(base_key.to_string(), JsonValue::Array(items));
    }
}

fn apply_list_field(
    target_object: &mut JsonMap<String, JsonValue>,
    base_key: &str,
    style: ListStyle,
    operations: PatchOperations,
) {
    let mut items = Vec::new();
    append_items(&mut items, operations.start);
    match operations.replace {
        Some(replace) => items.append(&mut collect_array_items(replace)),
        None => items.append(&mut take_array_field(target_object, base_key)),
    }
    append_items(&mut items, operations.merge);
    append_items(&mut items, operations.end);
    if matches!(style, ListStyle::NamedObjects) {
        items = dedup_named_items(items);
    }
    target_object.insert(base_key.to_string(), JsonValue::Array(items));
}

/// `rules` behaves like a list, except a terminal `MATCH,…` rule always stays last: appended
/// rules from a later override must not end up after the catch-all.
fn apply_rules_field(
    target_object: &mut JsonMap<String, JsonValue>,
    base_key: &str,
    operations: PatchOperations,
) {
    let mut items = Vec::new();
    append_items(&mut items, operations.start);

    let mut base_items = match operations.replace {
        Some(replace) => collect_array_items(replace),
        None => take_array_field(target_object, base_key),
    };
    let terminal_rules = base_items
        .iter()
        .position(is_terminal_match_rule)
        .map_or_else(Vec::new, |index| base_items.split_off(index));
    items.append(&mut base_items);

    append_items(&mut items, operations.merge);
    append_items(&mut items, operations.end);
    items.extend(terminal_rules);
    target_object.insert(base_key.to_string(), JsonValue::Array(items));
}

fn append_items(items: &mut Vec<JsonValue>, sources: Vec<JsonValue>) {
    for source in sources {
        items.append(&mut collect_array_items(source));
    }
}

fn is_terminal_match_rule(value: &JsonValue) -> bool {
    value
        .as_str()
        .and_then(|rule| rule.split(',').next())
        .map(str::trim)
        .map(|kind| kind.eq_ignore_ascii_case("MATCH"))
        .unwrap_or(false)
}
