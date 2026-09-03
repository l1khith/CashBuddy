# PaisaPal KMP + Rust — Gemini Assistant Rules

This project follows the strict architecture and development rules specified in [rules.md](file:///c:/Users/ailik/AndroidStudioProjects/CashBuddy/rules.md) and [AGENTS.md](file:///c:/Users/ailik/AndroidStudioProjects/CashBuddy/AGENTS.md).

## Critical Non-Negotiables:
1. **NO SMS PERMISSION**: Use `BIND_NOTIFICATION_LISTENER_SERVICE` exclusively.
2. **NO NETWORK PERMISSION**: 100% offline, local-only processing.
3. **RUST HYBRID CORE**: Rust for Parser, ML Classifier, Crypto. Kotlin for UI, ViewModels, DB.
4. **NO UNWRAP IN RUST**: Zero `unwrap()` / Zero `expect()` in production code. Result + ? everywhere.
5. **ENCRYPTED DATABASE**: SQLCipher (AES-256 GCM) with Rust Crypto + Android Keystore.
6. **HUMAN-IN-THE-LOOP**: Transactions with confidence < 0.85 or amount >= ₹10,000 must be marked PENDING.
7. **BIOMETRIC LOCK**: BIOMETRIC_STRONG on app launch and 5-min timeout.
8. **PACKAGE ALLOWLIST**: Only 50+ curated Indian banking and UPI apps are parsed.
