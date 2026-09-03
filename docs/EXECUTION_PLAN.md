# PaisaPal KMP + Rust — 5-Phase Execution Plan & Roadmap

## Phase 1: Rust Core Library (Engine, Model & Cryptography)

### Objectives
Build and verify the high-performance, size-optimized Rust core library in `core-rust/`, implementing the 5-layer notification parser, DistilBERT ONNX classifier, and AES-256-GCM / Argon2id cryptography.

### Tasks & Deliverables
1. **Rust Crate Setup (`core-rust/`)**:
   - Verify `Cargo.toml` with size optimization flags (`opt-level = "z"`, `lto = true`, `codegen-units = 1`, `strip = true`, `panic = "abort"`).
   - Configure `uniffi.toml` and `src/paisapal.udl`.
2. **Notification Parser Engine (`src/parser/`)**:
   - Implement `amount_extractor.rs`, `type_detector.rs`, `merchant_extractor.rs`, `account_extractor.rs`, `fraud_detector.rs`, `engine.rs`.
   - Implement allowlist of 50+ curated banking/payment packages.
   - Enforce zero `unwrap()` and zero `expect()` across all production code.
3. **DistilBERT ONNX Classifier (`src/classifier/`)**:
   - Implement WordPiece tokenizer and 14-category label mapping.
   - Integrate `ort` ONNX Runtime Mobile session wrapper.
4. **Cryptographic Core (`src/crypto/`)**:
   - Implement `cipher.rs` (AES-256-GCM) with `zeroize` memory sanitization on drop.
   - Implement `kdf.rs` (Argon2id key derivation).
5. **Security Validator (`src/security/`)**:
   - Implement native root detection heuristics.

### Phase 1 Acceptance Criteria
- `cargo test --lib` passes with 100% test success.
- Zero `unwrap()` or `expect()` in production paths.
- Compiled binary size $< 2.0 \text{ MB}$.

---

## Phase 2: KMP & UniFFI Integration

### Objectives
Integrate the compiled Rust static/cdylib into the Kotlin Multiplatform build pipeline using UniFFI bindings, and configure Koin 4.0 and SQLDelight schemas.

### Tasks & Deliverables
1. **Gradle UniFFI Configuration**:
   - Add `ch.ubique.uniffi` plugin and configure Android/iOS target ABIs.
   - Auto-generate Kotlin binding files in `composeApp/src/commonMain/kotlin/com/paisapal/core/`.
2. **SQLDelight Schema Implementation**:
   - Implement all 7 tables in `shared/src/commonMain/sqldelight/com/paisapal/db/`.
   - Implement SQLCipher database driver factory using keys derived from Rust `CryptoManager`.
3. **Koin 4.0 DI Modules**:
   - Register Rust singletons (`NotificationParser.create()`, `CryptoManager.create()`, `TransactionClassifier`).
   - Register domain repositories and use cases.

### Phase 2 Acceptance Criteria
- `./gradlew :composeApp:build` compiles Rust code and generates type-safe Kotlin bindings.
- Koin initializes without missing dependencies.
- SQLDelight generated queries execute successfully with encrypted driver.

---

## Phase 3: Notification Listener Service & Pipeline

### Objectives
Deploy Android's `NotificationListenerService` and connect it via UniFFI to the Rust parser and on-device ML classifier.

### Tasks & Deliverables
1. **Android Service Implementation**:
   - `TransactionNotificationListener.kt` with `BIND_NOTIFICATION_LISTENER_SERVICE`.
   - Forward raw notification payload to `NotificationParser.parse()`.
2. **Parser-to-Classifier Fallback**:
   - If parser confidence $< 0.70$ or category is `Unknown`, invoke `TransactionClassifier.classify()`.
3. **Status Logic & Persistence**:
   - Confidence $\ge 0.85$ AND amount $< autoConfirmThreshold$ $\to$ `CONFIRMED`.
   - Else $\to$ `PENDING` (Review Inbox).
   - Atomic balance updates via SQLDelight triggers.

### Phase 3 Acceptance Criteria
- 50+ real-world notification samples parse with $>90\%$ accuracy.
- OTP and promotional alerts discarded with 0 database writes.
- High-value alerts ($\ge ₹10,000$) unconditionally marked `PENDING`.

---

## Phase 4: UI Layer (Compose Multiplatform & MVI/MVVM)

### Objectives
Implement the complete declarative user interface with Material3 design tokens, type-safe Navigation 3, and MVI Review Inbox.

### Tasks & Deliverables
1. **Home Screen (MVVM)**: Balance header, monthly debit/credit delta, review banner, recent transactions.
2. **Review Inbox (MVI)**: Swipe-to-confirm, confidence badges, category quick-chip, bulk actions.
3. **Transactions, Stats, Accounts, Budgets, Goals, Settings**: Complete implementation of remaining screens.

### Phase 4 Acceptance Criteria
- Smooth 60fps animations on physical device.
- Review Inbox updates database reactively.

---

## Phase 5: Security Hardening & Play Store Release Polish

### Objectives
Harden hardware Keystore bindings, enforce BiometricPrompt, implement Scoped Storage export, and finalize Play Store compliance.

### Tasks & Deliverables
1. **Hardware Keystore & Biometrics**:
   - Secure passphrase vault with Android Keystore (TEE/StrongBox).
   - `BIOMETRIC_STRONG` prompt on cold launch and 5-minute background timeout.
2. **Data Portability**:
   - CSV and Excel export via MediaStore API.
   - Encrypted local backup and restore.
3. **Play Store Release**:
   - Zero network permission audit.
   - Closed testing rollout (20 testers $\times$ 14 days).

### Phase 5 Acceptance Criteria
- Total APK download size $< 25 \text{ MB}$.
- Runtime memory $< 100 \text{ MB}$.
- 100% offline data sovereignty confirmed.
