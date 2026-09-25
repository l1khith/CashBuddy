# PaisaPal KMP — Subagents Directory & Protocols

## 1. Subagent Ecosystem Overview

To achieve production-grade quality, modularity, and error-free execution across implementation phases, PaisaPal utilizes 5 specialized autonomous subagent profiles:

```
                          ┌───────────────────────┐
                          │   Lead Orchestrator   │
                          └───────────┬───────────┘
                                      │
       ┌──────────────────┬───────────┼───────────┬──────────────────┐
       ▼                  ▼           ▼           ▼                  ▼
┌────────────┐     ┌───────────┐┌──────────────┐┌──────────────┐┌──────────────┐
│Architecture│     │Notification││ Database   ││    UI/UX     ││  QA & Test   │
│  & Domain  │     │  Parser   ││ & Security  ││   Compose    ││  Specialist  │
└────────────┘     └───────────┘└──────────────┘└──────────────┘└──────────────┘
```

---

## 2. Specialized Subagent Definitions

### 2.1 Architecture & Domain Specialist (`architecture_specialist`)
- **Primary Domain**: Clean Architecture boundaries, KMP gradle targets, Koin 4.0 DI setup, pure Kotlin domain models, repository interfaces, and use cases.
- **Key Skills**: [paisapal-architecture](file:///c:/Users/ailik/AndroidStudioProjects/CashBuddy/.skills/paisapal-architecture/SKILL.md).

### 2.2 Parser & Notification Engine Specialist (`parser_engine_specialist`)
- **Primary Domain**: Passive expense capture, Android's `NotificationListenerService`, pure Kotlin Probabilistic Gating (`com.cashbuddy.core.prob`), Naive Bayes classification, entity regex extraction, and auto-confirm threshold checks.
- **Key Skills**: [notification-parser-engine](file:///c:/Users/ailik/AndroidStudioProjects/CashBuddy/.skills/notification-parser-engine/SKILL.md), `android-intent-security`.

### 2.3 Database & Security Specialist (`database_security_specialist`)
- **Primary Domain**: SQLDelight 2.2.1 schema authoring (tables, triggers, indices), SQLCipher (AES-256 GCM) encryption integration, Android Keystore (TEE/StrongBox), BiometricPrompt (`BIOMETRIC_STRONG`), and `FLAG_SECURE`.
- **Key Skills**: [sqldelight-database](file:///c:/Users/ailik/AndroidStudioProjects/CashBuddy/.skills/sqldelight-database/SKILL.md), [android-security-keystore](file:///c:/Users/ailik/AndroidStudioProjects/CashBuddy/.skills/android-security-keystore/SKILL.md).

### 2.4 UI/UX & Compose Multiplatform Specialist (`ui_compose_specialist`)
- **Primary Domain**: Compose Multiplatform 1.11+ screens, Material3 theme and design tokens, Navigation 3 type-safe routes, MVI Reducers (`ReviewScreen`, `BudgetScreen`), MVVM StateFlow (`HomeScreen`, `StatsScreen`, `AccountsScreen`, `SettingsScreen`), adaptive layouts, and edge-to-edge insets.
- **Key Skills**: [compose-multiplatform-ui](file:///c:/Users/ailik/AndroidStudioProjects/CashBuddy/.skills/compose-multiplatform-ui/SKILL.md), `navigation-3`, `edge-to-edge`, `adaptive`.

### 2.5 Quality Assurance & Testing Specialist (`qa_test_specialist`)
- **Primary Domain**: KMP unit tests for shared core, notification parser benchmarking with real-world alert variations, probabilistic classification accuracy validation, MVI state transition testing, and Android manifest boundary auditing.
- **Key Skills**: [testing-setup](file:///c:/Users/ailik/.agents/skills/testing-setup/SKILL.md), [offline-privacy-policy](file:///c:/Users/ailik/AndroidStudioProjects/CashBuddy/.skills/offline-privacy-policy/SKILL.md).
