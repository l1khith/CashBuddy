use aes_gcm::aead::{Aead, KeyInit};
use aes_gcm::{Aes256Gcm, Nonce};
use crate::{CoreError, EncryptedData};
use rand::RngCore;

const NONCE_SIZE: usize = 12; // 96-bit nonce for GCM
const KEY_SIZE: usize = 32;   // 256-bit key

pub fn encrypt_aes_gcm(plaintext: &[u8], key: &[u8]) -> Result<EncryptedData, CoreError> {
    if key.len() != KEY_SIZE {
        return Err(CoreError::InvalidKey);
    }

    let cipher = Aes256Gcm::new_from_slice(key).map_err(|_| CoreError::InvalidKey)?;

    let mut nonce_bytes = [0u8; NONCE_SIZE];
    rand::rngs::OsRng.fill_bytes(&mut nonce_bytes);
    let nonce = Nonce::from_slice(&nonce_bytes);

    let ciphertext = cipher
        .encrypt(nonce, plaintext)
        .map_err(|_| CoreError::EncryptionFailed)?;

    Ok(EncryptedData {
        ciphertext,
        nonce: nonce_bytes.to_vec(),
    })
}

pub fn decrypt_aes_gcm(data: &EncryptedData, key: &[u8]) -> Result<Vec<u8>, CoreError> {
    if key.len() != KEY_SIZE {
        return Err(CoreError::InvalidKey);
    }
    if data.nonce.len() != NONCE_SIZE {
        return Err(CoreError::DecryptionFailed);
    }

    let cipher = Aes256Gcm::new_from_slice(key).map_err(|_| CoreError::InvalidKey)?;
    let nonce = Nonce::from_slice(&data.nonce);

    cipher
        .decrypt(nonce, data.ciphertext.as_ref())
        .map_err(|_| CoreError::DecryptionFailed)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_encrypt_decrypt_roundtrip() {
        let key = [42u8; 32];
        let message = b"Confidential financial transaction data";

        let encrypted = encrypt_aes_gcm(message, &key).unwrap();
        let decrypted = decrypt_aes_gcm(&encrypted, &key).unwrap();

        assert_eq!(decrypted, message);
    }

    #[test]
    fn test_decrypt_invalid_key_fails() {
        let key1 = [1u8; 32];
        let key2 = [2u8; 32];
        let message = b"Secret text";

        let encrypted = encrypt_aes_gcm(message, &key1).unwrap();
        let result = decrypt_aes_gcm(&encrypted, &key2);

        assert!(result.is_err());
    }
}
