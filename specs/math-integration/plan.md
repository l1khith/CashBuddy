# Plan: Mathematical Formulas Integration (specs/math-integration/plan.md)

──────────────────────────────────────────────────────────────────
COMPONENT 1 — JARO-WINKLER SIMILARITY
──────────────────────────────────────────────────────────────────

### Purpose
Resolve merchant name variants that exact lookup and token lookup miss. `"SWIGGY_BLR_KORAMANGALA"` and `"swiggy@ybl"` both describe the merchant Swiggy, but neither matches `MAP["swiggy"]` on exact or token tiers.

### Mathematical Definition
Let $s_1, s_2$ be two strings.

- $m$ = number of matching characters.
  A character at position $i$ in $s_1$ matches the character at position $j$ in $s_2$ if $s_1[i] == s_2[j]$ and:
  $$|i - j| \le \left\lfloor \frac{\max(|s_1|, |s_2|)}{2} \right\rfloor - 1$$
- $t$ = number of transpositions / 2.
  Count of positions where matched characters are in a different order. Divide by 2 to avoid double counting.
- Jaro similarity:
  $$d_j = \frac{1}{3} \cdot \left( \frac{m}{|s_1|} + \frac{m}{|s_2|} + \frac{m - t}{m} \right)$$
- Common prefix length $L$, capped at 4 characters.
- Winkler modification with $P = 0.1$:
  $$d_w = d_j + P \cdot L \cdot (1 - d_j)$$
  Result is bounded in $[0.0, 1.0]$.

### Reference Values (Test Oracles from Winkler's 1990 paper)
- `"MARTHA"` vs `"MARHTA"` $\rightarrow 0.9611$
- `"DWAYNE"` vs `"DUANE"` $\rightarrow 0.8400$
- `"DIXON"` vs `"DICKSONX"` $\rightarrow 0.8133$

### File to Create
`shared/src/commonMain/kotlin/com/cashbuddy/core/prob/JaroWinkler.kt`

### Public API
```kotlin
object JaroWinkler {
    fun similarity(a: String, b: String): Double
}
```

### Implementation Rules
- Return `1.0` immediately if `a == b`
- Return `0.0` immediately if `a.isEmpty() || b.isEmpty()`
- Case-sensitive; caller normalizes input before calling
- Pure function; no caching, no I/O, no side effects
- `// NO-NETWORK` header at top of file
- KDoc on the object and the function

──────────────────────────────────────────────────────────────────
COMPONENT 2 — MERCHANT MAP FUZZY TIER
──────────────────────────────────────────────────────────────────

### Purpose
Wire `JaroWinkler` into `MerchantMap.lookup()` as a third tier, between token matching and generic keyword matching.

### Current Lookup Order (Verified by Audit)
1. Exact `MAP[norm]`
2. Token split on space; each token in `MAP`
3. Generic keyword tier
4. Fallback $\rightarrow$ `Unknown`

### New Lookup Order
1. Exact `MAP[norm]` (confidence `0.95`)
2. Token split on space; each token in `MAP` (confidence `0.90`)
3. **NEW**: Fuzzy — `JaroWinkler.similarity(norm, key)` for each key in `MAP` (including token components if compound); return best match if $\ge$ `FUZZY_THRESHOLD` (confidence $0.85 \times \text{similarity}$)
4. Generic keyword tier (confidence `0.70`)
5. Fallback $\rightarrow$ `Unknown` (confidence `0.0`)

### Threshold Constant
`private const val FUZZY_THRESHOLD = 0.88`
Declared inside the `MerchantMap` object.

### Confidence Mapping for Fuzzy Tier
$$\text{confidence} = 0.85\text{f} \times \text{similarity\_score}$$
- Similarity $0.88 \rightarrow$ confidence $0.748$
- Similarity $0.95 \rightarrow$ confidence $0.808$

### File to Modify
`shared/src/commonMain/kotlin/com/cashbuddy/core/prob/MerchantMap.kt`

> [!IMPORTANT]
> Do not create `DynamicMerchantResolver.kt`. It does not exist and must not exist. Fuzzy logic lives exclusively in `MerchantMap`.

──────────────────────────────────────────────────────────────────
COMPONENT 3 — MODIFIED Z-SCORE (MAD)
──────────────────────────────────────────────────────────────────

### Purpose
Flag transactions whose amount is a statistical outlier for that merchant, using a median-based measure that is immune to single large historical values (e.g. rent, insurance).

### Mathematical Definition
Let $X = \{x_1, x_2, \dots, x_n\}$ be the amounts of all `DEBIT` transactions at merchant $M$ in the 90 days before the current transaction's timestamp.

- $\text{median}(X)$ = the middle value of $X$ when sorted (average of two middles if $n$ is even).
- Absolute deviations:
  $$D = \{ |x_i - \text{median}(X)| : x_i \in X \}$$
- Median Absolute Deviation:
  $$\text{MAD} = \text{median}(D)$$
- Modified Z-Score for the new transaction amount $x_{\text{new}}$:
  $$M_i = 0.6745 \cdot \frac{x_{\text{new}} - \text{median}(X)}{\text{MAD}}$$
  The constant $0.6745$ is the $0.75$ quantile of the standard normal distribution $\mathcal{N}(0, 1)$, normalizing MAD to be a consistent estimator of standard deviation $\sigma$.

