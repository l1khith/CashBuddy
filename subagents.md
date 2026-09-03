# PaisaPal KMP + Rust — Subagents Directory & Protocols

## 1. Subagent Ecosystem Overview

To achieve production-grade quality, modularity, and error-free execution across the 5 implementation phases, PaisaPal utilizes 6 specialized autonomous subagent profiles:

```
                          ┌───────────────────────┐
                          │   Lead Orchestrator   │
                          └───────────┬───────────┘
                                      │
       ┌──────────────────┬───────────┼───────────┬──────────────────┬──────────────┐
       ▼                  ▼           ▼           ▼                  ▼              ▼
┌──────────────┐   ┌────────────┐┌───────────┐┌──────────────┐┌──────────────┐┌──────────────┐
│  Rust Core   │   │ Architecture││ Notification││ Database  ││    UI/UX     ││  QA & Test   │
│  Specialist  │   │   & Domain  ││   Parser   ││& Security ││   Compose    ││  Specialist  │
└──────────────┘   └─────────────┘└────────────┘└───────────┘└──────────────┘└──────────────┘
```

---

## 2. Specialized Subagent Definitions

### 2.1 Rust Core Specialist (`rust_core_specialist`)
- **Primary Domain**: High-performance Rust engineering inside `core-rust/`, UniFFI scaffolding and UDL interfaces, ONNX Runtime Mobile integration (`ort`), AES-256-GCM authenticated cipher with `zeroize`, and size-optimized compilation (`opt-level = "z"`).
- **Key Skills**: [rust-core-development](file:///c:/Users/ailik/AndroidStudioProjects/CashBuddy/.skills/rust-core-development/SKILL.md).
- **Execution Mandate**:
  - Zero `unwrap()` / Zero `expect()` in production code. All errors use `Result<T, CoreError>`.
  - Zero unnecessary `.clone()`; borrow string and byte slices wherever possible.
  - Zero JVM heap key leakage; use `zeroize` for memory sanitization.

### 2.2 Architecture & Domain Specialist (`architecture_specialist`)
- **Primary Domain**: Clean Architecture boundaries, KMP gradle targets, Koin 4.0 DI setup with UniFFI Rust bindings, pure Kotlin domain models, repository interfaces, and use cases.
- **Key Skills**: [paisapal-architecture](file:///c:/Users/ailik/AndroidStudioProjects/CashBuddy/.skills/paisapal-architecture/SKILL.md).

### 2.3 Parser & Notification Engine Specialist (`parser_engine_specialist`)
- **Primary Domain**: Passive expense capture, Android's `NotificationListenerService`, connecting to the Rust `NotificationParser` and `TransactionClassifier` via UniFFI, 50+ app allowlist validation, and auto-confirm threshold checks.
- **Key Skills**: [notification-parser-engine](file:///c:/Users/ailik/AndroidStudioProjects/CashBuddy/.skills/notification-parser-engine/SKILL.md), `android-intent-security`.

### 2.4 Database & Security Specialist (`database_security_specialist`)
- **Primary Domain**: SQLDelight 2.2.1 schema authoring (7 tables, triggers, indices), SQLCipher (AES-256 GCM) encryption integration using keys derived from Rust `CryptoManager`, Android Keystore (TEE/StrongBox), BiometricPrompt (`BIOMETRIC_STRONG`), and `FLAG_SECURE`.
- **Key Skills**: [sqldelight-database](file:///c:/Users/ailik/AndroidStudioProjects/CashBuddy/.skills/sqldelight-database/SKILL.md), [android-security-keystore](file:///c:/Users/ailik/AndroidStudioProjects/CashBuddy/.skills/android-security-keystore/SKILL.md).

### 2.5 UI/UX & Compose Multiplatform Specialist (`ui_compose_specialist`)
- **Primary Domain**: Compose Multiplatform 1.11+ screens, Material3 theme and design tokens, Navigation 3 type-safe routes, MVI Reducers (`ReviewScreen`, `BudgetScreen`), MVVM StateFlow (`HomeScreen`, `StatsScreen`, `AccountsScreen`, `SettingsScreen`), adaptive layouts, and edge-to-edge insets.
- **Key Skills**: [compose-multiplatform-ui](file:///c:/Users/ailik/AndroidStudioProjects/CashBuddy/.skills/compose-multiplatform-ui/SKILL.md), `navigation-3`, `edge-to-edge`, `adaptive`.

### 2.6 Quality Assurance & Testing Specialist (`qa_test_specialist`)
- **Primary Domain**: Cargo unit tests for `core-rust/`, notification parser benchmarking with 50+ real-world alert variations, DistilBERT classification accuracy validation, MVI state transition testing, and Android manifest boundary auditing.
- **Key Skills**: [testing-setup](file:///c:/Users/ailik/.agents/skills/testing-setup/SKILL.md), [offline-privacy-policy](file:///c:/Users/ailik/AndroidStudioProjects/CashBuddy/.skills/offline-privacy-policy/SKILL.md).
