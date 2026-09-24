# CashBuddy — Project Memory & Architectural Decisions (ADR)

## 1. Project Overview & Identity
- **Product Name**: CashBuddy — Intelligent Offline Finance Tracker
- **Architecture**: Clean Architecture + 100% Pure Kotlin Multiplatform Core
- **Platforms**: Android (API 26+) → iOS (Phase 2 via KMP)
- **Build System**: Gradle 9.x with Version Catalogs
- **Current Milestone**: **100% Pure Kotlin Multiplatform Core & 3-Layer Transaction Intake Established**.

---

## 2. Technology Stack Matrix

| Layer / Concern | Technology | Version | Rationale |
|---|---|---|---|
| **Language (App & Core)** | Kotlin Multiplatform | 2.4.x | Clean domain, UI, reactive state, and pure Kotlin core engines. Zero JNI/native crashes. |
| **OCR Text Recognition** | Google ML Kit (Bundled) | 16.0.1 | 100% offline text extraction from shared payment screenshots. |
| **UI Framework** | Compose Multiplatform | 1.11.x | Declarative, shared UI across Android and iOS targets with Material 3. |
| **Local Database** | SQLDelight | 2.0.2 | Compile-time SQL verification, reactive Flow queries, zero reflection. |
| **Data Encryption** | SQLCipher | 4.6.1 | AES-256 GCM page-level SQLite database encryption. |
| **Key Storage** | Android Keystore | Hardware TEE/StrongBox | Master key protected in hardware; in-memory passphrases zeroized immediately. |
| **Dependency Injection** | Koin | 4.0.0 | KMP-native, constructor-based, zero annotation processing overhead. |

---

## 3. Architectural Decision Records (ADRs)

### ADR-001: Notification Listener Instead of SMS Access
- **Decision**: CashBuddy exclusively implements `BIND_NOTIFICATION_LISTENER_SERVICE` and declares zero SMS permissions.

### ADR-002: Zero Network Permission (100% Offline Architecture)
- **Decision**: The `android.permission.INTERNET` permission is completely omitted from production manifests.

### ADR-003: Pure Kotlin for Presentation, Navigation, and Database
- **Decision**: Keep UI (Compose), ViewModels, Navigation, and SQLDelight in pure Kotlin.

### ADR-004: Dual UI Pattern (MVVM for Dashboards, MVI for Review Inbox)
- **Decision**: MVVM for standard screens, MVI with pure Reducer for ReviewScreen.

### ADR-005: Two-Tier Confidence & Value Confirmation Policy
- **Decision**: Auto-confirm only if `confidence >= 0.85` AND `amount < autoConfirmThreshold` (default ₹10,000). All other alerts are routed to the Review Inbox as `PENDING`.

### ADR-006: Hardware Keystore + StrongBox Protection
- **Decision**: Android Keystore keys generated in hardware (TEE/StrongBox) to encrypt application passphrases. In-memory arrays are zeroized with `Arrays.fill(0)` after initialization.

### ADR-007: 3-Layer Capture Pipeline
- **Decision**: Implement three complementary intake vectors: (1) NotificationListenerService, (2) Offline Screenshot Share with ML Kit, (3) Smart Manual Add with frequent merchant chips.

### ADR-008: Pure Kotlin Multiplatform Core (Deprecating Rust/UniFFI/JNA)
- **Context**: The Rust/UniFFI/JNA hybrid architecture resulted in `UnsatisfiedLinkError` on Android OEM ROMs (ColorOS/Oppo/Xiaomi), slow multi-architecture builds, heavy `.so` binary bloat, and fragile JNI crossings. Furthermore, the Rust code was only performing regex matching and map lookups, which Kotlin executes just as fast without any JNI overhead.
- **Decision**: Completely strip out Rust, UniFFI, and JNA. Implement `NotificationParser`, `CategoryEngine`, `ScreenshotParserEngine`, `DedupEngine`, and `FraudDetector` in `shared/commonMain` in 100% pure Kotlin.
- **Outcome**: 100% crash-free runtime across all devices, zero NDK compilation overhead, instant builds, and a clean, maintainable KMP architecture.
