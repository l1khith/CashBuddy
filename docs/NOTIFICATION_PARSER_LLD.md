# PaisaPal KMP + Rust — Notification Parser Engine (LLD)

## 1. Executive Overview

The Notification Parser Engine is the passive intake backbone of PaisaPal. Operating via Android's `NotificationListenerService` (`BIND_NOTIFICATION_LISTENER_SERVICE`), it delegates raw alert payloads directly into the compiled **Rust Core Library (`core-rust/`)** via UniFFI.

### Key Architectural Advantages of Rust Implementation:
- **Sub-Millisecond Regex Execution**: Utilizes Rust's `regex` crate and `nom` parser combinators, yielding 5-80× faster throughput than JVM regex.
- **Zero JVM Heap Pressure**: Temporary strings, token sequences, and intermediate captures are allocated and freed deterministically in native memory without triggering Android ART Garbage Collector pauses.
- **Deep Categorization via On-Device NLP**: When heuristic rules yield low confidence, the engine triggers an on-device quantized DistilBERT INT8 model running on ONNX Runtime (`ort`), achieving $>95\%$ accuracy in under 50ms with only $\approx 15 \text{ MB}$ RAM.

---

## 2. 5-Layer Pipeline in Rust

```
┌─────────────────────────────────────────────────────────────────────────────┐
│  LAYER 1: SOURCE VALIDATION (`engine.rs`)                                   │
│  • Curated HashSet allowlist of 50+ banking & payment package names         │
│  • Immediate rejection of untrusted application broadcasts                  │
└─────────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│  LAYER 2: CONTENT CLASSIFICATION & DISCARD (`engine.rs`)                    │
│  • Fast keyword rejection: OTP phrases, promotional terms, discount vouchers│
│  • Discard non-transactional alerts before entity parsing                   │
└─────────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│  LAYER 3: ENTITY EXTRACTION                                                 │
│  • Amount (`amount_extractor.rs`): Regex ₹, Rs., INR with comma normalization│
│  • Type (`type_detector.rs`): Debit vs Credit keyword matching               │
│  • Merchant (`merchant_extractor.rs`): Pattern matching + app name fallback │
│  • Account (`account_extractor.rs`): Last 4 digit identification ("XX1234") │
└─────────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│  LAYER 4: CATEGORIZATION & ML INFERENCE                                     │
│  • Stage A: Keyword & Merchant rules mapping (Instant)                      │
│  • Stage B: If category is Unknown or Confidence < 0.70, invoke             │
│    DistilBERT ONNX Classifier (`classifier/model.rs`) via ort crate          │
│  • 7-factor confidence score calculated (0.0 to 1.0)                        │
└─────────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│  LAYER 5: FRAUD & ANOMALY GUARD (`fraud_detector.rs`)                       │
│  • In-memory sliding velocity window (max 5 transactions per minute)        │
│  • 5-minute duplicate bloom/hash suppression                                │
│  • Status determined: CONFIRMED if confidence >= 0.85 & amount < threshold, │
│    otherwise marked PENDING for Review Inbox                                │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 3. DistilBERT ONNX Categorization Pipeline

- **Model Specification**: Quantized INT8 DistilBERT sequence classifier (`finmigodeveloper/distilbert-transaction-classifier`).
- **Disk Footprint**: $\approx 10 \text{ MB}$ bundled in `models/`.
- **Inference Runtime**: `ort` crate wrapping ONNX Runtime Mobile.
- **Target Latency**: $< 50 \text{ ms}$ on modern mobile ARM64 CPUs.
- **Categories (14 Classes)**: Food, Transport, Shopping, Bills, Entertainment, Health, Education, Housing, Insurance, Investments, Salary, Refund, Gift, Unknown.
