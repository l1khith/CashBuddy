# PaisaPal KMP + Rust — Security Architecture Specification

## 1. Threat Model & Mitigation Matrix

| Threat ID | Threat Vector | Impact | Mitigation Strategy | Implementation Details |
|---|---|---|---|---|
| **TH-01** | Data-at-rest exfiltration | Critical | Full SQLite database encryption using SQLCipher (AES-256 GCM) | Keys derived via Rust `CryptoManager` and stored in Android Keystore |
| **TH-02** | Key extraction via JVM heap dump | High | Native memory key handling with RAII & `zeroize` | Key material is held in Rust memory and overwritten with zeros on `Drop`; never left in JVM GC heap |
| **TH-03** | Weak passphrase derivation | High | Argon2id Key Derivation Function | Rust `argon2` crate with mobile-tuned memory-hard parameters (16MB memory, 2 passes) |
| **TH-04** | Rooted/Jailbroken environment | High | Multi-layer native inspection via Rust `SecurityValidator` | Direct inspection of su paths and build properties |
| **TH-05** | Notification spoofing | High | OS-enforced signature & package verification + Rust allowlist | Verified against 50+ item allowlist HashSet in native memory |
| **TH-06** | Local shoulder surfing | Medium | Biometric app lock on resume/launch | AndroidX `BiometricPrompt` with `BIOMETRIC_STRONG` |
| **TH-07** | Screen recording | Medium | Screen capture prevention | `FLAG_SECURE` set on sensitive activities |
| **TH-08** | Network leakage | Zero | Zero network permissions declared in AndroidManifest.xml | Total removal of `android.permission.INTERNET` eliminates network vector entirely |

---

## 2. Zero-Heap-Leakage Key Lifecycle (Rust + Keystore)

```
┌─────────────────┐     ┌─────────────────┐     ┌─────────────────┐
│   App Launch    │────►│ Android Keystore│────►│ Retrieve Master │
│                 │     │ (TEE/StrongBox) │     │ Encrypted Key   │
└─────────────────┘     └─────────────────┘     └────────┬────────┘
                                                         │
                                                         ▼
                                                ┌─────────────────┐
                                                │  Rust Core FFI  │
                                                │  (CryptoManager)│
                                                └────────┬────────┘
                                                         │
                                      ┌──────────────────┴──────────────────┐
                                      ▼                                     ▼
                              ┌───────────────┐                     ┌───────────────┐
                              │  Argon2id KDF │                     │ AES-256-GCM   │
                              │  Derivation   │                     │ Decrypt Key   │
                              └───────┬───────┘                     └───────┬───────┘
                                      │                                     │
                                      └──────────────────┬──────────────────┘
                                                         │
                                                         ▼
                                                ┌─────────────────┐
                                                │  SQLCipher DB   │
                                                │  Open & Decrypt │
                                                └────────┬────────┘
                                                         │
                                                         ▼
                                                ┌─────────────────┐
                                                │ Key Zeroization │
                                                │ (zeroize memory)│
                                                └─────────────────┘
```

By decoupling key operations into native Rust buffers protected with `zeroize::Zeroize`, cryptographic keys are never copied across JVM generations or abandoned in garbage-collected heap spaces.
