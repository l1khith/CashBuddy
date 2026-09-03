# PaisaPal Privacy Policy

**Effective Date:** September 3, 2026  
**Product:** PaisaPal — Intelligent Offline Finance Tracker  

---

## 1. Our Privacy Guarantee: 100% On-Device & Zero Network

PaisaPal was engineered from the ground up around a fundamental principle: **Your financial data belongs exclusively to you and should never leave your personal device.**

- **Zero Cloud Servers**: PaisaPal does not maintain, communicate with, or operate any remote servers or APIs.
- **Zero Internet Access**: PaisaPal does not declare or request Android's `android.permission.INTERNET` permission in production. It is mathematically and architecturally impossible for the application to transmit your data across the internet.
- **Zero SMS Access**: PaisaPal does not request `android.permission.READ_SMS` or `android.permission.RECEIVE_SMS`. Your personal, financial, and confidential text messages are never accessed.
- **Zero Third-Party Telemetry**: There are no analytics SDKs, advertising trackers, crash-reporting clouds, or user profiling libraries included in this application.

---

## 2. Information Handled & How It Is Used

### 2.1 Notification Access (`BIND_NOTIFICATION_LISTENER_SERVICE`)
To automatically detect and track transactions, PaisaPal requests access to incoming device notifications.
- **Strict Package Filtering**: PaisaPal inspects notifications originated solely from a curated allowlist of known banking, UPI, and fintech applications (such as Google Pay, PhonePe, Paytm, and approved Indian scheduled commercial banks).
- **Discarding of Sensitive Communications**: Notifications containing one-time passwords (OTPs), authentication codes, personal chats, and marketing promotions are detected and discarded instantly from volatile memory.
- **Redaction & Local Storage**: Only extracted numerical amounts, detected merchant identifiers, account references (last 4 digits only), and transaction dates are persisted locally.

### 2.2 Local Database Encryption
All persistent transaction and account records are stored inside a hardware-encrypted SQLite database powered by **SQLCipher (AES-256 GCM)**. The encryption key is derived using keys managed within the **Android Keystore System**, protected by the device's hardware-backed Trusted Execution Environment (TEE) or StrongBox.

---

## 3. Data Export & Backup

When you choose to export your transaction records (CSV or Excel) or create a database backup:
- The resulting files are saved strictly to the local storage location you designate via Android's system document picker.
- You maintain complete ownership and control over these files.

---

## 4. Contact & Auditing

Because PaisaPal has no access to your network or personal identity, we do not collect contact information. Users are welcome to review our open architecture and permission manifests directly on GitHub to verify our offline integrity.
