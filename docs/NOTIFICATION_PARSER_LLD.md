# CashBuddy — Notification Parser Engine (LLD)

## 1. Executive Overview

The **Notification Parser Engine** ([NotificationParser.kt](file:///c:/Users/ailik/AndroidStudioProjects/CashBuddy/shared/src/commonMain/kotlin/com/cashbuddy/core/NotificationParser.kt)) is the automated intake backbone of CashBuddy. Operating via Android's `NotificationListenerService` (`BIND_NOTIFICATION_LISTENER_SERVICE`), it parses incoming transaction alerts locally in pure Kotlin Multiplatform.

### Key Architectural Advantages:
- **100% On-Device & Zero Network**: Transactions are parsed and categorized in memory with zero cloud transmission.
- **Pure Kotlin Multiplatform**: Directly accessible across common code, ViewModels, and services without JNI glue or native library loader dependencies.
- **5-Layer Defense-in-Depth**: Every notification passes through 5 distinct validation and extraction layers before entering the ledger.

---

## 2. The 5-Layer Processing Pipeline

```
┌─────────────────────────────────────────────────────────────────────────────┐
│  LAYER 1: SOURCE VALIDATION                                                 │
│  • Curated Set allowlist of 50+ banking & payment package names             │
│  • Immediate rejection of non-banking application broadcasts                │
└─────────────────────────────────────┬───────────────────────────────────────┘
                                      │
                                      ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│  LAYER 2: CONTENT CLASSIFICATION & DISCARD                                  │
│  • Discard OTP alerts ("one time password", "verification code", "valid for")│
│  • Discard promotions ("cashback up to", "pre-approved", "win cash")        │
└─────────────────────────────────────┬───────────────────────────────────────┘
                                      │
                                      ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│  LAYER 3: ENTITY EXTRACTION (REGEX ENGINE)                                  │
│  • Amount: Prefixed/suffixed ₹, Rs, INR with comma sanitization             │
│  • Transaction Type: Debit vs. Credit keyword detection                     │
│  • Merchant Name: Regex anchor patterns + app package name fallback         │
│  • Account Last-4: Extracted from card/account references ("XX1234")        │
└─────────────────────────────────────┬───────────────────────────────────────┘
                                      │
                                      ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│  LAYER 4: CATEGORIZATION VIA CATEGORY ENGINE                                │
│  • Tier 1: User-learned corrections (Confidence: 1.0)                       │
│  • Tier 2: 300+ Curated Indian merchant keyword dictionary (Confidence: 0.90)│
│  • Tier 3: Unknown Fallback (Confidence: 0.50)                              │
└─────────────────────────────────────┬───────────────────────────────────────┘
                                      │
                                      ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│  LAYER 5: FRAUD & ANOMALY GUARD                                             │
│  • Velocity rate limiter: Sliding 1-minute window (max 5 tx/min)            │
│  • Confidence Scoring: Sum of entity weights (0.0 to 1.0)                   │
│  • Status Assignment:                                                       │
│    - CONFIRMED if confidence >= 0.85 AND amount < ₹10,000                   │
│    - PENDING (triggers Review Inbox notification) otherwise                 │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 3. Supported Applications Allowlist (Sample)

| Category | Supported Packages |
|---|---|
| **UPI & Wallets** | Google Pay (`com.google.android.apps.nbu.paisa.user`), PhonePe (`com.phonepe.app`), Paytm (`net.one97.paytm`), BHIM (`in.org.npci.upiapp`), CRED (`com.dreamplug.androidapp`), Amazon Pay (`in.amazon.mShop.android.shopping`), WhatsApp Pay (`com.whatsapp`), Navi (`com.naviapp`), MobiKwik (`com.mobikwik_new`), Freecharge (`com.freecharge.android`) |
| **Private Banks** | HDFC (`com.snapwork.hdfc`), ICICI (`com.csam.icici.bank.imobile`), Axis (`com.axis.mobile`), Kotak (`com.msf.kbank.mobile`), IndusInd (`com.indusind.mpassbook`), IDFC FIRST (`com.idfcfirstbank.optimus`), Federal Bank (`com.fedmobile`), YES Bank (`com.yesbank`), RBL (`com.rblbank.mobank`), Bandhan (`com.bandhan.mpassbook`) |
| **Public Banks** | SBI YONO (`com.sbi.lotusintouch`), SBI Card (`com.sbicard.customerapp`), Bank of Baroda (`com.bankofbaroda.mconnect`), PNB (`com.pnb.one`), Canara (`com.canarabank.mobility`), Union Bank (`com.unionbank.ecommerce.mobile.android`), Indian Bank (`com.infrasoft.indianbank`), BOI (`com.boi.omnineo`), CBI (`com.cbi.mobile`), UCO Bank (`com.uco.ucobank`) |
| **Neobanks & Cards** | Jupiter (`money.jupiter`), Fi (`money.fi.banking`), OneCard (`com.onecard.app`), Slice (`org.slicepay`), Uni Cards (`in.uni.cards`), Scapia (`com.scapia.cards`), Niyo (`com.niyo.equitas`) |
