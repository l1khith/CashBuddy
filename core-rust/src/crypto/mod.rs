pub mod cipher;
pub mod kdf;
pub mod keystore;

use crate::{CoreError, EncryptedData};

pub struct CryptoManager;

impl CryptoManager {
    pub fn new() -> Self {
        Self
    }

    pub fn generate_key(&self) -> Vec<u8> {
        keystore::SecureKey::generate().to_vec()
    }

    pub fn encrypt(&self, plaintext: Vec<u8>, key: Vec<u8>) -> Result<EncryptedData, CoreError> {
        cipher::encrypt_aes_gcm(&plaintext, &key)
    }

    pub fn decrypt(&self, data: EncryptedData, key: Vec<u8>) -> Result<Vec<u8>, CoreError> {
        cipher::decrypt_aes_gcm(&data, &key)
    }

    pub fn derive_key(&self, passphrase: String, salt: Vec<u8>) -> Vec<u8> {
        kdf::derive_argon2id(&passphrase, &salt)
    }
}

impl Default for CryptoManager {
    fn default() -> Self {
        Self::new()
    }
}
