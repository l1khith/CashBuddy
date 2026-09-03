# PaisaPal Core — Low-Level Design (LLD) for Rust Core Library

## 1. Crate Layout & Compilation Topology

```
core-rust/
├── Cargo.toml                # Size-optimized profiles and dependencies
├── uniffi.toml               # UniFFI package metadata
├── build.rs                  # Scaffolding build script
└── src/
    ├── paisapal.udl          # UniFFI Interface Definition Language file
    ├── lib.rs                # Crate root, type exports, UniFFI scaffolding hook
    ├── parser/
    │   ├── mod.rs
    │   ├── engine.rs         # 5-layer pipeline orchestrator
    │   ├── amount_extractor.rs   # Static OnceLock regex parser
    │   ├── type_detector.rs      # Debit/credit keyword matcher
    │   ├── merchant_extractor.rs # Entity extraction regexes
    │   ├── account_extractor.rs  # Last-4 account digit extraction
    │   └── fraud_detector.rs     # Velocity tracking and duplicate suppression
    ├── classifier/
    │   ├── mod.rs
    │   ├── model.rs          # ONNX runtime inference with ort
    │   ├── tokenizer.rs      # WordPiece tokenizer implementation
    │   └── categories.rs     # Category string parsing and label mapping
    ├── crypto/
    │   ├── mod.rs
    │   ├── cipher.rs         # AES-256-GCM authenticated encryption
    │   ├── kdf.rs            # Argon2id key derivation
    │   └── keystore.rs       # Zeroize-backed key wrapper
    └── security/
        ├── mod.rs
        └── validator.rs      # Root detection heuristics
```

---

## 2. Size & Performance Optimization Flags (`Cargo.toml`)

```toml
[profile.release]
opt-level = "z"           # Optimize aggressively for binary size
lto = true                # Link-Time Optimization across all crates
codegen-units = 1         # Maximum compiler optimization passes
strip = true              # Strip all symbol tables and debug symbols
panic = "abort"           # Eliminate stack unwinding tables for smaller binary
```

Expected Compiled Artifact Size:
- Android `libpaisapal_core.so`: $\approx 1.5 - 1.8 \text{ MB}$ per target ABI.
- iOS `libpaisapal_core.a`: $\approx 2.0 \text{ MB}$.

---

## 3. Strict Coding Conventions & Quality Rules

### 3.1 Zero-Panic Policy (No `unwrap()` / No `expect()`)
- In production code paths, **`unwrap()` and `expect()` are strictly banned**.
- Every fallible operation returns `Result<T, CoreError>` or `Option<T>`.
- Recoverable errors bubble up using the `?` operator.
- Regex compilation uses `std::sync::OnceLock` initialized safely.

### 3.2 Error Hierarchy (`thiserror`)
All errors crossing the UniFFI boundary map to `CoreError`:
```rust
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
```

### 3.3 Memory & Zeroization Discipline
- Cryptographic keys implement the `zeroize::Zeroize` and `Drop` traits, ensuring byte buffers are overwritten with zeros immediately upon deallocation.
- Avoid unnecessary `.clone()`: Function arguments borrow references (`&str`, `&[u8]`) wherever possible.

---

## 4. UniFFI Interface Definition (`paisapal.udl`)

The UniFFI bridge generates type-safe JNI and C bindings from `src/paisapal.udl`:
- Enums: `TransactionType`, `Category`, `CoreError`.
- Dictionaries: `ParsedTransaction`, `RawNotification`, `ClassificationResult`, `EncryptedData`.
- Interfaces: `NotificationParser`, `TransactionClassifier`, `CryptoManager`, `SecurityValidator`.

Build automation generates Kotlin bindings into `composeApp/src/commonMain/kotlin/com/paisapal/core/`.

---

## 5. Testing & Verification

Unit tests are embedded in each submodule:
- `cargo test --lib`: Executes all unit tests with full assertions.
- Test coverage covers amount extraction variants, OTP discards, AES-GCM round-trips, Argon2 key derivations, and tokenizer output shapes.
