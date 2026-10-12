# CashBuddy 💰

**CashBuddy** is a 100% offline, privacy-first personal finance tracker built with **Kotlin Multiplatform (KMP)** and **Compose Multiplatform**.

It captures, categorizes, and tracks expenses and income across all Indian banks and UPI applications through a reliable three-layer intake system—with **zero SMS permissions**, **zero network permissions**, and **hardware-backed AES-256 database encryption**.

---

## 🌟 Key Features

- 🔒 **100% Offline & Private**: Zero `INTERNET` permission in production. Your financial data never leaves your device.
- 🚫 **Zero SMS Permissions**: Fully compliant with Google Play Store policies. Uses `BIND_NOTIFICATION_LISTENER_SERVICE` exclusively.
- ⚡ **Pure Kotlin Multiplatform Core**: Built with 100% pure Kotlin (`shared/commonMain`). Zero JNI/NDK dependencies, zero native crashes, instant builds, and a tiny APK size (<15 MB).
- 📲 **3-Layer Transaction Capture**:
  1. **Notification Listener**: Passively parses transactional alerts from 50+ curated Indian banks and UPI apps (Google Pay, PhonePe, Paytm, CRED, HDFC, SBI, ICICI, Axis, etc.).
  2. **Screenshot Sharing**: Share UPI payment screenshots directly to CashBuddy from any app (e.g. BMTC tickets, GPay, PhonePe receipts). Offline ML Kit OCR extracts text and parses entities automatically.
  3. **Smart Manual Entry**: Instant manual entry with frequency-based merchant chips, auto-prefill, and fast category assignment.
- 🧠 **On-Device Hybrid Personalization**:
  - **Tier 1 (User Rules)**: Custom merchant-to-category rules learned immediately from user corrections (Confidence: 1.0).
  - **Tier 2 (Keyword Dictionary)**: Curated dictionary of 300+ Indian merchant keywords (Swiggy, Zomato, Uber, Blinkit, D-Mart, etc.) (Confidence: 0.90).
  - **Tier 3 (Fallback)**: Graceful fallback to Uncategorized for manual review.
- 🛡️ **Human-in-the-Loop Review**: Transactions with confidence $< 0.85$ or amount $\ge ₹10,000$ are flagged as `PENDING` for explicit user review.
- 🔐 **Hardware-Backed Encryption**: Encrypted with **SQLCipher (AES-256 GCM)** using hardware-backed keys from the **Android Keystore (TEE / StrongBox)**.
- 👆 **Biometric Security**: Protected by `BIOMETRIC_STRONG` authentication on app launch and automatically locks after 5 minutes of background inactivity.
- 📊 **Visual Analytics**: Interactive monthly summaries, spending category breakdowns, and financial goals.
- 🎯 **Global & Category Budgets & Real-Time Alerts**: Set an overall global spending limit as well as per-category budgets for monthly, weekly, or yearly periods. Features computed Unbudgeted spending visibility, dynamic progress tracking (<80% green, 80–99% amber, ≥100% red), prioritized at-most-once notifications (category before global) at 80% and 100% threshold crossings, automated period rollover, and transaction drill-down. (See [Budgets Spec](specs/budgets/spec.md) and [Global Budget Spec](specs/budgets/spec-global.md)).

---

## 🏗️ Architecture Overview

CashBuddy follows **Clean Architecture** and **Unidirectional Data Flow (UDF)**:

```
┌────────────────────────────────────────────────────────┐
│                   PRESENTATION LAYER                   │
│      (Compose Multiplatform 1.11+ • Material 3)        │
│       HomeScreen • ReviewScreen • TransactionsScreen   │
│         StatsScreen • SettingsScreen • AddScreen       │
└───────────────────────────┬────────────────────────────┘
                            │ StateFlow / Events
                            ▼
┌────────────────────────────────────────────────────────┐
│                      DOMAIN LAYER                      │
│            (Pure Kotlin Multiplatform)                 │
│    Use Cases: CalculateBalance, ConfirmTransaction,    │
│     GetRecentTransactions, GenerateBreakdown, etc.     │
└───────────────────────────┬────────────────────────────┘
                            │
              ┌─────────────┴─────────────┐
              ▼                           ▼
┌───────────────────────────┐ ┌──────────────────────────┐
│        DATA LAYER         │ │       CORE ENGINES       │
│    SQLDelight Database    │ │ 5-Layer Notification     │
│   SQLCipher AES-256 GCM   │ │   Parser Engine          │
│    Android Keystore TEE   │ │ 3-Tier Category Engine   │
│   Unified Ledger Tables   │ │ Screenshot OCR Parser    │
│  Correction & Rule Repos  │ │ Dedup & Fraud Guards     │
└───────────────────────────┘ └──────────────────────────┘
```

---

## 🛠️ Tech Stack

| Layer | Technology |
|---|---|
| **Framework** | Kotlin Multiplatform (KMP 2.4.10) |
| **UI Toolkit** | Compose Multiplatform 1.11.1 (Material 3) |
| **Database** | SQLDelight 2.0.2 with SQLCipher (AES-256 GCM) |
| **Key Storage** | Android Keystore (TEE / StrongBox) |
| **Dependency Injection** | Koin 4.0 |
| **Text Recognition (OCR)** | Play flavor: Google ML Kit • F-Droid flavor: Tesseract4Android (100% Offline) |
| **Concurrency** | Kotlin Coroutines & Flow |
| **Target Platforms** | Android (API 24+ / targetSdk 36) & iOS |

---

## 🚀 Building & Running

### Prerequisites
- Android Studio Ladybug / Meerkat (or newer)
- JDK 21
- Android SDK 36

### Build Flavors (`play` & `fdroid`)

CashBuddy provides two distribution flavors:
- **`play`**: Uses Google ML Kit (`com.google.mlkit:text-recognition`) for offline screenshot OCR.
- **`fdroid`**: 100% free and open-source build with zero proprietary dependencies. Uses **Tesseract4Android** (`cz.adaptech.tesseract4android:tesseract4android:4.9.0`, Apache-2.0 License) and bundles `eng.traineddata` and `hin.traineddata` from [`tesseract-ocr/tessdata_fast`](https://github.com/tesseract-ocr/tessdata_fast) under the **Apache-2.0 License** in `androidApp/src/fdroid/assets/tessdata/` (adds ~5.2 MB of uncompressed traineddata to the F-Droid APK).

### Build Commands

```bash
# Build Google Play Release APK & AAB
./gradlew :androidApp:assemblePlayRelease :androidApp:bundlePlayRelease

# Build F-Droid Unsigned Release APK (FOSS / Tesseract OCR)
./gradlew :androidApp:assembleFdroidRelease

# Run all unit tests
./gradlew :shared:testAndroidHostTest
```

---

## 📄 License & Privacy

CashBuddy is built with a zero-compromise approach to privacy:
- Zero data collection
- Zero analytics or tracking SDKs
- Zero network communication (`INTERNET` permission stripped)
- 100% local on-device operation
- F-Droid flavor bundles `eng.traineddata` and `hin.traineddata` from `tesseract-ocr/tessdata_fast` under the Apache License 2.0.