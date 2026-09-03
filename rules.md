# PaisaPal KMP + Rust — Development & Architectural Rules

## 1. Non-Negotiable Core Constraints

These 8 constraints are strictly enforced across the entire codebase:

1. **NO SMS PERMISSIONS**:
   - Never declare or request `android.permission.READ_SMS` or `android.permission.RECEIVE_SMS`.
   - All expense data extraction must originate solely from `android.permission.BIND_NOTIFICATION_LISTENER_SERVICE`.

2. **NO NETWORK PERMISSIONS (100% OFFLINE)**:
   - Never declare or request `android.permission.INTERNET`.
   - No external HTTP clients, telemetry SDKs, or cloud sync endpoints.
   - All processing and model inferences occur 100% on-device.

3. **HYBRID ARCHITECTURE: RUST FOR CORE, KOTLIN FOR APP**:
   - **Rust (`core-rust/`)**: Strictly used for Notification Parsing, DistilBERT ML Classification, Cryptography (AES-256-GCM, Argon2id), and Fraud Detection.
   - **Kotlin Multiplatform**: Strictly used for Presentation (Compose), ViewModels, Navigation 3, and Local Database (SQLDelight).
   - **Zero `unwrap()` / Zero `expect()` in Rust**: All Rust code must return `Result<T, CoreError>`. No panics in production.
   - **Memory Discipline**: Sensitive keys must be wiped with `zeroize`. Avoid unnecessary `.clone()`.

4. **HARDWARE-BACKED DATABASE ENCRYPTION**:
   - The SQLite database (`paisapal.db`) must always be encrypted with SQLCipher (AES-256 GCM) using keys derived via the Rust crypto core and Android Keystore (TEE / StrongBox).

5. **MANDATORY REVIEW FOR HIGH VALUE OR LOW CONFIDENCE**:
   - Any transaction with confidence $< 0.85$ OR amount $\ge ₹10,000$ (or user-defined threshold) must be stored with status `PENDING`.
   - Only transactions meeting both criteria ($\text{confidence} \ge 0.85$ AND $\text{amount} < ₹10,000$) may be marked `CONFIRMED` automatically.

6. **BIOMETRIC APP PROTECTION**:
   - Require `BiometricPrompt` with `BIOMETRIC_STRONG` on application cold launch.
   - Automatically lock the application after 5 minutes of background inactivity.

7. **STRICT PACKAGE ALLOWLIST**:
   - Only process notifications originating from the curated allowlist of 50+ verified Indian banking, UPI, and payment apps. Discard unknown package sources immediately at Layer 1.

8. **ZERO PRIVATE DATA LEAKAGE**:
   - Never log raw notification text, full account numbers, or OTP contents in production logs.
   - Use `FLAG_SECURE` on sensitive screens to prevent unauthorized screen captures.

---

## 2. Rust Coding & Tokio Guidelines

- **No `unwrap()` or `expect()`**: Always use `Result<T, CoreError>` with the `?` operator.
- **Tokio / Async**: Assume a runtime exists; never call `Runtime::block_on()` inside async; never hold locks across `.await`; use `spawn_blocking` for CPU/heavy tasks.
- **Editing Hygiene**: Make minimal, additive changes; do not refactor unrelated code; avoid unnecessary comments unless explaining non-obvious constraints.
