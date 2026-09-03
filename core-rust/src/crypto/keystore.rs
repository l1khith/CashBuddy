use rand::RngCore;
use zeroize::Zeroize;

pub struct SecureKey {
    bytes: [u8; 32],
}

impl SecureKey {
    pub fn generate() -> Self {
        let mut bytes = [0u8; 32];
        rand::rngs::OsRng.fill_bytes(&mut bytes);
        Self { bytes }
    }

    pub fn as_bytes(&self) -> &[u8] {
        &self.bytes
    }

    pub fn to_vec(&self) -> Vec<u8> {
        self.bytes.to_vec()
    }
}

impl Drop for SecureKey {
    fn drop(&mut self) {
        self.bytes.zeroize();
    }
}
