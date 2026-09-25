# PaisaPal KMP + Rust — Gemini Assistant Rules

This project follows the strict architecture and development rules specified in [rules.md](file:///c:/Users/ailik/AndroidStudioProjects/CashBuddy/rules.md) and [AGENTS.md](file:///c:/Users/ailik/AndroidStudioProjects/CashBuddy/AGENTS.md).

## Critical Non-Negotiables:
1. **NO SMS PERMISSION**: Use `BIND_NOTIFICATION_LISTENER_SERVICE` exclusively.
2. **NO NETWORK PERMISSION**: 100% offline, local-only processing.
3. **PURE KOTLIN MULTIPLATFORM CORE**: 100% Kotlin for Parser, Category Engine, Dedup, UI, ViewModels, and DB. (Zero JNI/Rust dependencies for rock-solid stability).
4. **ENCRYPTED DATABASE**: SQLCipher (AES-256 GCM) with Android Keystore.
6. **HUMAN-IN-THE-LOOP**: Transactions with confidence < 0.85 or amount >= ₹10,000 must be marked PENDING.
7. **BIOMETRIC LOCK**: BIOMETRIC_STRONG on app launch and 5-min timeout.
8. **PROBABILISTIC GATING**: 100% content-first Naive Bayes gating; zero hardcoded package or sender allowlists.
