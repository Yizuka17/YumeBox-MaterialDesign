//! Minimal blocking HTTP/1.1 client behind the JS `fetch()` helper.
//!
//! Deliberately tiny: overrides only ever pull a small config fragment from a local or LAN
//! endpoint, and the crate carries no HTTP dependency. `https://` is not supported.

use std::collections::BTreeMap;
use std::io::{Read, Write};
use std::net::{TcpStream, ToSocketAddrs};
use std::time::Duration;

use serde::{Deserialize, Serialize};

const FETCH_IO_TIMEOUT: Duration = Duration::from_secs(15);

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct FetchRequest {
    url: String,
    #[serde(default = "default_fetch_method")]
    method: String,
    #[serde(default)]
    headers: BTreeMap<String, String>,
    #[serde(default)]
    body: Option<String>,
}

#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct FetchResponsePayload {
    ok: bool,
    status: u16,
    status_text: String,
    url: String,
    headers: BTreeMap<String, String>,
    body: String,
}

fn default_fetch_method() -> String {
    "GET".to_string()
}

pub fn execute_fetch(request: FetchRequest) -> Result<FetchResponsePayload, String> {
    if request.url.trim().is_empty() {
        return Err("fetch request url is empty".to_string());
    }
    let request_url = parse_fetch_url(&request.url)?;
    if request_url.scheme != "http" {
        return Err(format!(
            "fetch only supports http:// urls in the embedded override engine: {}",
            request.url
        ));
    }

    let method = request.method.trim().to_ascii_uppercase();
    let body = request.body.unwrap_or_default();
    let mut stream = connect_fetch(&request_url.host, request_url.port)
        .map_err(|err| format!("fetch {method} {} failed: {err}", request.url))?;
    let mut request_headers = request.headers;
    request_headers
        .entry("host".to_string())
        .or_insert_with(|| request_url.authority.clone());
    request_headers
        .entry("connection".to_string())
        .or_insert_with(|| "close".to_string());
    if !body.is_empty() {
        request_headers
            .entry("content-length".to_string())
            .or_insert_with(|| body.len().to_string());
    }

    let mut raw_request = format!("{method} {} HTTP/1.1\r\n", request_url.path_and_query);
    for (header_name, header_value) in &request_headers {
        raw_request.push_str(header_name);
        raw_request.push_str(": ");
        raw_request.push_str(header_value);
        raw_request.push_str("\r\n");
    }
    raw_request.push_str("\r\n");
    raw_request.push_str(&body);

    stream
        .write_all(raw_request.as_bytes())
        .map_err(|err| format!("send fetch request failed: {err}"))?;
    let mut response_bytes = Vec::new();
    stream
        .read_to_end(&mut response_bytes)
        .map_err(|err| format!("read fetch response failed: {err}"))?;
    let response = parse_http_response(&response_bytes)?;

    Ok(FetchResponsePayload {
        ok: (200..=299).contains(&response.status),
        status: response.status,
        status_text: response.status_text,
        url: request.url,
        headers: response.headers,
        body: String::from_utf8_lossy(&response.body).into_owned(),
    })
}

fn connect_fetch(host: &str, port: u16) -> Result<TcpStream, String> {
    let addresses = (host, port)
        .to_socket_addrs()
        .map_err(|err| format!("resolve fetch host {host}: {err}"))?;
    let mut last_error = None;
    for address in addresses {
        match TcpStream::connect_timeout(&address, FETCH_IO_TIMEOUT) {
            Ok(stream) => {
                stream
                    .set_read_timeout(Some(FETCH_IO_TIMEOUT))
                    .map_err(|err| format!("set fetch read timeout: {err}"))?;
                stream
                    .set_write_timeout(Some(FETCH_IO_TIMEOUT))
                    .map_err(|err| format!("set fetch write timeout: {err}"))?;
                return Ok(stream);
            }
            Err(error) => last_error = Some(error),
        }
    }
    Err(last_error
        .map(|error| format!("connect fetch host {host}:{port}: {error}"))
        .unwrap_or_else(|| format!("fetch host {host}:{port} resolved to no addresses")))
}

struct ParsedFetchUrl {
    scheme: String,
    /// Verbatim `host[:port]`, used for the `Host` header (IPv6 keeps its brackets).
    authority: String,
    /// Bare host for socket resolution (IPv6 brackets stripped).
    host: String,
    port: u16,
    path_and_query: String,
}

fn parse_fetch_url(raw_url: &str) -> Result<ParsedFetchUrl, String> {
    let (scheme, rest) = raw_url
        .split_once("://")
        .ok_or_else(|| format!("invalid fetch url: {raw_url}"))?;
    let scheme = scheme.to_ascii_lowercase();
    let default_port = match scheme.as_str() {
        "http" => 80,
        "https" => 443,
        _ => return Err(format!("unsupported fetch scheme: {scheme}")),
    };
    let (authority, path_and_query) = match rest.find('/') {
        Some(index) => (&rest[..index], &rest[index..]),
        None => (rest, "/"),
    };
    if authority.is_empty() {
        return Err(format!("invalid fetch url authority: {raw_url}"));
    }

    let (host, port) = if authority.starts_with('[') {
        let end = authority
            .find(']')
            .ok_or_else(|| format!("invalid IPv6 fetch authority: {authority}"))?;
        let host = authority[1..end].to_string();
        let port = authority[end + 1..]
            .strip_prefix(':')
            .map(parse_fetch_port)
            .transpose()?
            .unwrap_or(default_port);
        (host, port)
    } else if let Some((host, port)) = authority.rsplit_once(':') {
        if host.contains(':') {
            (authority.to_string(), default_port)
        } else {
            (host.to_string(), parse_fetch_port(port)?)
        }
    } else {
        (authority.to_string(), default_port)
    };

    Ok(ParsedFetchUrl {
        scheme,
        authority: authority.to_string(),
        host,
        port,
        path_and_query: path_and_query.to_string(),
    })
}

