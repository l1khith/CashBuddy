# CashBuddy — Core Engine Low-Level Design (LLD)

## 1. Class & Method Signatures

All core engines reside in `shared/src/commonMain/kotlin/com/cashbuddy/core/`.

### 1.1. Core Models ([CoreModels.kt](file:///c:/Users/ailik/AndroidStudioProjects/CashBuddy/shared/src/commonMain/kotlin/com/cashbuddy/core/CoreModels.kt))
```kotlin
enum class TransactionType { DEBIT, CREDIT }

enum class Category {
    Food, Transport, Shopping, Bills, Entertainment,
    Health, Education, Housing, Insurance, Investments,
    Salary, Refund, Gift, Unknown
}

data class ParsedTransaction(
    val amount: Double,
    val transactionType: TransactionType,
    val category: Category,
    val merchant: String,
    val accountId: String?,
    val sourceApp: String,
    val rawText: String,
    val confidence: Float,
    val timestamp: Long
)

data class ScreenshotTransaction(
    val amount: Double,
    val transactionType: TransactionType,
    val merchant: String,
    val category: String,
    val utrOrRef: String?,
    val appName: String,
    val confidence: Float,
    val rawText: String
)
```

---

## 2. Low-Level Implementation Details

### 2.1. Deduplication Engine ([DedupEngine.kt](file:///c:/Users/ailik/AndroidStudioProjects/CashBuddy/shared/src/commonMain/kotlin/com/cashbuddy/core/DedupEngine.kt))
Deduplication prevents double-counting when a transaction produces both a push notification and a screenshot, or multiple notifications.

#### Algorithm:
1. **Amount Tolerance**: Absolute difference between `amount1` and `amount2` must be $\le 0.01$ (1 paisa).
2. **Time Window**: Absolute difference between timestamps must be $\le \text{windowMs}$ (default: 300,000 ms = 5 minutes).
3. **Merchant Normalization**:
   ```kotlin
   fun normalizeMerchant(s: String): String = s.trim()
       .lowercase()
       .replace("pvt ltd", "")
       .replace("private limited", "")
       .replace("ltd", "")
       .replace("india", "")
       .trim()
   ```
4. **Fuzzy Containment**: Returns true if normalized names match exactly, OR if both have length $\ge 3$ and one contains the other.

---

### 2.2. Category Engine ([CategoryEngine.kt](file:///c:/Users/ailik/AndroidStudioProjects/CashBuddy/shared/src/commonMain/kotlin/com/cashbuddy/core/CategoryEngine.kt))
Thread-safe, lock-free priority categorization using a copy-on-write immutable Map:

```kotlin
class CategoryEngine {
    @Volatile
    private var userRules: Map<String, String> = emptyMap()

    fun loadUserRules(rules: List<MerchantRuleEntry>) {
        val newMap = mutableMapOf<String, String>()
        for (entry in rules) {
            val key = entry.merchant.trim().lowercase()
            if (key.isNotEmpty()) newMap[key] = entry.category
        }
        userRules = newMap
    }

    fun learnCorrection(merchant: String, category: String) {
        val key = merchant.trim().lowercase()
        if (key.isEmpty() || category.isEmpty()) return
        userRules = userRules + (key to category)
    }

    fun getCategory(merchant: String): CategoryMatch {
        val mLower = merchant.trim().lowercase()
        if (mLower.isEmpty()) return CategoryMatch("Unknown", 0.50f, "Fallback")

        val currentRules = userRules
        currentRules[mLower]?.let { cat ->
            return CategoryMatch(cat, 1.0f, "UserRule")
        }
        if (mLower.length >= 3) {
            for ((pattern, cat) in currentRules) {
                if (pattern.length >= 3 && (mLower.contains(pattern) || pattern.contains(mLower))) {
                    return CategoryMatch(cat, 1.0f, "UserRule")
                }
            }
        }

        for ((keyword, cat) in keywordMap) {
            if (mLower.contains(keyword)) {
                return CategoryMatch(cat, 0.90f, "KeywordMap")
            }
        }

        return CategoryMatch("Unknown", 0.50f, "Fallback")
    }
}
```

---

### 2.3. Screenshot Parser Engine ([ScreenshotParserEngine.kt](file:///c:/Users/ailik/AndroidStudioProjects/CashBuddy/shared/src/commonMain/kotlin/com/cashbuddy/core/ScreenshotParserEngine.kt))

#### Multi-Strategy Amount Extraction:
1. **Symbol Prefix**: `(?:₹|Rs\.?|INR)\s*([0-9]{1,3}(?:,[0-9]{2,3})*(?:\.[0-9]{1,2})?|[0-9]+(?:\.[0-9]{1,2})?)`
2. **Transit & Confirmation Patterns**: `(?:payment\s+of|paid|sent)\s+([0-9.]+)\s+(?:completed|successful|done)`
3. **Standalone Line with Context**: For receipts that display amounts without symbols (e.g., BMTC bus tickets showing `18` on a line with `Payment completed` below).

#### Multi-Strategy Merchant Extraction:
1. **Prefix Anchors**: Extracts the line following `Paid to`, `To:`, `Payment to`, `Received from`.
2. **VPA Handles**: Identifies UPI VPAs (`handle@bank`) and cleans the username portion.
3. **Transit Brand Matchers**: Detects known transit authorities (`BMTC`, `KSRTC`, `Metro`).

---

### 2.4. Fraud & Velocity Detector ([FraudDetector.kt](file:///c:/Users/ailik/AndroidStudioProjects/CashBuddy/shared/src/commonMain/kotlin/com/cashbuddy/core/FraudDetector.kt))
- Maintains an in-memory sliding list of timestamps per package name.
- Automatically evicts timestamps older than 60 seconds (`cutoff = now - 60_000L`).
- If more than 5 events occur in the 60-second window, `checkVelocity` returns `false`, penalizing the transaction confidence score by -0.30 to prevent rapid duplicate bursts from auto-confirming.
