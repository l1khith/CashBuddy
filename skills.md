# PaisaPal KMP — Skills Registry & Usage Manual

This directory documents all specialized skills established for the **PaisaPal KMP** project. Agents and developers must read and utilize these skills before designing, writing, or refactoring code.

---

## 1. Project-Specific Skills (`.skills/` and `.agents/skills/`)

| Skill Name | Location | Description & Primary Use Case |
|---|---|---|
| **`paisapal-architecture`** | [SKILL.md](file:///c:/Users/ailik/AndroidStudioProjects/CashBuddy/.skills/paisapal-architecture/SKILL.md) | Clean Architecture, Unidirectional Data Flow (UDF), pure Kotlin domain boundaries, MVI/MVVM patterns, and Koin 4.0 dependency injection setup. |
| **`sqldelight-database`** | [SKILL.md](file:///c:/Users/ailik/AndroidStudioProjects/CashBuddy/.skills/sqldelight-database/SKILL.md) | SQLDelight 2.2.1 schema authoring (7 tables, triggers, indices), column adapters (`Boolean`, `Long`), SQLCipher (AES-256 GCM) driver factory, and reactive queries. |
| **`notification-parser-engine`** | [SKILL.md](file:///c:/Users/ailik/AndroidStudioProjects/CashBuddy/.skills/notification-parser-engine/SKILL.md) | Probabilistic notification intake pipeline: content-first source detection, OTP/promo filtering, regex entity extraction, Naive Bayes confidence scoring, and fraud velocity checks. |
| **`android-security-keystore`** | [SKILL.md](file:///c:/Users/ailik/AndroidStudioProjects/CashBuddy/.skills/android-security-keystore/SKILL.md) | Hardware-backed key generation via Android Keystore (TEE/StrongBox), BiometricPrompt (`BIOMETRIC_STRONG`), root detection heuristics, and `FLAG_SECURE`. |
| **`compose-multiplatform-ui`** | [SKILL.md](file:///c:/Users/ailik/AndroidStudioProjects/CashBuddy/.skills/compose-multiplatform-ui/SKILL.md) | Compose Multiplatform 1.11+ UI implementation, Material3 theme & tokens, Navigation 3 type-safe backstack, MVI Reducers (Review Inbox), and MVVM StateFlow. |
| **`offline-privacy-policy`** | [SKILL.md](file:///c:/Users/ailik/AndroidStudioProjects/CashBuddy/.skills/offline-privacy-policy/SKILL.md) | Offline boundary enforcement (0 network, 0 SMS), Scoped Storage data export (CSV/Excel via MediaStore), encrypted local backups, and Google Play compliance. |
| **`macrobenchmark-baseline-profiles`** | [SKILL.md](file:///c:/Users/ailik/AndroidStudioProjects/CashBuddy/.skills/macrobenchmark-baseline-profiles/SKILL.md) | Jetpack Macrobenchmark (Cold/Warm/Hot starts, TTID, TTFD, jank metrics), Baseline Profile generation (`baseline-prof.txt`), memory tracking (`RSS + Swap`), and Perfetto startup tracing. |
| **`play-policy-insights`** | [SKILL.md](file:///c:/Users/ailik/AndroidStudioProjects/CashBuddy/.skills/play-policy-insights/SKILL.md) | Automated pre-submission audit for Google Play Policy: Data Safety declarations, permission hygiene (0 SMS, 0 Network), account deletion, Scoped Storage, and prominent disclosures. |

---

## 2. Skill Activation Matrix Across Execution Phases

- **Phase 1 (KMP Core & Database)**:
  - Must consult: `paisapal-architecture`, `sqldelight-database`.
- **Phase 2 (Notification Parser Engine)**:
  - Must consult: `notification-parser-engine`, `android-intent-security`.
- **Phase 3 (UI Layer)**:
  - Must consult: `compose-multiplatform-ui`, `navigation-3`, `edge-to-edge`, `adaptive`.
- **Phase 4 (Security & Privacy Compliance)**:
  - Must consult: `android-security-keystore`, `offline-privacy-policy`, `play-policy-insights`.
- **Phase 5 (Performance, Startup & Profiling)**:
  - Must consult: `macrobenchmark-baseline-profiles`, `android-profiler`, `r8-analyzer`, `testing-setup`.
