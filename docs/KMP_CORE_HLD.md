# CashBuddy — Core Engine High-Level Design (HLD)

## 1. Scope & Responsibilities

The **Core Engine** (`shared/src/commonMain/kotlin/com/cashbuddy/core/`) encapsulates all parsing, entity extraction, deduplication, categorization, and anomaly prevention algorithms for CashBuddy.

By implementing these engines in **Pure Kotlin Multiplatform**, CashBuddy achieves:
- **Zero Native Crashes**: Eliminates `UnsatisfiedLinkError` and JVM segmentation faults on OEM Android ROMs (Oppo/Realme/OnePlus ColorOS, Xiaomi MIUI/HyperOS, Samsung OneUI).
- **Zero JNI Latency**: Direct JVM/ART function calls without serialization or JNI string copying overhead.
- **Tiny APK Footprint**: Avoids bundling multiple megabytes of `.so` libraries across `arm64-v8a` and `x86_64` ABIs.
- **Cross-Platform Parity**: The exact same parsing and categorization algorithms compile directly to ARM64 for iOS without duplicate maintenance.

---

## 2. Core Engine Components

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                          CASHBUDDY CORE ENGINES                             │
│                      (shared/commonMain/com/cashbuddy/core)                 │
├──────────────────────────────────────┬──────────────────────────────────────┤
│ 1. NotificationParser                │ 2. ScreenshotParserEngine            │
│ • 50+ banking/UPI package allowlist  │ • Multi-app detection (GPay, PhonePe)│
│ • OTP & promotional alert discard    │ • Fuzzy amount & transit extractors  │
│ • Regex entity extraction            │ • UTR & VPA handle extraction        │
│ • Confidence scoring (0.0 to 1.0)    │ • Offline ML Kit OCR text ingestion  │
├──────────────────────────────────────┼──────────────────────────────────────┤
│ 3. CategoryEngine                    │ 4. DedupEngine                       │
│ • Tier 1: User-learned rules (1.0)   │ • 5-minute sliding time window       │
│ • Tier 2: 300+ keyword dictionary    │ • 1 paisa (₹0.01) float tolerance    │
│ • Tier 3: Unknown / Fallback (0.50)  │ • Multi-channel collision detection  │
│ • Lock-free Volatile copy-on-write   │ • Merchant suffix normalization      │
├──────────────────────────────────────┼──────────────────────────────────────┤
│ 5. FraudDetector                     │ 6. ProbabilisticClassifier           │
│ • Velocity rate limiting (5 tx/min)  │ • Naive Bayes log-odds inference     │
│ • Deduplication hash generation      │ • Content-first SourceDetector       │
└──────────────────────────────────────┴──────────────────────────────────────┘
```

---

## 3. High-Level Workflows

### 3.1. Notification Intake Workflow
1. Android OS triggers `onNotificationPosted` in `TransactionNotificationListener`.
2. `NotificationParser.parse(raw)` processes content signals via `SourceDetector` and `EvidenceExtractor`.
3. Content is screened for OTP / promo triggers via `ProbabilisticClassifier`.
4. Amount, Debit/Credit type, Merchant, and Account number are extracted via compiled Regexes.
5. `CategoryEngine.getCategory(merchant)` determines category and confidence score.
6. `DedupEngine` checks against recent database records within the last 5 minutes.
7. If novel, transaction is persisted with `CONFIRMED` or `PENDING` status based on policy thresholds.

### 3.2. Screenshot Intake Workflow
1. User shares a screenshot from a payment app via Android's system share sheet (`ACTION_SEND`).
2. `MainActivity` caches the image and triggers `ScreenshotParser`.
3. Local Google ML Kit Text Recognition processes the bitmap into structured text blocks.
4. `ScreenshotParserEngine.parse(ocrText)` extracts amount, merchant, type, app name, and UTR.
5. `DedupEngine` verifies no duplicate transaction exists.
6. Transaction is persisted, a Toast feedback message is shown, and the user is navigated to the Transactions list.
