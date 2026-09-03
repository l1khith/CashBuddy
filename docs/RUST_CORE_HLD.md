# PaisaPal Core — High-Level Design (HLD) for Rust Core Library

## 1. Executive Summary & Purpose

The **Rust Core Library (`core-rust/`)** provides a zero-overhead, memory-safe, deterministic foundation for performance-critical and security-sensitive operations in PaisaPal.

### Core Rationales for Rust:
1. **Zero JVM Garbage Collection Spikes**: The Notification Listener Service processes rapid bursts of alerts. Pure Kotlin regex and string allocations cause GC pressure and frame drops. Rust processes raw strings with zero heap allocations where possible and deterministic deallocation.
2. **Zero JVM Heap Key Retention**: Master keys in JVM memory cannot be reliably wiped due to GC object copying. Rust uses RAII and `zeroize` to zero out cryptographic keys in memory immediately after use.
3. **High-Performance On-Device ML**: Leverages the official `ort` crate for ONNX Runtime Mobile, enabling zero-copy tensor operations and quantized INT8 inference in under 50ms with only ~15MB RAM.
4. **Binary Footprint Budget**: Optimized for size via LTO, single codegen unit, and symbol stripping, keeping the compiled static/cdylib under 2MB.

---

## 2. System Context & UniFFI Bridge

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           KMP KOTLIN APPLICATION                            │
│  ┌───────────────────────────────────────────────────────────────────────┐  │
│  │ Presentation Layer (Compose Multiplatform)                            │  │
│  │ ViewModels & StateFlow (UDF)                                          │  │
│  │ Domain Layer (Use Cases, Repository Interfaces)                       │  │
│  │ Local Persistence (SQLDelight + Flow)                                 │  │
│  └──────────────────────────────────┬────────────────────────────────────┘  │
└─────────────────────────────────────┼───────────────────────────────────────┘
                                      │
                                      ▼  (UniFFI Auto-Generated Bindings)
┌─────────────────────────────────────────────────────────────────────────────┐
│                          C-ABI & SCENARIO BOUNDARY                          │
│  • Auto-generated JNI bridge on Android                                    │
│  • Direct C-ABI bridge on iOS                                               │
│  • Zero-overhead value marshaling for primitive and byte sequences          │
└─────────────────────────────────────┬───────────────────────────────────────┘
                                      │
                                      ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                              RUST CORE LIBRARY                              │
│  ┌───────────────────────────────────────────────────────────────────────┐  │
│  │ Notification Parser Engine (regex + nom combinators)                  │  │
│  │ • 50+ banking package allowlist                                       │  │
│  │ • OTP / Promo elimination                                             │  │
│  │ • Amount, merchant, account extraction                                │  │
│  │ • 7-factor confidence scoring                                         │  │
│  ├───────────────────────────────────────────────────────────────────────┤  │
│  │ DistilBERT ONNX Classifier (ort + WordPiece)                          │  │
│  │ • INT8 quantized model (~10MB disk, ~15MB RAM)                        │  │
│  │ • 14 financial category classification                                │  │
│  ├───────────────────────────────────────────────────────────────────────┤  │
│  │ Cryptographic Core (aes-gcm + argon2 + zeroize)                       │  │
│  │ • AES-256-GCM authenticated encryption                                │  │
│  │ • Argon2id key derivation                                             │  │
│  │ • Secure ephemeral key management                                     │  │
│  ├───────────────────────────────────────────────────────────────────────┤  │
│  │ Security & Anomaly Guard                                              │  │
│  │ • In-memory velocity checks (max 5/min)                               │  │
│  │ • 5-minute duplicate bloom/hash suppression                           │  │
│  │ • Root detection heuristics                                           │  │
│  └───────────────────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 3. Module Boundaries & Interaction Model

1. **`NotificationParser`**:
   - Accepts `RawNotification` from Kotlin's `NotificationListenerService`.
   - Executes Layer 1 through Layer 5 in Rust.
   - Returns `Option<ParsedTransaction>` to Kotlin without touching the JVM GC.

2. **`TransactionClassifier`**:
   - Invoked when parser confidence is below the high threshold or category is `Unknown`.
   - Tokenizes input with native WordPiece tokenizer.
   - Runs ONNX runtime inference session via `ort`.
   - Returns `ClassificationResult` with probability distribution over 14 categories.

3. **`CryptoManager`**:
   - Generates 256-bit entropy keys using OS random generator (`rand::rngs::OsRng`).
   - Encrypts and decrypts SQLCipher database passphrases and export payloads using AES-256-GCM.
   - Derives encryption keys from user passwords using Argon2id.
   - Guarantees immediate zeroization of memory upon drop.

4. **`SecurityValidator`**:
   - Performs rapid root and tamper heuristics natively before application initialization.
