//! Base64 for the JS `b64d` / `b64e` / `Buffer` helpers.
//!
//! Hand-rolled on purpose: the crate ships no base64 dependency.

const TABLE: &[u8; 64] = b"ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";

pub fn base64_encode_string(data: &[u8]) -> String {
    let mut encoded = String::with_capacity(data.len().div_ceil(3) * 4);
    let mut index = 0;
    while index < data.len() {
        let first = data[index];
        let second = if index + 1 < data.len() {
            data[index + 1]
        } else {
            0
        };
        let third = if index + 2 < data.len() {
            data[index + 2]
        } else {
            0
        };
        let value = ((first as u32) << 16) | ((second as u32) << 8) | (third as u32);
        encoded.push(TABLE[((value >> 18) & 0x3F) as usize] as char);
        encoded.push(TABLE[((value >> 12) & 0x3F) as usize] as char);
        encoded.push(if index + 1 < data.len() {
            TABLE[((value >> 6) & 0x3F) as usize] as char
        } else {
            '='
        });
        encoded.push(if index + 2 < data.len() {
            TABLE[(value & 0x3F) as usize] as char
        } else {
            '='
        });
        index += 3;
    }
    encoded
}

pub fn base64_decode_string(content: &str) -> Result<String, String> {
    let mut bytes = Vec::with_capacity(content.len() / 4 * 3 + 3);
    let mut chunk = [0u8; 4];
    let mut chunk_len = 0usize;
    for byte in content.bytes().filter(|value| !value.is_ascii_whitespace()) {
        chunk[chunk_len] = byte;
        chunk_len += 1;
        if chunk_len == 4 {
            decode_base64_chunk(&chunk, &mut bytes)?;
            chunk_len = 0;
        }
    }
    if chunk_len != 0 {
        return Err("invalid base64 padding".to_string());
    }
    String::from_utf8(bytes).map_err(|err| err.to_string())
}

fn decode_base64_chunk(chunk: &[u8; 4], bytes: &mut Vec<u8>) -> Result<(), String> {
    let mut values = [0u8; 4];
    let mut padding = 0usize;
    for (index, item) in chunk.iter().enumerate() {
        values[index] = match item {
            b'A'..=b'Z' => item - b'A',
            b'a'..=b'z' => item - b'a' + 26,
            b'0'..=b'9' => item - b'0' + 52,
            b'+' => 62,
            b'/' => 63,
            b'=' => {
                padding += 1;
                0
            }
            _ => return Err(format!("invalid base64 character: {}", *item as char)),
        };
    }
    let combined = ((values[0] as u32) << 18)
        | ((values[1] as u32) << 12)
        | ((values[2] as u32) << 6)
        | values[3] as u32;
    bytes.push(((combined >> 16) & 0xFF) as u8);
    if padding < 2 {
        bytes.push(((combined >> 8) & 0xFF) as u8);
    }
    if padding < 1 {
        bytes.push((combined & 0xFF) as u8);
    }
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn round_trips_every_padding_length() {
        for text in ["", "a", "ab", "abc", "abcd", "hello world", "中文测试"] {
            let encoded = base64_encode_string(text.as_bytes());
            assert_eq!(
                base64_decode_string(&encoded).expect("decode"),
                text,
                "round trip failed for {text:?}"
            );
        }
    }

    #[test]
    fn known_vectors_match() {
        assert_eq!(base64_encode_string(b"f"), "Zg==");
        assert_eq!(base64_encode_string(b"fo"), "Zm8=");
        assert_eq!(base64_encode_string(b"foo"), "Zm9v");
        assert_eq!(base64_decode_string("Zm9vYmFy").expect("decode"), "foobar");
    }

    #[test]
    fn whitespace_is_ignored_and_bad_input_is_rejected() {
        assert_eq!(
            base64_decode_string("Zm9v\nYmFy").expect("decode"),
            "foobar"
        );
        assert!(base64_decode_string("Zm9").is_err());
        assert!(base64_decode_string("Zm9*").is_err());
    }
}