fn parse_fetch_port(raw_port: &str) -> Result<u16, String> {
    raw_port
        .parse::<u16>()
        .map_err(|err| format!("invalid fetch port {raw_port}: {err}"))
}

struct HttpResponse {
    status: u16,
    status_text: String,
    headers: BTreeMap<String, String>,
    body: Vec<u8>,
}

fn parse_http_response(response_bytes: &[u8]) -> Result<HttpResponse, String> {
    let header_end = response_bytes
        .windows(4)
        .position(|window| window == b"\r\n\r\n")
        .ok_or_else(|| "invalid HTTP response: missing header terminator".to_string())?;
    let header_bytes = &response_bytes[..header_end];
    let body_bytes = &response_bytes[header_end + 4..];
    let header_text = String::from_utf8_lossy(header_bytes);
    let mut lines = header_text.lines();
    let status_line = lines
        .next()
        .ok_or_else(|| "invalid HTTP response: missing status line".to_string())?;
    let mut status_parts = status_line.splitn(3, ' ');
    let _ = status_parts.next();
    let status = status_parts
        .next()
        .ok_or_else(|| "invalid HTTP response: missing status code".to_string())?
        .parse::<u16>()
        .map_err(|err| format!("invalid HTTP status code: {err}"))?;
    let status_text = status_parts.next().unwrap_or_default().trim().to_string();
    let mut headers = BTreeMap::new();
    for line in lines {
        if let Some((name, value)) = line.split_once(':') {
            headers.insert(name.trim().to_ascii_lowercase(), value.trim().to_string());
        }
    }

    let body = if headers
        .get("transfer-encoding")
        .map(|value| value.eq_ignore_ascii_case("chunked"))
        .unwrap_or(false)
    {
        decode_chunked_body(body_bytes)?
    } else {
        body_bytes.to_vec()
    };

    Ok(HttpResponse {
        status,
        status_text,
        headers,
        body,
    })
}

fn decode_chunked_body(body_bytes: &[u8]) -> Result<Vec<u8>, String> {
    let mut decoded = Vec::with_capacity(body_bytes.len());
    let mut cursor = 0usize;
    while cursor < body_bytes.len() {
        let size_end = body_bytes[cursor..]
            .windows(2)
            .position(|window| window == b"\r\n")
            .map(|index| cursor + index)
            .ok_or_else(|| "invalid chunked response: missing chunk size terminator".to_string())?;
        let size_text = String::from_utf8_lossy(&body_bytes[cursor..size_end]);
        let chunk_size = usize::from_str_radix(size_text.trim(), 16)
            .map_err(|err| format!("invalid chunk size: {err}"))?;
        cursor = size_end + 2;
        if chunk_size == 0 {
            break;
        }
        let chunk_end = cursor + chunk_size;
        if chunk_end > body_bytes.len() {
            return Err("invalid chunked response: chunk exceeds body length".to_string());
        }
        decoded.extend_from_slice(&body_bytes[cursor..chunk_end]);
        cursor = chunk_end + 2;
    }
    Ok(decoded)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn parse_fetch_url_removes_ipv6_brackets_for_socket_resolution() {
        let parsed = parse_fetch_url("http://[::1]:8080/path").expect("parse IPv6 URL");
        assert_eq!(parsed.host, "::1");
        assert_eq!(parsed.authority, "[::1]:8080");
        assert_eq!(parsed.port, 8080);
    }

    #[test]
    fn parse_fetch_url_defaults_port_and_path() {
        let parsed = parse_fetch_url("HTTP://example.com").expect("parse url");
        assert_eq!(parsed.scheme, "http");
        assert_eq!(parsed.host, "example.com");
        assert_eq!(parsed.port, 80);
        assert_eq!(parsed.path_and_query, "/");

        let parsed = parse_fetch_url("https://example.com/a?b=c").expect("parse url");
        assert_eq!(parsed.port, 443);
        assert_eq!(parsed.path_and_query, "/a?b=c");
    }

    #[test]
    fn parse_fetch_url_rejects_malformed_input() {
        assert!(parse_fetch_url("example.com").is_err());
        assert!(parse_fetch_url("ftp://example.com").is_err());
        assert!(parse_fetch_url("http://").is_err());
        assert!(parse_fetch_url("http://example.com:70000/").is_err());
    }

    #[test]
    fn https_urls_are_rejected_by_the_embedded_client() {
        let request: FetchRequest =
            serde_json::from_str(r#"{"url":"https://example.com"}"#).expect("decode request");
        let error = execute_fetch(request).expect_err("https must be rejected");
        assert!(error.contains("only supports http://"));
    }

    #[test]
    fn parse_http_response_reads_status_headers_and_body() {
        let raw = b"HTTP/1.1 204 No Content\r\nContent-Type: text/plain\r\nX-Upper: V\r\n\r\nbody";
        let response = parse_http_response(raw).expect("parse response");
        assert_eq!(response.status, 204);
        assert_eq!(response.status_text, "No Content");
        assert_eq!(response.headers["content-type"], "text/plain");
        assert_eq!(response.headers["x-upper"], "V");
        assert_eq!(response.body, b"body");
    }

    #[test]
    fn parse_http_response_decodes_chunked_bodies() {
        let raw = b"HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n4\r\nWiki\r\n5\r\npedia\r\n0\r\n\r\n";
        let response = parse_http_response(raw).expect("parse response");
        assert_eq!(response.body, b"Wikipedia");
    }
}
