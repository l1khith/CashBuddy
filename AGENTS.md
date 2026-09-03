# PaisaPal KMP + Rust — AI Agent & Developer Rules

This project follows the strict architecture and development rules specified in [rules.md](file:///c:/Users/ailik/AndroidStudioProjects/CashBuddy/rules.md).

## Critical Non-Negotiables:
1. **NO SMS PERMISSION**: Use `BIND_NOTIFICATION_LISTENER_SERVICE` exclusively.
2. **NO NETWORK PERMISSION**: 100% offline, local-only processing.
3. **RUST HYBRID CORE**: Rust for Parser, ML Classifier, Crypto. Kotlin for UI, ViewModels, DB.
4. **NO UNWRAP IN RUST**: Zero `unwrap()` / Zero `expect()` in production code. Result + ? everywhere.
5. **ENCRYPTED DATABASE**: SQLCipher (AES-256 GCM) with Rust Crypto + Android Keystore.
6. **HUMAN-IN-THE-LOOP**: Transactions with confidence < 0.85 or amount >= ₹10,000 must be marked PENDING.
7. **BIOMETRIC LOCK**: BIOMETRIC_STRONG on app launch and 5-min timeout.
8. **PACKAGE ALLOWLIST**: Only 50+ curated Indian banking and UPI apps are parsed.

Refer to:
- High-Level Design: [docs/ARCHITECTURE_HLD.md](file:///c:/Users/ailik/AndroidStudioProjects/CashBuddy/docs/ARCHITECTURE_HLD.md)
- Rust Core HLD: [docs/RUST_CORE_HLD.md](file:///c:/Users/ailik/AndroidStudioProjects/CashBuddy/docs/RUST_CORE_HLD.md)
- Rust Core LLD: [docs/RUST_CORE_LLD.md](file:///c:/Users/ailik/AndroidStudioProjects/CashBuddy/docs/RUST_CORE_LLD.md)
- Low-Level Database Design: [docs/DATABASE_LLD.md](file:///c:/Users/ailik/AndroidStudioProjects/CashBuddy/docs/DATABASE_LLD.md)
- Notification Parser Engine: [docs/NOTIFICATION_PARSER_LLD.md](file:///c:/Users/ailik/AndroidStudioProjects/CashBuddy/docs/NOTIFICATION_PARSER_LLD.md)
- Security Architecture: [docs/SECURITY_ARCHITECTURE.md](file:///c:/Users/ailik/AndroidStudioProjects/CashBuddy/docs/SECURITY_ARCHITECTURE.md)
- UI/UX Specification: [docs/UI_UX_SPECIFICATION.md](file:///c:/Users/ailik/AndroidStudioProjects/CashBuddy/docs/UI_UX_SPECIFICATION.md)
- Execution Plan: [docs/EXECUTION_PLAN.md](file:///c:/Users/ailik/AndroidStudioProjects/CashBuddy/docs/EXECUTION_PLAN.md)
- Skills Registry: [skills.md](file:///c:/Users/ailik/AndroidStudioProjects/CashBuddy/skills.md)
- Project Memory: [memory.md](file:///c:/Users/ailik/AndroidStudioProjects/CashBuddy/memory.md)
- Subagents Directory: [subagents.md](file:///c:/Users/ailik/AndroidStudioProjects/CashBuddy/subagents.md)
