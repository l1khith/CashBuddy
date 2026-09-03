# PaisaPal KMP + Rust — Project Memory & Architectural Decisions (ADR)

## 1. Project Overview & Identity
- **Product Name**: PaisaPal — Intelligent Offline Finance Tracker
- **Architecture**: Clean Architecture + KMP + Rust Core Hybrid
- **Platforms**: Android (API 24+) → iOS (Phase 2 via KMP + Rust staticlib)
- **Build System**: Gradle 8.5+ with Version Catalogs + Cargo for `core-rust/`
- **Current Milestone**: **KMP + Rust Hybrid Architecture & Project Structure Established**.

---

## 2. Technology Stack Matrix

| Layer / Concern | Technology | Version | Rationale |
|---|---|---|---|
| **Language (App)** | Kotlin Multiplatform | 2.4.x | Clean domain, UI, and reactive state management. |
| **Language (Core)** | Rust | 2021 Edition | Zero GC pauses, sub-millisecond parsing, zero JVM heap key retention. |
| **FFI Bridge** | UniFFI | 0.28.x | Production-grade auto-generated type-safe Kotlin/C bindings. |
| **ML Inference** | ONNX Runtime Mobile (`ort`) | 2.0.x | Zero-copy tensor ops, INT8 quantized DistilBERT (~10MB disk, ~15MB RAM). |
| **Cryptography** | `aes-gcm`, `argon2`, `zeroize` | 0.10 / 0.5 / 1.7 | AES-256-GCM, Argon2id KDF, automatic memory wiping on drop. |
| **UI Framework** | Compose Multiplatform | 1.11.x | Declarative, shared UI across Android and iOS targets. |
| **Local Database** | SQLDelight | 2.2.1 | Compile-time SQL verification, reactive Flow queries, zero reflection. |
| **Data Encryption** | SQLCipher | 4.6.1 | AES-256 GCM page-level SQLite database encryption. |
| **Dependency Injection** | Koin | 4.0.0 | KMP-native, constructor-based, zero annotation processing overhead. |

---

## 3. Architectural Decision Records (ADRs)

### ADR-001: Notification Listener Instead of SMS Access
- **Decision**: PaisaPal exclusively implements `BIND_NOTIFICATION_LISTENER_SERVICE` and declares zero SMS permissions.

### ADR-002: Zero Network Permission (100% Offline Architecture)
- **Decision**: The `android.permission.INTERNET` permission is completely omitted from production manifests.

### ADR-003: Pure Kotlin for Presentation, Navigation, and Database
- **Decision**: Keep UI (Compose), ViewModels, Navigation 3, and SQLDelight in pure Kotlin for developer velocity.

### ADR-004: Dual UI Pattern (MVVM for Dashboards, MVI for Review Inbox)
- **Decision**: MVVM for standard screens, MVI with pure Reducer for ReviewScreen.

### ADR-005: Two-Tier Confidence & Value Confirmation Policy
- **Decision**: Auto-confirm only if `confidence >= 0.85` AND `amount < autoConfirmThreshold` (default ₹10,000). All other alerts are routed to the Review Inbox as `PENDING`.

### ADR-006: Hardware Keystore + StrongBox Protection
- **Decision**: Android Keystore keys generated in hardware (TEE/StrongBox) to encrypt application passphrases.

### ADR-007: Rust Core Integration for Cryptography, Parsing & ML
- **Context**: The user required a production-grade ("no MVP, only final product") solution with zero GC latency during notification bursts, non-extractable cryptographic key memory, and high-accuracy categorization via quantized NLP.
- **Decision**: Create `core-rust/` compiled via UniFFI (`ch.ubique.uniffi`). Rust handles:
  1. 5-layer notification parser with `regex` and `nom`.
  2. DistilBERT ONNX runtime classifier with INT8 quantization (`ort`).
  3. AES-256-GCM authenticated cipher and Argon2id KDF with `zeroize`.
  4. Real-time velocity and duplicate fraud guard.
- **Rules**: Zero `unwrap()` / Zero `expect()` in production code. All errors use `Result<T, CoreError>`. Size-optimized profile (`opt-level = "z"`).

---

## 4. Execution Roadmap Status
- [x] Rust Core Library High-Level Design (`docs/RUST_CORE_HLD.md`)
- [x] Rust Core Library Low-Level Design (`docs/RUST_CORE_LLD.md`)
- [x] Full `core-rust/` Project Structure Authoring (Cargo.toml, uniffi.toml, build.rs, UDL, parser, classifier, crypto, security)
- [x] Models Directory Created (`models/README.md`)
- [x] Agent Skills Configured (`.skills/` & `.agents/skills/`)
- [x] Governance, Memory & Rules Updated (`rules.md`, `skills.md`, `memory.md`, `subagents.md`, `AGENTS.md`)
- [ ] Phase 1: Rust Core Library Build & Verification
- [ ] Phase 2: KMP & UniFFI Integration
- [ ] Phase 3: Notification Listener Service & Pipeline
- [ ] Phase 4: Compose Multiplatform UI Implementation
- [ ] Phase 5: Security Hardening & Release Polish
