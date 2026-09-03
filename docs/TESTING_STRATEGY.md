# PaisaPal KMP — Testing Strategy & Verification Plan

## 1. Testing Pyramid Overview

PaisaPal enforces a rigorous, multi-layered automated and manual testing strategy to ensure zero data corruption, parser infallibility, and rock-solid privacy:

```
                  ┌─────────────────┐
                  │  End-to-End /   │
                  │   Device Tests  │ (10%)
                  ├─────────────────┤
                  │   Integration   │
                  │  (DB + Service) │ (25%)
                  ├─────────────────┤
                  │   Unit Tests    │
                  │ (Parsers, MVI,  │ (65%)
                  │  Math, Domain)  │
                  └─────────────────┘
```

---

## 2. Unit Testing Suite

### 2.1 Parser & Extraction Engine Suite (`NotificationParserTest`)
Tests the parser pipeline against a benchmark suite of 50+ real-world notification string variations:
1. **Google Pay**: `"Paid ₹450.00 to Swiggy via GPay"` $\to$ Amount: 450.0, Type: DEBIT, Merchant: Swiggy, Cat: Food.
2. **PhonePe**: `"Paid Rs. 1,299 to Uber India"` $\to$ Amount: 1299.0, Type: DEBIT, Merchant: Uber India, Cat: Transportation.
3. **Paytm**: `"Payment of ₹55.00 to Chai Point successful"` $\to$ Amount: 55.0, Type: DEBIT, Merchant: Chai Point.
4. **HDFC Alert**: `"Alert: INR 3,499.00 debited from A/C **1234 on 03-SEP at AMAZON"` $\to$ Amount: 3499.0, Account: 1234.
5. **ICICI Alert**: `"Your A/C 9876 is credited with INR 50,000.00 on 01-SEP by SALARY"` $\to$ Amount: 50000.0, Type: CREDIT.
6. **OTP Negative Case**: `"Your OTP for HDFC Bank NetBanking is 482910. Do not share with anyone."` $\to$ Discarded.
7. **Promo Negative Case**: `"Congratulations! You have won ₹100 cashback voucher on your next order."` $\to$ Discarded.

### 2.2 Confidence Calculator Suite (`ConfidenceCalculatorTest`)
- All 7 signals present: Confidence = 1.0f.
- Only Amount and Debit signal present: Confidence = 0.60f $\to$ Status = PENDING.
- High confidence ($0.95$) but amount = ₹15,000 $\to$ Status = PENDING (exceeds ₹10,000 auto-confirm threshold).
- High confidence ($0.95$) and amount = ₹450 $\to$ Status = CONFIRMED.

### 2.3 MVI Reducer Test (`ReviewReducerTest`)
Pure function testing ensuring predictable state immutability:
- `ReviewIntent.ConfirmTransaction` $\to$ Removes item from pending list, decrements unreviewed counter, computes new review stats.
- `ReviewIntent.RejectTransaction` $\to$ Removes item, records rejection audit state.
- `ReviewIntent.ModifyCategory` $\to$ Updates target transaction category without changing other properties.

---

## 3. Database Integration Testing (`SqlDelightTest`)

Using in-memory SQLDelight driver (`JdbcSqliteDriver` or in-memory `AndroidSqliteDriver`):
- Verify account debit trigger updates account balance accurately.
- Verify account credit trigger updates account balance accurately.
- Verify foreign key constraints (cascades or restrictions on deleting categories with linked transactions).
- Verify date range aggregation queries (`getMonthlySummary`, `getCategoryBreakdown`).

---

## 4. Security & Biometrics Verification

- **Keystore Encryption Test**: Verify data written to database with an encrypted driver cannot be read by an unencrypted standard SQLite driver (fails with `file is not a database`).
- **Root Detection Test**: Emulate dangerous props and verify detection flag sets security mode.
- **Biometric Challenge Test**: Verify screen interaction is blocked until `BiometricPrompt.AuthenticationCallback.onAuthenticationSucceeded()` triggers.

---

## 5. Execution Commands

```bash
# Run shared common unit tests (Parsers, MVI Reducers, Confidence Math)
./gradlew :shared:testDebugUnitTest

# Run Android host unit tests
./gradlew :shared:testAndroidHostTest

# Run lint and assemble debug APK
./gradlew :androidApp:assembleDebug
```
