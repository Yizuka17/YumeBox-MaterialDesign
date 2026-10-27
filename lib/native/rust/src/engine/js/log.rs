//! The `<override>.log` file written next to each JS override.
//!
//! The app reads this file to show the script's console output, so it is truncated at the start of
//! every run and appended to as the script executes.

use std::fs::{self, OpenOptions};
use std::io::Write;
use std::path::{Path, PathBuf};

use boa_engine::{Context, js_string};

pub fn override_log_path(override_path: &str) -> PathBuf {
    Path::new(override_path).with_extension("log")
}

pub fn reset_override_log(log_path: &Path) -> Result<(), String> {
    if let Some(parent) = log_path.parent() {
        fs::create_dir_all(parent).map_err(|err| err.to_string())?;
    }
    fs::write(log_path, "").map_err(|err| err.to_string())
}

pub fn append_override_log(log_path: &Path, level: &str, message: &str) -> Result<(), String> {
    let mut file = OpenOptions::new()
        .create(true)
        .append(true)
        .open(log_path)
        .map_err(|err| err.to_string())?;
    writeln!(file, "[{level}] {message}").map_err(|err| err.to_string())
}

/// Resolves the log path from the realm, so a native `console.*` call lands in the log of the
/// override currently running in the shared runtime.
pub fn append_override_log_from_context(
    context: &mut Context,
    level: &str,
    message: &str,
) -> Result<(), String> {
    let log_path = context
        .global_object()
        .clone()
        .get(js_string!("__overrideLogPath"), context)
        .map_err(|err| format!("read __overrideLogPath failed: {err}"))?
        .to_string(context)
        .map_err(|err| format!("stringify __overrideLogPath failed: {err}"))?
        .to_std_string_escaped();
    append_override_log(Path::new(&log_path), level, message)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn log_path_replaces_the_override_extension() {
        assert_eq!(
            override_log_path("/tmp/a/example.js"),
            Path::new("/tmp/a/example.log")
        );
        assert_eq!(override_log_path("example"), Path::new("example.log"));
    }
}
