pub mod classifier;
pub mod crypto;
pub mod parser;
pub mod security;

pub use classifier::TransactionClassifier;
pub use crypto::CryptoManager;
pub use parser::NotificationParser;
pub use security::SecurityValidator;

#[derive(Debug, thiserror::Error)]
pub enum CoreError {
    #[error("Failed to load on-device ML model")]
    ModelLoadError,
    #[error("Inference execution failed")]
    InferenceError,
    #[error("Decryption failed: corrupted payload or key mismatch")]
    DecryptionFailed,
    #[error("Encryption failed")]
    EncryptionFailed,
    #[error("Invalid key: must be 32 bytes for AES-256")]
    InvalidKey,
    #[error("Notification parsing error")]
    ParseError,
    #[error("WordPiece tokenizer encoding error")]
    TokenizerError,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, serde::Serialize, serde::Deserialize)]
pub enum TransactionType {
    Debit,
    Credit,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, serde::Serialize, serde::Deserialize)]
pub enum Category {
    Food,
    Transport,
    Shopping,
    Bills,
    Entertainment,
    Health,
    Education,
    Housing,
    Insurance,
    Investments,
    Salary,
    Refund,
    Gift,
    Unknown,
}

#[derive(Debug, Clone)]
pub struct ParsedTransaction {
    pub amount: f64,
    pub transaction_type: TransactionType,
    pub category: Category,
    pub merchant: String,
    pub account_id: Option<String>,
    pub source_app: String,
    pub raw_text: String,
    pub confidence: f32,
    pub timestamp: i64,
}

#[derive(Debug, Clone)]
pub struct RawNotification {
    pub package_name: String,
    pub title: String,
    pub text: String,
    pub timestamp: i64,
}

#[derive(Debug, Clone)]
pub struct CategoryScore {
    pub category: Category,
    pub score: f32,
}

#[derive(Debug, Clone)]
pub struct ClassificationResult {
    pub category: Category,
    pub confidence: f32,
    pub all_scores: Vec<CategoryScore>,
}

#[derive(Debug, Clone)]
pub struct EncryptedData {
    pub ciphertext: Vec<u8>,
    pub nonce: Vec<u8>,
}

uniffi::include_scaffolding!("cashbuddy");
