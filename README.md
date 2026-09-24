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
- 📊 **Visual Analytics**: Interactive monthly summaries, spending category breakdowns, budget tracking, and financial goals.

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
| **Text Recognition (OCR)** | Bundled Google ML Kit (100% Offline) |
| **Concurrency** | Kotlin Coroutines & Flow |
| **Target Platforms** | Android (API 26+) & iOS |

---

## 🚀 Building & Running

### Prerequisites
- Android Studio Ladybug / Meerkat (or newer)
- JDK 17 or JDK 21
- Android SDK 35

### Build Commands

```bash
# Build Android Debug APK
./gradlew :androidApp:assembleDebug

# Build Android Release APK (with R8 minification)
./gradlew :androidApp:assembleRelease

# Run all unit tests
./gradlew :shared:testAndroidHostTest
```

---

## 📄 License & Privacy

CashBuddy is built with a zero-compromise approach to privacy:
- Zero data collection
- Zero analytics or tracking SDKs
- Zero network communication
- 100% local on-device operation