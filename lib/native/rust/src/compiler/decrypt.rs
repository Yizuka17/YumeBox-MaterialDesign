//! age-encrypted profile sources.

pub fn is_age_encrypted(bytes: &[u8]) -> bool {
    bytes.starts_with(b"age-encryption.org/v1")
        || bytes.starts_with(b"-----BEGIN AGE ENCRYPTED FILE-----")
}

pub fn decrypt_age_source(ciphertext: &[u8], secret_key: Option<&str>) -> Result<Vec<u8>, String> {
    let secret_key = secret_key
        .map(str::trim)
        .filter(|value| !value.is_empty())
        .ok_or_else(|| "age encrypted profile requires ageSecretKey".to_string())?;
    let identities = parse_age_identities(secret_key)?;
    let mut last_error = String::new();
    for identity in identities {
        match age::decrypt(&identity, ciphertext) {
            Ok(plaintext) => return Ok(plaintext),
            Err(err) => last_error = err.to_string(),
        }
    }
    if last_error.is_empty() {
        last_error = "no matching age identity".to_string();
    }
    Err(format!("decrypt age profile: {last_error}"))
}

fn parse_age_identities(secret_keys: &str) -> Result<Vec<age::x25519::Identity>, String> {
    let mut identities = Vec::new();
    for (index, raw_line) in secret_keys.lines().enumerate() {
        let line = raw_line.trim();
        if line.is_empty() || line.starts_with('#') {
            continue;
        }
        if line.starts_with("AGE-SECRET-KEY-PQ-1") {
            return Err(
                "hybrid age secret keys are not supported by the Rust override decryptor yet"
                    .to_string(),
            );
        }
        let identity = line
            .parse::<age::x25519::Identity>()
            .map_err(|err| format!("parse age secret key at line {}: {err}", index + 1))?;
        identities.push(identity);
    }
    if identities.is_empty() {
        return Err("no supported age secret keys found".to_string());
    }
    Ok(identities)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn age_headers_are_detected() {
        assert!(is_age_encrypted(b"age-encryption.org/v1\n..."));
        assert!(is_age_encrypted(b"-----BEGIN AGE ENCRYPTED FILE-----\n"));
        assert!(!is_age_encrypted(b"mode: rule\n"));
    }

    #[test]
    fn missing_secret_key_is_reported() {
        let error =
            decrypt_age_source(b"age-encryption.org/v1", None).expect_err("missing key must fail");
        assert!(error.contains("requires ageSecretKey"));
    }

    #[test]
    fn hybrid_secret_keys_are_rejected() {
        let error = decrypt_age_source(b"payload", Some("AGE-SECRET-KEY-PQ-1EXAMPLE"))
            .expect_err("hybrid key must fail");
        assert!(error.contains("hybrid age secret keys"));
    }
}
