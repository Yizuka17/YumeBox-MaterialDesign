//! Forces `proxies[].reality-opts.short-id` to parse as a string.
//!
//! A short-id such as `0123` or `1e5` is a hex string, but YAML would resolve it to a number and
//! mihomo then rejects the proxy. The scanner walks the document line by line and inserts an
//! explicit `!!str` tag on exactly those values.

use std::borrow::Cow;

/// Tracks how deep in `proxies: - …: reality-opts:` the scanner currently is.
#[derive(Default)]
struct ScanState {
    in_proxies: bool,
    proxies_indent: isize,
    in_item: bool,
    item_indent: isize,
    in_reality_opts: bool,
    reality_opts_indent: isize,
}

pub fn add_yaml_tags_to_proxies_short_id(
    yaml_content: &str,
    include_nested_proxies: bool,
) -> Cow<'_, str> {
    if !yaml_content.contains("proxies:") || !yaml_content.contains("short-id:") {
        return Cow::Borrowed(yaml_content);
    }

    let mut result = String::with_capacity(yaml_content.len() + 64);
    let mut state = ScanState::default();
    for (index, line) in yaml_content.lines().enumerate() {
        if index > 0 {
            result.push('\n');
        }

        let trimmed = line.trim();
        if trimmed.is_empty() || trimmed.starts_with('#') {
            result.push_str(line);
            continue;
        }

        let indent = (line.len() - line.trim_start().len()) as isize;
        if trimmed.starts_with("proxies:") && (include_nested_proxies || indent == 0) {
            state = ScanState {
                in_proxies: true,
                proxies_indent: indent,
                ..ScanState::default()
            };
        } else if state.in_proxies && indent <= state.proxies_indent && !trimmed.starts_with('-') {
            state = ScanState::default();
        }

        if !state.in_proxies {
            result.push_str(line);
            continue;
        }

        if trimmed.starts_with('-') {
            state.in_item = true;
            state.item_indent = indent;
            state.in_reality_opts = false;
        } else if state.in_item && indent <= state.item_indent {
            state.in_item = false;
            state.in_reality_opts = false;
        }

        if state.in_item {
            if trimmed.starts_with("reality-opts:") {
                state.in_reality_opts = true;
                state.reality_opts_indent = indent;
            } else if state.in_reality_opts && indent <= state.reality_opts_indent {
                state.in_reality_opts = false;
            }

            if state.in_reality_opts && trimmed.starts_with("short-id:") {
                match tag_short_id_line(line) {
                    Some(tagged) => result.push_str(&tagged),
                    None => result.push_str(line),
                }
                continue;
            }
        }

        result.push_str(line);
    }

    Cow::Owned(result)
}

/// Returns the line with `!!str` inserted, or `None` when the value must be left alone
/// (already tagged, null, or a flow collection).
fn tag_short_id_line(line: &str) -> Option<String> {
    const KEY: &str = "short-id:";
    let key_end = line.find(KEY)? + KEY.len();
    let prefix = &line[..key_end];
    let after_prefix = &line[key_end..];
    let trimmed_after_prefix = after_prefix.trim_start();
    let leading_whitespace = &after_prefix[..after_prefix.len() - trimmed_after_prefix.len()];

    let value_end = trimmed_after_prefix
        .char_indices()
        .find(|(_, character)| character.is_whitespace() || *character == '#')
        .map_or(trimmed_after_prefix.len(), |(index, _)| index);
    let value = &trimmed_after_prefix[..value_end];
    let suffix = &trimmed_after_prefix[value_end..];

    if value.eq_ignore_ascii_case("null")
        || value == "~"
        || value.starts_with("!!")
        || value.starts_with('{')
        || value.starts_with('[')
    {
        return None;
    }
    Some(format!("{prefix}{leading_whitespace}!!str {value}{suffix}"))
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn untouched_documents_are_borrowed() {
        let content = "mode: rule\nproxies:\n  - name: a\n";
        assert!(matches!(
            add_yaml_tags_to_proxies_short_id(content, false),
            Cow::Borrowed(_)
        ));
    }

    #[test]
    fn short_id_inside_reality_opts_is_tagged() {
        let content = "proxies:\n  - name: example\n    reality-opts:\n      short-id: abc123\n      other: 1\n";
        let tagged = add_yaml_tags_to_proxies_short_id(content, false);
        assert!(tagged.contains("short-id: !!str abc123"));
        assert!(tagged.contains("other: 1"));
    }

    #[test]
    fn already_tagged_null_and_flow_values_are_left_alone() {
        for value in ["!!str abc", "null", "~", "{a: b}", "[1]"] {
            let content =
                format!("proxies:\n  - name: a\n    reality-opts:\n      short-id: {value}\n");
            let tagged = add_yaml_tags_to_proxies_short_id(&content, false);
            assert_eq!(tagged, content.trim_end(), "value {value} must be kept");
        }
    }

    #[test]
    fn short_id_outside_reality_opts_is_left_alone() {
        let content = "proxies:\n  - name: a\n    short-id: 0123\nother:\n  short-id: 0123\n";
        let tagged = add_yaml_tags_to_proxies_short_id(content, false);
        assert!(!tagged.contains("!!str"));
    }

    #[test]
    fn trailing_comments_are_preserved() {
        let content = "proxies:\n  - name: a\n    reality-opts:\n      short-id: abc # keep me\n";
        let tagged = add_yaml_tags_to_proxies_short_id(content, false);
        assert!(tagged.contains("short-id: !!str abc # keep me"));
    }
}
