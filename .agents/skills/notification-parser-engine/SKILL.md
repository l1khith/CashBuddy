---
name: notification-parser-engine
description: >-
  Use this skill when implementing, modifying, or testing the 5-layer notification parser engine in pure Kotlin,
  including source allowlist validation (50+ banking/UPI apps), OTP/promo filtering, regex entity extraction,
  confidence scoring, and fraud detection.
---

# Notification Parser Engine Procedures

## Overview
The Notification Parser Engine operates on Android's `NotificationListenerService`. It converts raw text from incoming payment and bank alerts into structured `Transaction` domain entities with zero network access and zero SMS reading.

## 5-Layer Pipeline Execution Order

```
[Layer 1: Source Validation]
       ↓ (Package is in 50+ curated allowlist)
[Layer 2: Content Classification]
       ↓ (Not an OTP, Not Promotional, Contains Transaction Signal)
[Layer 3: Entity Extraction]
       ↓ (Extracts Amount, Debit/Credit, Merchant, Account Last 4, Timestamp)
[Layer 4: Categorization & Confidence Scoring]
       ↓ (Applies Rules -> Keywords -> Heuristics, computes score 0.0 to 1.0)
[Layer 5: Fraud & Anomaly Detection]
       ↓ (Velocity limit, 5-min duplicate deduplication, outlier threshold)
[Persistence & Trigger]
```

## Critical Implementation Rules

1. **Strictly Pure Kotlin**:
   - Do not import Android regex or framework classes inside parser logic.
   - Use Kotlin's standard `kotlin.text.Regex`.
   - Never use machine learning runtimes (TFLite, ONNX) or native binaries.

2. **Regex Patterns**:
   - Currency & Amount:
     `AMOUNT_REGEX = Regex("""(?:(?:₹|Rs\.?|INR)\s*([0-9]{1,3}(?:,[0-9]{3})*(?:\.[0-9]{1,2})?|[0-9]+(?:\.[0-9]{1,2})?))|(?:([0-9]{1,3}(?:,[0-9]{3})*(?:\.[0-9]{1,2})?|[0-9]+(?:\.[0-9]{1,2})?)\s*(?:₹|Rs\.?|INR))""", RegexOption.IGNORE_CASE)`
   - Debit: `\b(debited|paid|spent|sent|transferred\s+to|purchase\s+at|withdrawn)\b`
   - Credit: `\b(credited|received|added|refunded|deposited|cashback)\b`
   - Discard Signals (OTP & Promo):
     `\b(otp|one time password|verification code|secret code|do not share|win|cashback up to|congratulations|deal|discount|apply now)\b`

3. **Confidence Scoring (0.0f - 1.0f)**:
   - Core signals: Amount present (0.30), Type detected (0.30).
   - Supporting signals: Merchant present (0.10), Account present (0.05), Source known (0.10).
   - Categorization signals: Merchant in user rules (0.10), Keyword match (0.05).
   - Total capped at `1.0f`.

4. **Auto-Confirm Policy**:
   - Transaction status is `CONFIRMED` only when: `confidence >= 0.85f && amount < autoConfirmThreshold` (default ₹10,000).
   - All other transactions ($\text{confidence} < 0.85$ OR $\text{amount} \ge ₹10,000$) MUST be marked `PENDING` for user review.

## Verification Checklist
- Run test suite with 50+ real-world notification sample variations.
- Verify OTP and marketing messages produce `null` output and trigger zero database writes.
- Verify identical notifications arriving within $\pm 5$ minutes are deduplicated.
