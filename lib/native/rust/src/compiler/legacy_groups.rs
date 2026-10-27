//! Keep the fork's legacy relay migration when compiling old overrides with modern mihomo.
//! The source document is left intact; only the compiled runtime copy is sanitized.

use serde_json::Value;
use std::collections::BTreeSet;

pub fn sanitize(root: &mut Value) -> Vec<String> {
    let mut removed = BTreeSet::new();
    let Some(groups) = root.get("proxy-groups").and_then(Value::as_array) else { return Vec::new() };
    loop {
        let before = removed.len();
        for group in groups {
            let name = group.get("name").and_then(Value::as_str).unwrap_or("").trim();
            let relay = group.get("type").and_then(Value::as_str).is_some_and(|kind| kind.trim().eq_ignore_ascii_case("relay"));
            let providers = group.get("use").and_then(Value::as_array).is_some_and(|items| !items.is_empty())
                || ["include-all", "include-all-proxies", "include-all-providers"].iter().any(|key| group.get(key).and_then(Value::as_bool) == Some(true));
            let empty_after = !providers && group.get("proxies").and_then(Value::as_array).is_some_and(|items| {
                !items.is_empty() && items.iter().all(|item| item.as_str().is_some_and(|value| removed.contains(value)))
            });
            if (relay || empty_after) && !name.is_empty() { removed.insert(name.to_owned()); }
        }
        if removed.len() == before { break; }
    }
    if let Some(groups) = root.get_mut("proxy-groups").and_then(Value::as_array_mut) {
        groups.retain(|group| !group.get("name").and_then(Value::as_str).is_some_and(|name| removed.contains(name.trim()))
            && !group.get("type").and_then(Value::as_str).is_some_and(|kind| kind.trim().eq_ignore_ascii_case("relay")));
        for group in groups {
            if let Some(items) = group.get_mut("proxies").and_then(Value::as_array_mut) {
                items.retain(|item| !item.as_str().is_some_and(|name| removed.contains(name)));
                if items.is_empty() { if let Some(object) = group.as_object_mut() { object.remove("proxies"); } }
            }
        }
    }
    if let Some(rules) = root.get_mut("rules").and_then(Value::as_array_mut) {
        rules.retain(|rule| !rule.as_str().and_then(target).is_some_and(|name| removed.contains(name)));
    }
    removed.into_iter().map(|name| format!("Removed unsupported legacy relay group or dependent empty group: {name}")).collect()
}

fn target(rule: &str) -> Option<&str> {
    let mut depth = 0i32;
    let mut start = 0;
    let mut tokens = Vec::new();
    for (index, ch) in rule.char_indices() {
        match ch {
            '(' => depth += 1,
            ')' => depth = (depth - 1).max(0),
            ',' if depth == 0 => { tokens.push(rule[start..index].trim()); start = index + 1; }
            _ => {}
        }
    }
    tokens.push(rule[start..].trim());
    tokens.get(if tokens.first().is_some_and(|kind| kind.eq_ignore_ascii_case("MATCH")) { 1 } else { 2 }).copied()
}

#[cfg(test)]
mod tests {
    use super::*;
    use serde_json::json;
    #[test]
    fn removes_cascading_empty_groups_but_keeps_provider_groups_and_valid_rules() {
        let mut root = json!({"proxy-groups":[
            {"name":"Relay","type":"relay","proxies":["A","B"]},
            {"name":"Empty","type":"select","proxies":["Relay"]},
            {"name":"Provider","type":"select","proxies":["Relay"],"use":["subscription"]},
            {"name":"Valid","type":"select","proxies":["Relay","DIRECT"]}],
            "rules":["MATCH,Empty","AND,((NETWORK,TCP),(DST-PORT,443)),Relay","DOMAIN,example.com,Valid"]});
        assert_eq!(sanitize(&mut root).len(), 2);
        assert_eq!(root["proxy-groups"].as_array().unwrap().len(), 2);
        assert_eq!(root["proxy-groups"][1]["proxies"], json!(["DIRECT"]));
        assert_eq!(root["rules"], json!(["DOMAIN,example.com,Valid"]));
        assert!(sanitize(&mut root).is_empty());
    }
    #[test]
    fn preserves_current_valid_config() {
        let mut root = json!({"proxy-groups":[{"name":"Main","type":"select","proxies":["DIRECT"]}],"rules":["MATCH,Main"]});
        let original = root.clone();
        assert!(sanitize(&mut root).is_empty());
        assert_eq!(root, original);
    }
}
