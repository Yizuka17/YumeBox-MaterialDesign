//! The JSON envelope exchanged with the app (JNI / C ABI / CLI).

use serde::{Deserialize, Serialize};

pub const REQUEST_SCHEMA_VERSION: u32 = 1;

#[derive(Debug, Deserialize, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct CompileRequest {
    #[serde(default = "default_request_schema_version")]
    pub schema_version: u32,
    pub profile_uuid: String,
    pub profile_dir: String,
    pub profile_path: String,
    #[serde(default)]
    pub overrides: Vec<OverrideSpec>,
    #[serde(default)]
    pub output_path: String,
    #[serde(default)]
    pub age_secret_key: Option<String>,
    #[serde(default)]
    pub run_mode: RunMode,
    #[serde(default)]
    pub skip_runtime_patches: bool,
    /// Internal, inspect-only launch role. It is intentionally separate from the user-visible
    /// run mode so preview cannot accidentally become a root/TUN selection.
    #[serde(default)]
    pub preview: bool,
}

/// The proxy run mode the app selected. eBPF and Root Tun keep the profile authoritative except for
/// eBPF's narrow Tun-conflict guard; VPN runtime patches protect the app-owned fd tunnel.
#[derive(Debug, Deserialize, Serialize, Clone, Copy, PartialEq, Eq, Default)]
#[serde(rename_all = "kebab-case")]
pub enum RunMode {
    #[default]
    Vpn,
    Tun,
    Ebpf,
}

#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct CompileResult {
    pub success: bool,
    pub fingerprint: String,
    pub final_yaml: String,
    pub warnings: Vec<String>,
    pub error: Option<String>,
}

#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct CompileRawResult {
    pub success: bool,
    pub fingerprint: String,
    pub config_raw: String,
    pub warnings: Vec<String>,
    pub error: Option<String>,
}

#[derive(Debug, Deserialize, Serialize, Clone)]
#[serde(rename_all = "camelCase")]
pub struct OverrideSpec {
    pub path: String,
    pub ext: String,
}

#[derive(Debug, Clone)]
pub struct LoadedOverride {
    pub path: String,
    pub ext: String,
    pub content: String,
}

#[derive(Debug, Clone, Copy, Eq, PartialEq)]
pub enum CliMode {
    Preview,
    Compile,
}

fn default_request_schema_version() -> u32 {
    REQUEST_SCHEMA_VERSION
}
