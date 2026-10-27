//! `override <preview|compile>` reads a compile request on stdin and writes a result document on
//! stdout; `override helper <yaml-parse|yaml-stringify>` is a raw YAML/JSON converter.
//!
//! Exit codes: 0 success, 1 usage/helper failure, 2 preview failure, 3 compile failure.

use std::io::{self, Read, Write};

use crate::compiler::compile_request;
use crate::compiler::result::{compile_error_json, encode_compile_result};
use crate::engine::yaml::{parse_yaml_to_json_string, stringify_json_to_yaml_string};
use crate::model::{CliMode, CompileRequest};

pub fn run_cli() -> i32 {
    let mut args = std::env::args().skip(1);
    let mode = match args.next().as_deref() {
        Some("preview") => CliMode::Preview,
        Some("compile") => CliMode::Compile,
        Some("helper") => return run_helper(args.next().as_deref()),
        Some(other) => {
            let _ = writeln!(io::stderr(), "unknown command: {other}");
            let _ = writeln!(io::stderr(), "usage: YumeOverride <preview|compile>");
            return 1;
        }
        None => {
            let _ = writeln!(io::stderr(), "usage: YumeOverride <preview|compile>");
            return 1;
        }
    };

    let mut stdin = String::new();
    if let Err(err) = io::stdin().read_to_string(&mut stdin) {
        let payload = compile_error_json(format!("read stdin: {err}"));
        let _ = writeln!(io::stdout(), "{payload}");
        return 2;
    }

    let result = match serde_json::from_str::<CompileRequest>(&stdin) {
        Ok(request) => compile_request(request, mode == CliMode::Compile),
        Err(err) => Err(format!("decode override request: {err}")),
    };

    let success = result.is_ok();
    let payload = match result {
        Ok(result) => encode_compile_result(result),
        Err(err) => compile_error_json(err),
    };
    let _ = writeln!(io::stdout(), "{payload}");

    if !success && matches!(mode, CliMode::Compile) {
        3
    } else if !success {
        2
    } else {
        0
    }
}

fn run_helper(helper_name: Option<&str>) -> i32 {
    let mut stdin = String::new();
    if let Err(err) = io::stdin().read_to_string(&mut stdin) {
        let _ = writeln!(io::stderr(), "read helper stdin failed: {err}");
        return 1;
    }

    let result = match helper_name {
        Some("yaml-parse") => parse_yaml_to_json_string(&stdin),
        Some("yaml-stringify") => stringify_json_to_yaml_string(&stdin),
        Some(other) => {
            let _ = writeln!(io::stderr(), "unknown helper command: {other}");
            return 1;
        }
        None => {
            let _ = writeln!(
                io::stderr(),
                "usage: YumeOverride helper <yaml-parse|yaml-stringify>"
            );
            return 1;
        }
    };

    match result {
        Ok(payload) => {
            let _ = write!(io::stdout(), "{payload}");
            0
        }
        Err(err) => {
            let _ = writeln!(io::stderr(), "{err}");
            1
        }
    }
}
