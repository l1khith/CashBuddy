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

### ADR-009: Atomic Multi-Table Write Clusters via Dedicated Stores (2026-11 Production Audit)
- **Context**: The 2026-11 production-readiness audit found that multi-table write flows (duplicate merge, survivor delete, clear-all, ingest+audit-link) ran as separate SQLDelight transactions from repositories/ViewModels. A crash mid-flow left half-committed states: ghost merged rows, orphaned merge_log entries, half-reset user data, wrong `last_insert_rowid()` under concurrency.
- **Decision**: Cross-table write flows live in dedicated atomic stores that execute the WHOLE flow in one driver-level SQLDelight transaction: `TransactionIngestStore`, `SqlDelightMergeStore` (`MergeTransactionStore`), `SqlDelightDataResetStore` (`DataResetStore`). Transaction bodies MUST stay free of suspend calls and dispatcher hops (SQLDelight transactions are thread-bound). Repositories become pure orchestration; ViewModels never sequence multi-repo writes for destructive flows.
- **Consequence**: Any future multi-table write must follow this pattern; single-table writes may stay in their repository.

### ADR-010: Batched Budget Status Computation (2026-11 Production Audit)
- **Context**: `BudgetEngine.statusFor` issued ~4 queries per budget; `BudgetViewModel` re-ran it for every budget on every `transactionUpdates` emission (N+1).
- **Decision**: `BudgetEngine.statusesFor(budgets, now)` fetches the period's transactions ONCE per DISTINCT period and derives every budget from the shared list via a single `computeStatus` core whose filters exactly mirror `sumAll`/`sumByCategory`/`getByDateRange` SQL semantics. Single-budget `statusFor` shares the same core.
- **Consequence**: Test fakes for `TransactionRepository` used with BudgetEngine MUST implement `getByDateRange` faithfully (timestamp range + `is_merged = 0`), not return empty lists.

---

## 4. Open Decisions Deferred to Product Owner (2026-11 Audit Follow-up)

1. **Full `.sqm` migration collapse** — schema v4 columns (`is_merged`, `merged_into_id`, `payment_method`, `is_loan_given`, `merge_log`) are currently created by the guarded runtime patcher in `DatabaseDriverFactory.runMigrations` because NO `4.sqm` exists; users upgrading from v≤3 depend on the patcher. Collapsing into `4.sqm` would crash existing v4 installs (duplicate-column ALTER) unless versioned carefully. Needs install-base handling decision.
2. **`Tokenizer.MAX_TOKEN_CHARS = 24`** — conservative default; adjustable (longest common English words ~15 chars, medical jargon ~22).
3. **iOS SQLCipher encryption** — Android path is encrypted; iOS driver is plaintext SQLite. Priority + approach (SQLCipher for iOS vs platform Keychain-based file encryption) undecided.
4. **Biometric opt-in default** — currently opt-in (`biometricEnabled` default false); ADR-006 mandates BIOMETRIC_STRONG when enabled.
5. **Clipboard dedup window** — `DedupEngine` clipboard-source dedup window size N not finalized.
