// NO-NETWORK
package com.cashbuddy.core.prob

/**
 * Immutable map of normalized brand tokens to standard categories.
 * O(1) lookup. Does NOT contain sender IDs.
 */
object MerchantMap {
    private val MAP: Map<String, String> = mapOf(
        // Food & Dining
        "swiggy" to "Food & Dining",
        "zomato" to "Food & Dining",
        "starbucks" to "Food & Dining",
        "mcdonald" to "Food & Dining",
        "mcdonalds" to "Food & Dining",
        "kfc" to "Food & Dining",
        "domino" to "Food & Dining",
        "dominos" to "Food & Dining",
        "pizza hut" to "Food & Dining",
        "burger king" to "Food & Dining",
        "chai point" to "Food & Dining",
        "chaipoint" to "Food & Dining",
        "chaayos" to "Food & Dining",
        "faasos" to "Food & Dining",
        "behrouz" to "Food & Dining",
        "eatfit" to "Food & Dining",
        "smartq" to "Food & Dining",
        "restaurant" to "Food & Dining",
        "cafe" to "Food & Dining",
        "dhaba" to "Food & Dining",
        "bistro" to "Food & Dining",
        "canteen" to "Food & Dining",
        "bakery" to "Food & Dining",

        // Groceries
        "blinkit" to "Groceries",
        "zepto" to "Groceries",
        "instamart" to "Groceries",
        "bigbasket" to "Groceries",
        "bb daily" to "Groceries",
        "dmart" to "Groceries",
        "spencer" to "Groceries",
        "kirana" to "Groceries",
        "supermarket" to "Groceries",

        // Transport
        "uber" to "Transportation",
        "ola" to "Transportation",
        "rapido" to "Transportation",
        "bmtc" to "Transportation",
        "ksrtc" to "Transportation",
        "irctc" to "Transportation",
        "metro" to "Transportation",
        "redbus" to "Transportation",
        "makemytrip" to "Transportation",
        "petrol" to "Transportation",
        "fuel" to "Transportation",
        "hpcl" to "Transportation",
        "bpcl" to "Transportation",
        "indianoil" to "Transportation",
        "shell" to "Transportation",
        "fastag" to "Transportation",

        // Shopping & Retail
        "amazon" to "Shopping & Retail",
        "flipkart" to "Shopping & Retail",
        "myntra" to "Shopping & Retail",
        "ajio" to "Shopping & Retail",
        "meesho" to "Shopping & Retail",
        "nykaa" to "Shopping & Retail",
        "croma" to "Shopping & Retail",
        "reliance digital" to "Shopping & Retail",
        "ikea" to "Shopping & Retail",
        "decathlon" to "Shopping & Retail",
        "zara" to "Shopping & Retail",
        "h&m" to "Shopping & Retail",
        "uniqlo" to "Shopping & Retail",

        // Bills & Utilities
        "bescom" to "Bills & Utilities",
        "tneb" to "Bills & Utilities",
        "airtel" to "Bills & Utilities",
        "jio" to "Bills & Utilities",
        "vi" to "Bills & Utilities",
        "vodafone" to "Bills & Utilities",
        "electricity" to "Bills & Utilities",
        "water" to "Bills & Utilities",
        "gas" to "Bills & Utilities",
        "broadband" to "Bills & Utilities",
        "recharge" to "Bills & Utilities",

        // Entertainment
        "bookmyshow" to "Entertainment & Recreation",
        "pvr" to "Entertainment & Recreation",
        "inox" to "Entertainment & Recreation",
        "netflix" to "Entertainment & Recreation",
        "spotify" to "Entertainment & Recreation",
        "hotstar" to "Entertainment & Recreation",
        "prime video" to "Entertainment & Recreation",
        "youtube" to "Entertainment & Recreation",

        // Healthcare
        "apollo" to "Healthcare & Medical",
        "1mg" to "Healthcare & Medical",
        "pharmeasy" to "Healthcare & Medical",
        "netmeds" to "Healthcare & Medical",
        "medplus" to "Healthcare & Medical",
        "practo" to "Healthcare & Medical",
        "pharmacy" to "Healthcare & Medical",
        "hospital" to "Healthcare & Medical",
        "clinic" to "Healthcare & Medical",

        // Investments
        "zerodha" to "Financial Services & Investments",
        "groww" to "Financial Services & Investments",
        "upstox" to "Financial Services & Investments",
        "kuvera" to "Financial Services & Investments",
        "smallcase" to "Financial Services & Investments"
    )

    fun lookup(normalizedMerchant: String): CategoryMatch {
        val norm = normalizedMerchant.trim()
        if (norm.isEmpty()) return CategoryMatch("Unknown", 0.50f, CategorySource.FALLBACK)

        // 1. Direct exact lookup
        MAP[norm]?.let {
            return CategoryMatch(it, 0.90f, CategorySource.MERCHANT_MAP)
        }

        // 2. Token lookup
        val tokens = norm.split(" ")
        for (token in tokens) {
            if (token.length >= 3) {
                MAP[token]?.let {
                    return CategoryMatch(it, 0.90f, CategorySource.MERCHANT_MAP)
                }
            }
        }

        return CategoryMatch("Unknown", 0.50f, CategorySource.FALLBACK)
    }

    data class CategoryMatch(val category: String, val confidence: Float, val source: CategorySource)
    enum class CategorySource { USER_RULE, MERCHANT_MAP, VPA_EXTRACT, FALLBACK }
}
