# Specification: Mathematical Formulas Integration (specs/math-integration/spec.md)

## User Stories

### US-1 — Merchant name normalization
When a bank SMS says `"SWIGGY_BLR_KORAMANGALA"` or `"swiggy@ybl"` or `"PAYTM*SWIGGY"`, the transaction must categorize as **Food & Dining**, not **Unknown**.

### US-2 — Anomaly alerts
When I spend significantly more than my historical median at a merchant, I want the transaction flagged so I can review it.

### US-3 — Confidence calibration
The confidence badge shown on each transaction must be a probability derived from the classifier's log-odds, not an arbitrary score.

---

## Acceptance Criteria

### AC-1 — Fuzzy merchant matching
Given `MerchantMap.lookup(normalizedMerchant)`:
- **Tier 1**: Exact `MAP[norm]` match → return with confidence `0.95`
- **Tier 2**: Token split, each token in `MAP` → return `0.90`
- **Tier 3**: Jaro-Winkler fuzzy match $\ge$ `FUZZY_THRESHOLD` → return `0.85 × similarity`
- **Tier 4**: Generic keyword tier (existing) → return `0.70`
- **Tier 5**: Fallback → `Unknown`, confidence `0.0`

**Test Cases**:
- `"SWIGGY_BLR_KORAMANGALA"` → Food & Dining
- `"PAYTM*SWIGGY"` → Food & Dining
- `"swiggy@ybl"` → Food & Dining
- `"ZOMATO"` → Food & Dining (exact, not fuzzy)
- `"ZOMATO"` → must NOT match Swiggy on fuzzy tier
- `"randomnonsense"` → Unknown

### AC-2 — Anomaly detection
Given `FraudDetector.checkAnomaly(merchant, amount, timestamp)`:
- **Window**: 90 days before `timestamp`
- **Minimum samples**: 10 historical `DEBIT` transactions at merchant
- If `samples < 10` → return `isAnomalous = false`
- If `MAD == 0.0` → return `isAnomalous = false`
- Else compute $M_i$, flag if $M_i > 3.5$

On flag: write `"anomaly_score=%.2f median=%.2f mad=%.2f"` to `transaction.notes`. No new notification channel. No policy change.

### AC-3 — Confidence calibration (verification only)
Read `ProbabilisticClassifier.kt`. Confirm the computation is:
$$\text{logit} = \ln\left(\frac{P_0}{1 - P_0}\right) + \sum \ln(\text{LR}_i)$$
$$p = \frac{1}{1 + \exp(-\text{logit})}$$

If it matches, report "verified, no change". If it differs, report the difference and DO NOT change the file.

---

## Non-Goals
- No new database tables
- No new SQLDelight migrations
- No new permissions
- No new dependencies
- No changes to `EvidenceExtractor`, `Regexes`, `LikelihoodRatios`, `PolicyEngine`, `DedupEngine`, or `ProbabilisticClassifier`
- No changes to extraction logic
