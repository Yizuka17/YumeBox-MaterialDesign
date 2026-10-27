//! Override key grammar.
//!
//! A key in an override document is a base config key plus an optional modifier:
//!
//! | key form    | modifier  | meaning                                  |
//! |-------------|-----------|------------------------------------------|
//! | `rules`     | replace   | replace the field                        |
//! | `rules-start` / `+rules` | start | prepend to the list          |
//! | `rules-end` / `rules+`   | end   | append to the list           |
//! | `dns-merge` | merge     | deep-merge into the map                  |
//! | `dns-force` | force     | write verbatim, skipping schema handling |
//! | `<rules+>`  | replace   | literal key, modifier characters escaped |

use std::collections::HashMap;

use serde_json::{Map as JsonMap, Value as JsonValue};

use crate::model::{ParsedKey, PatchModifier, PatchOperations};

/// Byte range of the base key inside the raw key, plus the modifier it carried.
struct KeySpan {
    start: usize,
    end: usize,
    modifier: PatchModifier,
}

const SUFFIX_MODIFIERS: [(&str, PatchModifier); 4] = [
    ("-start", PatchModifier::Start),
    ("-end", PatchModifier::End),
    ("-merge", PatchModifier::Merge),
    ("-force", PatchModifier::Force),
];

fn parse_modifier_span(key: &str) -> KeySpan {
    if let Some(inner) = literal_key_inner(key) {
        return KeySpan {
            start: 1,
            end: 1 + inner.len(),
            modifier: PatchModifier::Replace,
        };
    }

    // Explicit YAML suffixes take priority over the JS-compatible +/- forms.
    for (suffix, modifier) in SUFFIX_MODIFIERS {
        if let Some(base) = key.strip_suffix(suffix)
            && !base.is_empty()
        {
            return KeySpan {
                start: 0,
                end: base.len(),
                modifier,
            };
        }
    }

    // JS-style array modifiers: +key prepends, key+ appends.
    if let Some(base) = key.strip_prefix('+')
        && !base.is_empty()
    {
        return KeySpan {
            start: 1,
            end: key.len(),
            modifier: PatchModifier::Start,
        };
    }
    if let Some(base) = key.strip_suffix('+')
        && !base.is_empty()
    {
        return KeySpan {
            start: 0,
            end: base.len(),
            modifier: PatchModifier::End,
        };
    }

    KeySpan {
        start: 0,
        end: key.len(),
        modifier: PatchModifier::Replace,
    }
}

pub fn parse_modifier_key(key: &str) -> ParsedKey<'_> {
    let span = parse_modifier_span(key);
    ParsedKey {
        base: &key[span.start..span.end],
        modifier: span.modifier,
    }
}

/// Same as [`parse_modifier_key`], but reuses the key's own allocation for the base key.
fn split_modifier_key(mut key: String) -> (String, PatchModifier) {
    let span = parse_modifier_span(&key);
    key.truncate(span.end);
    if span.start > 0 {
        key.drain(..span.start);
    }
    (key, span.modifier)
}

/// Collapses `key`, `key-end`, `+key`, … into one [`PatchOperations`] per base key, keeping the
/// document's key order for the resulting groups.
///
/// Consumes the parsed override document: patch values are moved into the operations instead of
/// being deep-cloned.
pub fn group_patch_keys(map: JsonMap<String, JsonValue>) -> Vec<(String, PatchOperations)> {
    let mut grouped = Vec::<(String, PatchOperations)>::with_capacity(map.len());
    let mut positions = HashMap::<String, usize>::with_capacity(map.len());
    for (key, value) in map {
        let (base_key, modifier) = split_modifier_key(key);
        let index = match positions.get(&base_key) {
            Some(index) => *index,
            None => {
                let index = grouped.len();
                positions.insert(base_key.clone(), index);
                grouped.push((base_key, PatchOperations::default()));
                index
            }
        };
        let operations = &mut grouped[index].1;
        match modifier {
            PatchModifier::Replace => operations.replace = Some(value),
            PatchModifier::Start => operations.start.push(value),
            PatchModifier::End => operations.end.push(value),
            PatchModifier::Merge => operations.merge.push(value),
            PatchModifier::Force => operations.force = Some(value),
        }
    }
    grouped
}

pub fn literal_key_inner(key: &str) -> Option<&str> {
    key.strip_prefix('<')
        .and_then(|value| value.strip_suffix('>'))
}

/// Strips the angle brackets of an escaped literal key, reusing the allocation.
pub fn unescape_literal_key(mut key: String) -> String {
    // Angle-wrapped keys are rare; the byte checks avoid touching the allocation for normal keys.
    let bytes = key.as_bytes();
    if bytes.len() >= 2 && bytes[0] == b'<' && bytes[bytes.len() - 1] == b'>' {
        key.pop();
        key.remove(0);
    }
    key
}

/// Whether [`unescape_literal_key`] would rewrite this key.
pub fn is_escaped_literal_key(key: &str) -> bool {
    let bytes = key.as_bytes();
    bytes.len() >= 2 && bytes[0] == b'<' && bytes[bytes.len() - 1] == b'>'
}

#[cfg(test)]
mod tests {
    use super::*;

    fn parsed(key: &str) -> (&str, PatchModifier) {
        let parsed = parse_modifier_key(key);
        (parsed.base, parsed.modifier)
    }

    #[test]
    fn modifier_suffixes_and_prefixes_are_recognized() {
        assert_eq!(parsed("rules"), ("rules", PatchModifier::Replace));
        assert_eq!(parsed("rules-start"), ("rules", PatchModifier::Start));
        assert_eq!(parsed("rules-end"), ("rules", PatchModifier::End));
        assert_eq!(parsed("dns-merge"), ("dns", PatchModifier::Merge));
        assert_eq!(parsed("dns-force"), ("dns", PatchModifier::Force));
        assert_eq!(parsed("+rules"), ("rules", PatchModifier::Start));
        assert_eq!(parsed("rules+"), ("rules", PatchModifier::End));
        assert_eq!(parsed("<rules+>"), ("rules+", PatchModifier::Replace));
    }

    #[test]
    fn split_modifier_key_matches_borrowed_parser() {
        for key in [
            "rules",
            "rules-start",
            "rules-end",
            "dns-merge",
            "dns-force",
            "+rules",
            "rules+",
            "<rules+>",
            "<>",
            "+",
            "-end",
        ] {
            let borrowed = parse_modifier_key(key);
            let (base, modifier) = split_modifier_key(key.to_string());
            assert_eq!(base, borrowed.base, "base mismatch for {key}");
            assert_eq!(modifier, borrowed.modifier, "modifier mismatch for {key}");
        }
    }

    #[test]
    fn unescape_literal_key_only_strips_wrapped_keys() {
        assert_eq!(unescape_literal_key("<rules>".to_string()), "rules");
        assert_eq!(unescape_literal_key("<>".to_string()), "");
        assert_eq!(unescape_literal_key("<".to_string()), "<");
        assert_eq!(unescape_literal_key("rules".to_string()), "rules");
    }
}
