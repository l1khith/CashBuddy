# PaisaPal — Google Play Store Metadata & Compliance Assets

## 1. Store Listing Information

### 1.1 App Title & Tagline
- **Title (30 chars)**: `PaisaPal: Offline Money Track`
- **Short Description (80 chars)**: `Auto-track expenses from payment notifications. 100% offline, zero SMS, no ads.`

### 1.2 Full Description (up to 4000 chars)
```text
Take complete control of your personal finances with PaisaPal — the privacy-first money manager that automatically tracks your expenses and bank balances without sacrificing your personal data.

🔒 100% OFFLINE & ZERO INTERNET PERMISSION
Unlike other finance apps that upload your sensitive bank data to cloud servers, PaisaPal works completely offline. The app does not request or possess the Android INTERNET permission. Your financial records never leave your phone.

🚫 ZERO SMS ACCESS
PaisaPal does NOT read your SMS inbox. Instead, it securely listens to incoming payment and banking notification alerts via Android's official Notification Access Service. Personal texts and private messages remain completely untouched.

⚡ AUTOMATIC EXPENSE TRACKING
PaisaPal instantly recognizes transactions from your favorite Indian UPI and banking apps:
• Google Pay, PhonePe, Paytm, CRED, BHIM UPI
• HDFC, ICICI, SBI, Axis, Kotak, IDFC First, and 40+ banks
• Detects debits, credits, merchants, and categories automatically

🛡️ HARDWARE-GRADE SECURITY
Your entire financial database is encrypted at rest using AES-256 GCM encryption via SQLCipher, protected by keys stored in your device's hardware-backed Keystore (TEE/StrongBox). Lock your app with fingerprint or biometric face unlock.

📥 SMART REVIEW INBOX
Unsure about a transaction? PaisaPal features an intelligent Review Inbox with confidence scoring. Review, categorize, or modify any payment with a single swipe. High-value transactions always prompt for your approval.

📊 BEAUTIFUL INSIGHTS & BUDGETS
• Visual monthly spending graphs and category breakdowns
• Set monthly category budgets with timely threshold warnings
• Track savings goals with progress meters
• Manage multiple bank accounts and digital wallets in one place

📁 LOCAL EXPORT & BACKUP
Export your complete ledger anytime into CSV or Excel files, or create encrypted local backups with zero cloud lock-in.

PaisaPal is proudly ad-free, subscription-free, and dedicated to your financial sovereignty.
```

---

## 2. Google Play Policy Disclosures

### 2.1 Permissions Declaration Justification
- **`android.permission.BIND_NOTIFICATION_LISTENER_SERVICE`**:
  - *Core Functionality*: Required to capture real-time payment notifications (UPI debits, card swipes, net-banking credits) to automatically record financial transactions without manual entry.
  - *Data Safety Declaration*: All notification data processed locally in volatile memory; only extracted transaction metrics stored encrypted on-device. Zero data shared or transmitted off-device.

---

## 3. Visual Asset Specifications

- **App Icon**: 512x512 px 32-bit PNG (Dark navy background with bold emerald green and electric teal overlapping rupee glyph).
- **Feature Graphic**: 1024x500 px JPG/PNG (Headline: "Auto-Track Expenses Privately. 100% Offline. Zero SMS Access.").
- **Screenshots (Phone)**:
  1. *Home Dashboard*: Balance header, active monthly delta, review prompt banner, recent feed.
  2. *Review Inbox*: Swipe-to-confirm cards with confidence badges.
  3. *Analytics & Charts*: Category donut chart and monthly trend bar graph.
  4. *Multi-Account Hub*: Bank accounts, credit cards, and wallets at a glance.
  5. *Budgets & Goals*: Progress bars and threshold warnings.
  6. *Security & Privacy*: Keystore encryption and biometric unlock indicator.
