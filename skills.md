# PaisaPal KMP + Rust — Skills Registry & Usage Manual

This directory documents all specialized skills established for the **PaisaPal KMP + Rust** project. Agents and developers must read and utilize these skills before designing, writing, or refactoring code.

---

## 1. Project-Specific Skills (`.skills/` and `.agents/skills/`)

| Skill Name | Location | Description & Primary Use Case |
|---|---|---|
| **`rust-core-development`** | [SKILL.md](file:///c:/Users/ailik/AndroidStudioProjects/CashBuddy/.skills/rust-core-development/SKILL.md) | High-performance Rust development for `core-rust/`, UniFFI FFI bindings, zero-unwrap conventions, size-optimized compilation (`opt-level = "z"`), and ONNX Runtime (`ort`). |
| **`paisapal-architecture`** | [SKILL.md](file:///c:/Users/ailik/AndroidStudioProjects/CashBuddy/.skills/paisapal-architecture/SKILL.md) | Clean Architecture, Unidirectional Data Flow (UDF), pure Kotlin domain boundaries, MVI/MVVM patterns, and Koin 4.0 dependency injection setup with Rust UniFFI bindings. |
| **`sqldelight-database`** | [SKILL.md](file:///c:/Users/ailik/AndroidStudioProjects/CashBuddy/.skills/sqldelight-database/SKILL.md) | SQLDelight 2.2.1 schema authoring (7 tables, triggers, indices), column adapters (`Boolean`, `Long`), SQLCipher (AES-256 GCM) driver factory, and reactive queries. |
| **`notification-parser-engine`** | [SKILL.md](file:///c:/Users/ailik/AndroidStudioProjects/CashBuddy/.skills/notification-parser-engine/SKILL.md) | 5-layer notification intake pipeline: package allowlist validation (50+ apps), OTP/promo filtering, regex entity extraction, 7-factor confidence scoring, and fraud checks via Rust core. |
| **`android-security-keystore`** | [SKILL.md](file:///c:/Users/ailik/AndroidStudioProjects/CashBuddy/.skills/android-security-keystore/SKILL.md) | Hardware-backed key generation via Android Keystore (TEE/StrongBox), BiometricPrompt (`BIOMETRIC_STRONG`), root detection heuristics, and `FLAG_SECURE`. |
| **`compose-multiplatform-ui`** | [SKILL.md](file:///c:/Users/ailik/AndroidStudioProjects/CashBuddy/.skills/compose-multiplatform-ui/SKILL.md) | Compose Multiplatform 1.11+ UI implementation, Material3 theme & tokens, Navigation 3 type-safe backstack, MVI Reducers (Review Inbox), and MVVM StateFlow. |
| **`offline-privacy-policy`** | [SKILL.md](file:///c:/Users/ailik/AndroidStudioProjects/CashBuddy/.skills/offline-privacy-policy/SKILL.md) | Offline boundary enforcement (0 network, 0 SMS), Scoped Storage data export (CSV/Excel via MediaStore), encrypted local backups, and Google Play compliance. |

---

## 2. Skill Activation Matrix Across Execution Phases

- **Phase 1 (Rust Core Library)**:
  - Must consult: `rust-core-development`.
- **Phase 2 (KMP & UniFFI Integration)**:
  - Must consult: `paisapal-architecture`, `sqldelight-database`, `rust-core-development`.
- **Phase 3 (Notification Service & Engine)**:
  - Must consult: `notification-parser-engine`, `rust-core-development`, `android-intent-security`.
- **Phase 4 (UI Layer)**:
  - Must consult: `compose-multiplatform-ui`, `navigation-3`, `edge-to-edge`, `adaptive`.
- **Phase 5 (Security & Release Polish)**:
  - Must consult: `android-security-keystore`, `offline-privacy-policy`, `rust-core-development`.
