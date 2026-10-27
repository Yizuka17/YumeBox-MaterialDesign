//! Content fingerprints for compiled configs.

use sha2::{Digest, Sha256};

const HEX_DIGITS: &[u8; 16] = b"0123456789abcdef";

/// Lowercase hex, without the per-byte `format!` allocation.
pub fn hex_lower(bytes: &[u8]) -> String {
    let mut encoded = String::with_capacity(bytes.len() * 2);
    for byte in bytes {
        encoded.push(HEX_DIGITS[usize::from(byte >> 4)] as char);
        encoded.push(HEX_DIGITS[usize::from(byte & 0x0f)] as char);
    }
    encoded
}

pub fn fingerprint_for(profile_uuid: &[u8], payload: &[u8]) -> String {
    let mut hasher = Sha256::new();
    hasher.update(profile_uuid);
    hasher.update(payload);
    hex_lower(&hasher.finalize())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn hex_lower_matches_format_output() {
        let bytes = [0x00u8, 0x0f, 0x10, 0xa5, 0xff];
        let expected: String = bytes.iter().map(|byte| format!("{byte:02x}")).collect();
        assert_eq!(hex_lower(&bytes), expected);
    }

    #[test]
    fn fingerprint_is_stable_and_uuid_scoped() {
        let first = fingerprint_for(b"uuid-a", b"payload");
        assert_eq!(first, fingerprint_for(b"uuid-a", b"payload"));
        assert_ne!(first, fingerprint_for(b"uuid-b", b"payload"));
        assert_eq!(first.len(), 64);
    }
}
