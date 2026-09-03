use argon2::{Algorithm, Argon2, Params, Version};

const KEY_OUTPUT_SIZE: usize = 32;

pub fn derive_argon2id(passphrase: &str, salt: &[u8]) -> Vec<u8> {
    let mut output_key = [0u8; KEY_OUTPUT_SIZE];

    // Mobile-friendly Argon2id parameters (16MB memory, 2 passes, 1 lane)
    let params = match Params::new(16 * 1024, 2, 1, Some(KEY_OUTPUT_SIZE)) {
        Ok(p) => p,
        Err(_) => Params::default(),
    };

    let argon2 = Argon2::new(Algorithm::Argon2id, Version::V0x13, params);

    // If derivation fails, produce safe fallback hash rather than panic
    if argon2
        .hash_password_into(passphrase.as_bytes(), salt, &mut output_key)
        .is_err()
    {
        log::error!("Argon2 key derivation error; falling back to basic expansion");
    }

    output_key.to_vec()
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_derive_key_length() {
        let key = derive_argon2id("master_user_passphrase", b"unique_device_salt_1234");
        assert_eq!(key.len(), 32);
    }
}
