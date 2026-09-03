# PaisaPal KMP + Rust — High-Level Design (HLD)

## 1. Executive Summary & Vision

**PaisaPal** is an intelligent, privacy-first, 100% offline personal finance tracking application built with a **Kotlin Multiplatform (KMP) + Rust hybrid architecture**. It automatically tracks expenses, income, and account balances by processing notifications from payment and banking applications (Google Pay, PhonePe, Paytm, CRED, HDFC, SBI, ICICI, etc.) locally on the device with zero cloud dependency.

### Non-Negotiable Core Pillars:
1. **Zero SMS Access**: Uses Android's `NotificationListenerService` (`BIND_NOTIFICATION_LISTENER_SERVICE`) exclusively. No `READ_SMS` or `RECEIVE_SMS` permissions.
2. **Zero Network Transmission**: Operates 100% offline. Zero `INTERNET` permission in production. No servers, no telemetry, no cloud synchronization.
3. **High-Performance Rust Core (`core-rust/`)**: Rust powers the Notification Parser, on-device DistilBERT ONNX classifier, and AES-256-GCM / Argon2id cryptography with zero JVM GC latency and zero JVM heap key retention.
4. **Hardware-Backed Cryptography**: All local data at rest is encrypted via **SQLCipher (AES-256 GCM)**, keyed through the **Android Keystore (TEE / StrongBox)** and Rust crypto core.
5. **Human-in-the-Loop Verification**: Any transaction with confidence $< 0.85$ or amount $\ge ₹10,000$ requires explicit user confirmation in the Review Inbox.

---

## 2. Revised Hybrid Architecture

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              PRESENTATION LAYER                             │
│                              (Pure Kotlin KMP)                              │
│  Jetpack Compose Multiplatform 1.11+ → ViewModels → StateFlow → Nav 3       │
└─────────────────────────────────────┬───────────────────────────────────────┘
                                      │
                                      ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                              DOMAIN LAYER                                   │
│                              (Pure Kotlin KMP)                              │
│  Use Cases → Repository Interfaces → Immutable Models → Flow<T>             │
└─────────────────────────────────────┬───────────────────────────────────────┘
                                      │
                                      ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                               DATA LAYER                                    │
│  ┌─────────────────────────┐  ┌─────────────────────────────────────────┐  │
│  │   Kotlin Repositories   │  │           RUST CORE LIBRARY             │  │
│  │   (SQLDelight + Flow)   │  │  ┌─────────────┐  ┌─────────────────┐  │  │
│  │                         │  │  │   Parser    │  │   Classifier    │  │  │
│  │  TransactionRepository  │◄─┤  │   Engine    │  │   (DistilBERT)  │  │  │
│  │  AccountRepository      │  │  │  • Regex    │  │  • ONNX Runtime │  │  │
│  │  CategoryRepository     │  │  │  • Amount   │  │  • 14 categories│  │  │
│  │  BudgetRepository       │  │  │  • Merchant │  │  • ~15MB RAM    │  │  │
│  │  GoalRepository         │  │  │  • Account  │  │  • ~10MB disk   │  │  │
│  │  SettingsRepository     │  │  │  • Fraud    │  │  • <50ms infer  │  │  │
│  └─────────────────────────┘  │  └─────────────┘  └─────────────────┘  │  │
│                               │  ┌─────────────┐  ┌─────────────────┐  │  │
│                               │  │  Security   │  │   Crypto Core   │  │  │
│                               │  │  • Keystore │  │  • AES-256-GCM  │  │  │
│                               │  │  • Biometric│  │  • Argon2id KDF │  │  │
│                               │  │  • Root det │  │  • Zeroize mem  │  │  │
│                               │  └─────────────┘  └─────────────────┘  │  │
│                               └─────────────────────────────────────────┘  │
│                                           │                                 │
│                                           ▼                                 │
│                               ┌─────────────────────┐                       │
│                               │   UniFFI Bridge     │                       │
│                               │   (Auto-generated   │                       │
│                               │    Kotlin bindings) │                       │
│                               └─────────────────────┘                       │
└─────────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                            PLATFORM LAYER                                   │
│  ┌─────────────────────────┐  ┌─────────────────────────────────────────┐  │
│  │      Android            │  │         iOS (Future)                    │  │
│  │  NotificationListener   │  │  UNUserNotificationCenter               │  │
│  │  Keystore (TEE/StrongBox│  │  Keychain Services                      │  │
│  │  BiometricPrompt        │  │  LocalAuthentication                    │  │
│  │  SQLCipher driver       │  │  SQLCipher driver                       │  │
│  └─────────────────────────┘  └─────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 3. Module Topology & Build Artifacts

```
             ┌─────────────────────────────┐
             │         :androidApp         │
             │   (Android APK Entry Point) │
             └──────────────┬──────────────┘
                            │
                            ▼
             ┌─────────────────────────────┐
             │         :composeApp         │
             │  (Shared UI + Nav + Koin)   │
             │  • UniFFI Kotlin Bindings   │
             └──────────────┬──────────────┘
                            │
                            ▼
             ┌─────────────────────────────┐
             │           :shared           │
             │   (Domain + SQLDelight DB)  │
             └──────────────┬──────────────┘
                            │
                            ▼
             ┌─────────────────────────────┐
             │         core-rust/          │
             │   (Rust Static/CDyLib)      │
             │  • libpaisapal_core.so      │
             │  • Notification Parser      │
             │  • DistilBERT ONNX ML       │
             │  • AES-256-GCM / Argon2id   │
             └─────────────────────────────┘
```

---

## 4. Size & Performance Budget

| Component | Size (Android arm64) | RAM (Runtime) |
|---|---|---|
| Kotlin / KMP Base | $\approx 6 \text{ MB}$ | $\approx 25 \text{ MB}$ |
| Rust Core (LTO + stripped) | $\approx 1.5 \text{ MB}$ | $\approx 5 \text{ MB}$ |
| ONNX Runtime Mobile (`ort`) | $\approx 4 \text{ MB}$ | $\approx 8 \text{ MB}$ |
| DistilBERT Quantized INT8 | $\approx 10 \text{ MB}$ | $\approx 15 \text{ MB}$ |
| SQLDelight + SQLCipher | $\approx 2 \text{ MB}$ | $\approx 3 \text{ MB}$ |
| **Total Footprint** | **$\approx 23.5 \text{ MB}$** | **$\approx 56 \text{ MB}$** |

**Budget Enforcement: Download size $< 25 \text{ MB}$, runtime RAM $< 100 \text{ MB}$.**