### Flag Condition
$$\text{isAnomalous} = (M_i > 3.5) \land (n \ge 10)$$

### Edge Cases
- $n < 10 \rightarrow \text{isAnomalous} = \text{false}$, $\text{sampleSize} = n$
- $\text{MAD} == 0.0 \rightarrow \text{isAnomalous} = \text{false}$ (all historical amounts identical; no anomaly possible)
- $x_{\text{new}} == \text{median}(X) \rightarrow M_i = 0$, not anomalous

### File to Modify
`shared/src/commonMain/kotlin/com/cashbuddy/core/FraudDetector.kt`

### Public API
```kotlin
class FraudDetector(
    private val transactionRepository: TransactionRepository
) {
    suspend fun checkAnomaly(
        merchant: String,
        amount: Double,
        timestamp: Long
    ): AnomalyResult
}

data class AnomalyResult(
    val isAnomalous: Boolean,
    val score: Double,        // M_i
    val median: Double,       // median(X)
    val mad: Double,          // MAD
    val sampleSize: Int       // n
)
```

### Repository Requirement
Add method to `TransactionRepository` and implement in `TransactionRepositoryImpl.kt`:
```kotlin
suspend fun getAmountsAtMerchant(
    merchant: String,
    fromTimestamp: Long,
    toTimestamp: Long
): List<Double>
```
Backed by SQLDelight query filtering on `type = 'DEBIT'`, `status IN ('CONFIRMED', 'PENDING')`, `is_merged = 0`, `merchant = ?`, and the 90-day time range.

──────────────────────────────────────────────────────────────────
INTEGRATION POINT
──────────────────────────────────────────────────────────────────

### File to Modify
`shared/src/commonMain/kotlin/com/cashbuddy/core/prob/MessagePipeline.kt`

### Location
Inside `ingest()`, in the `Decision.Insert` branch, after the transaction is inserted and before the budget alert check.

### Code Shape
```kotlin
val txId = transactionRepo.insert(tx)
recentStateRepository?.record(tx)

val anomaly = fraudDetector.checkAnomaly(
    merchant  = tx.merchant,
    amount    = tx.amount,
    timestamp = tx.timestamp
)
if (anomaly.isAnomalous) {
    val note = "anomaly_score=%.2f median=%.2f mad=%.2f"
        .format(anomaly.score, anomaly.median, anomaly.mad)
    transactionRepo.updateNotes(txId, note)
}

budgetAlertScheduler?.checkAndAlert(raw.timestamp)
```
Add `updateNotes(id: Long, notes: String)` to `TransactionRepository` and `TransactionRepositoryImpl`.

──────────────────────────────────────────────────────────────────
THRESHOLD SWEEP PROCEDURE
──────────────────────────────────────────────────────────────────

The Jaro-Winkler threshold `0.88` is evaluated before finalizing T2:
1. Temporarily change `FUZZY_THRESHOLD` to each of: `0.75`, `0.80`, `0.85`, `0.88`, `0.90`, `0.95`.
2. After each change, run `AccuracyHarnessTest`.
3. Record precision, recall, and F1 for each threshold.
4. Select the threshold with the highest F1 that does not regress precision below 100%.
5. Hardcode the selected threshold and re-run the harness to confirm.

──────────────────────────────────────────────────────────────────
TESTING PLAN
──────────────────────────────────────────────────────────────────

### Test Files to Create:
1. `shared/src/commonTest/kotlin/com/cashbuddy/core/prob/JaroWinklerTest.kt`:
   - `"MARTHA"` vs `"MARHTA"` $\rightarrow 0.9611 \pm 0.001$
   - `"DWAYNE"` vs `"DUANE"` $\rightarrow 0.8400 \pm 0.001$
   - `"DIXON"` vs `"DICKSONX"` $\rightarrow 0.8133 \pm 0.001$
   - `"SWIGGY"` vs `"SWIGGY"` $\rightarrow 1.0$
   - `""` vs `"SWIGGY"` $\rightarrow 0.0$
   - `"SWIGGY"` vs `""` $\rightarrow 0.0$
   - `"SWIGGY"` vs `"SWIGGY BLR"` $\rightarrow > 0.88$

2. `shared/src/commonTest/kotlin/com/cashbuddy/core/prob/MerchantFuzzyTest.kt`:
   - `"SWIGGY_BLR_KORAMANGALA"` $\rightarrow$ Food & Dining
   - `"PAYTM*SWIGGY"` $\rightarrow$ Food & Dining
   - `"swiggy@ybl"` $\rightarrow$ Food & Dining
   - `"ZOMATO"` $\rightarrow$ Food & Dining (exact tier, not fuzzy)
   - `"ZOMATO"` $\rightarrow$ resolves differently from Swiggy
   - `"asdfghjkl"` $\rightarrow$ Unknown

3. `shared/src/commonTest/kotlin/com/cashbuddy/core/FraudDetectorTest.kt`:
   - 5 samples around ₹300, new ₹3000 $\rightarrow$ not anomalous ($n < 10$)
   - 12 samples around ₹300, new ₹3000 $\rightarrow$ anomalous
   - 12 samples around ₹300, new ₹320 $\rightarrow$ not anomalous
   - 12 samples all equal to ₹300, new ₹500 $\rightarrow$ not anomalous ($\text{MAD} = 0$)

4. `AccuracyHarnessTest` (existing):
   - Precision = 100%, Recall = 100% verified.
