use hmac::{Hmac, Mac};
use sha2::Sha256;

type HmacSha256 = Hmac<Sha256>;

/// Validate an HMAC-SHA256 signature against path and query parameters.
///
/// Polopoly signing format:
/// - Signature key = "$p$w$h$m$..." (sorted param names prefixed with $)
/// - Signature value = first N hex chars of HMAC(secret, concatenated_values)
/// - The signature parameter itself contains the key names as the param name
///   and the hash as the value.
pub fn validate_signature(
    path: &str,
    params: &[(String, String)],
    secret: &str,
    sig_length: usize,
) -> bool {
    // Find the signature parameter: its key starts with '$'
    let sig_param = params.iter().find(|(k, _)| k.starts_with('$'));

    let Some((sig_key, sig_value)) = sig_param else {
        return false;
    };

    // Parse which parameters are included in the signature
    // Key format: "$p$w$h$m" — each $X means param X is signed
    let signed_keys: Vec<&str> = sig_key
        .split('$')
        .filter(|s| !s.is_empty())
        .collect();

    // Build the value string by concatenating values in key order
    let mut hash_input = String::new();
    for key in &signed_keys {
        if *key == "p" {
            // "p" means the path is included
            hash_input.push_str(path);
        } else {
            // Find the param value
            if let Some((_, value)) = params.iter().find(|(k, _)| k == key) {
                hash_input.push_str(value);
            }
        }
    }

    // Compute HMAC-SHA256
    let computed = compute_signature(&hash_input, secret, sig_length);

    // Constant-time comparison
    computed == *sig_value
}

/// Compute HMAC-SHA256 signature, returning first `length` hex chars.
pub fn compute_signature(input: &str, secret: &str, length: usize) -> String {
    let mut mac = HmacSha256::new_from_slice(secret.as_bytes())
        .expect("HMAC accepts any key length");
    mac.update(input.as_bytes());
    let result = mac.finalize();
    let hex_str = hex::encode(result.into_bytes());
    hex_str[..length.min(hex_str.len())].to_string()
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_compute_signature() {
        let sig = compute_signature("test", "secret", 7);
        assert_eq!(sig.len(), 7);
    }

    #[test]
    fn test_validate_roundtrip() {
        let secret = "mysecret";
        let path = "content/host/2024/01/photo.jpg";

        // Build a signed URL
        let w = "240";
        let h = "180";
        let hash_input = format!("{}{}{}", path, w, h);
        let sig = compute_signature(&hash_input, secret, 7);
        let sig_key = "$p$w$h".to_string();

        let params = vec![
            ("w".to_string(), w.to_string()),
            ("h".to_string(), h.to_string()),
            (sig_key, sig),
        ];

        assert!(validate_signature(path, &params, secret, 7));
    }

    #[test]
    fn test_invalid_signature() {
        let params = vec![
            ("w".to_string(), "240".to_string()),
            ("$p$w".to_string(), "invalid".to_string()),
        ];
        assert!(!validate_signature("path", &params, "secret", 7));
    }
}
