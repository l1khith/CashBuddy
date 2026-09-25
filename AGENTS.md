# PaisaPal KMP — AI Agent & Developer Rules

This project follows the strict architecture and development rules specified in [rules.md](file:///c:/Users/ailik/AndroidStudioProjects/CashBuddy/rules.md).

## Critical Non-Negotiables:
1. **NO SMS PERMISSION**: Use `BIND_NOTIFICATION_LISTENER_SERVICE` exclusively.
2. **NO NETWORK PERMISSION**: 100% offline, local-only processing.
3. **PURE KOTLIN MULTIPLATFORM CORE**: 100% Kotlin for Parser, Category Engine, Dedup, UI, ViewModels, and DB. (Zero JNI/Rust dependencies for rock-solid stability).
4. **ENCRYPTED DATABASE**: SQLCipher (AES-256 GCM) with Android Keystore.
6. **HUMAN-IN-THE-LOOP**: Transactions with confidence < 0.85 or amount >= ₹10,000 must be marked PENDING.
7. **BIOMETRIC LOCK**: BIOMETRIC_STRONG on app launch and 5-min timeout.
8. **PROBABILISTIC GATING**: 100% content-first Naive Bayes gating; zero hardcoded package or sender allowlists.

Refer to:
- High-Level Design: [docs/ARCHITECTURE_HLD.md](file:///c:/Users/ailik/AndroidStudioProjects/CashBuddy/docs/ARCHITECTURE_HLD.md)
- KMP Core HLD: [docs/KMP_CORE_HLD.md](file:///c:/Users/ailik/AndroidStudioProjects/CashBuddy/docs/KMP_CORE_HLD.md)
- KMP Core LLD: [docs/KMP_CORE_LLD.md](file:///c:/Users/ailik/AndroidStudioProjects/CashBuddy/docs/KMP_CORE_LLD.md)
- Low-Level Database Design: [docs/DATABASE_LLD.md](file:///c:/Users/ailik/AndroidStudioProjects/CashBuddy/docs/DATABASE_LLD.md)
- Notification Parser Engine: [docs/NOTIFICATION_PARSER_LLD.md](file:///c:/Users/ailik/AndroidStudioProjects/CashBuddy/docs/NOTIFICATION_PARSER_LLD.md)
- Security Architecture: [docs/SECURITY_ARCHITECTURE.md](file:///c:/Users/ailik/AndroidStudioProjects/CashBuddy/docs/SECURITY_ARCHITECTURE.md)
- UI/UX Specification: [docs/UI_UX_SPECIFICATION.md](file:///c:/Users/ailik/AndroidStudioProjects/CashBuddy/docs/UI_UX_SPECIFICATION.md)
- Execution Plan: [docs/EXECUTION_PLAN.md](file:///c:/Users/ailik/AndroidStudioProjects/CashBuddy/docs/EXECUTION_PLAN.md)
- Skills Registry: [skills.md](file:///c:/Users/ailik/AndroidStudioProjects/CashBuddy/skills.md)
- Project Memory: [memory.md](file:///c:/Users/ailik/AndroidStudioProjects/CashBuddy/memory.md)
- Subagents Directory: [subagents.md](file:///c:/Users/ailik/AndroidStudioProjects/CashBuddy/subagents.md)
