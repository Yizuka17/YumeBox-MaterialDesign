//! Provider path normalization.
//!
//! mihomo resolves relative provider paths against its runtime home, while profiles ship paths in
//! every imaginable shape (`./ruleset/x.yaml`, `providers/rules/x.mrs`, absolute paths from another
//! device). Everything is rewritten to a path under `<profile_dir>/providers/<prefix>/`, expressed
//! relative to the runtime home so the compiled config stays portable.

use std::path::{Component, Path, PathBuf};

pub fn normalize_provider_path(
    path: &str,
    profile_dir: &Path,
    prefix: &str,
    extension: &str,
) -> String {
    let raw = Path::new(path);
    let profile_base = profile_dir.join("providers").join(prefix);
    if raw.is_absolute() && raw.starts_with(&profile_base) {
        return relative_to_runtime_home(profile_dir, raw);
    }
    let cleaned = if raw.is_absolute() {
        raw.file_name().map(PathBuf::from).unwrap_or_default()
    } else {
        trim_provider_prefix(clean_relative_path(raw))
    };
    let trimmed = trim_provider_prefix(cleaned);
    let normalized = ensure_provider_extension(trimmed, extension);
    profile_provider_path(profile_dir, prefix, &normalized)
}

pub fn profile_provider_path(profile_dir: &Path, prefix: &str, relative: &Path) -> String {
    let tail = if relative.as_os_str().is_empty() {
        PathBuf::from("provider.yaml")
    } else {
        relative.to_path_buf()
    };
    let provider_path = profile_dir.join("providers").join(prefix).join(tail);
    relative_to_runtime_home(profile_dir, &provider_path)
}

pub fn relative_to_runtime_home(profile_dir: &Path, path: &Path) -> String {
    let runtime_home = runtime_home_dir(profile_dir);
    relative_path_from(&normalize_path(path), &normalize_path(runtime_home))
        .unwrap_or_else(|| path.to_path_buf())
        .to_string_lossy()
        .replace('\\', "/")
}

const CONTAINER_DIRS: [&str; 6] = [
    "providers",
    "provider",
    "clash",
    "ruleset",
    "rules",
    "proxies",
];

/// Drops leading container directories (`providers/`, `ruleset/`, …) so only the file tail is kept.
fn trim_provider_prefix(mut path: PathBuf) -> PathBuf {
    while let Some(rest) = strip_container_prefix(&path) {
        path = rest;
    }
    path
}

fn strip_container_prefix(path: &Path) -> Option<PathBuf> {
    let first = path.iter().next()?.to_string_lossy();
    if !CONTAINER_DIRS.contains(&first.as_ref()) {
        return None;
    }
    path.strip_prefix(Path::new(first.as_ref()))
        .ok()
        .map(Path::to_path_buf)
}

fn ensure_provider_extension(path: PathBuf, extension: &str) -> PathBuf {
    if path.as_os_str().is_empty() {
        return PathBuf::from(format!("provider.{extension}"));
    }
    if path.extension().is_some() {
        return path;
    }
    PathBuf::from(format!("{}.{}", path.to_string_lossy(), extension))
}

fn clean_relative_path(path: &Path) -> PathBuf {
    let mut cleaned = PathBuf::new();
    for component in path.components() {
        match component {
            Component::CurDir => continue,
            Component::ParentDir => {
                cleaned.pop();
            }
            Component::Normal(part) => cleaned.push(part),
            _ => {}
        }
    }
    cleaned
}

pub fn runtime_home_dir(profile_dir: &Path) -> PathBuf {
    profile_dir
        .parent()
        .and_then(Path::parent)
        .map(|files_dir| files_dir.join("mihomo"))
        .unwrap_or_else(|| profile_dir.to_path_buf())
}

fn relative_path_from(path: &Path, base: &Path) -> Option<PathBuf> {
    let path_components = path.components().collect::<Vec<_>>();
    let base_components = base.components().collect::<Vec<_>>();

    let mut common_count = 0;
    while common_count < path_components.len()
        && common_count < base_components.len()
        && path_components[common_count] == base_components[common_count]
    {
        common_count += 1;
    }

    if common_count == 0 {
        return None;
    }

    let mut result = PathBuf::new();
    for component in &base_components[common_count..] {
        if matches!(component, Component::Normal(_)) {
            result.push("..");
        }
    }
    for component in &path_components[common_count..] {
        result.push(component.as_os_str());
    }
    Some(result)
}

pub fn normalize_path(path: impl AsRef<Path>) -> PathBuf {
    let mut normalized = PathBuf::new();
    for component in path.as_ref().components() {
        match component {
            Component::CurDir => continue,
            Component::ParentDir => {
                normalized.pop();
            }
            Component::Normal(part) => normalized.push(part),
            Component::RootDir | Component::Prefix(_) => normalized.push(component.as_os_str()),
        }
    }
    normalized
}
