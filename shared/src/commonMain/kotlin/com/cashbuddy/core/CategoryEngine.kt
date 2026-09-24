package com.cashbuddy.core

import kotlin.concurrent.Volatile

/**
 * 3-Tier Categorization Engine for CashBuddy:
 * 1. User learned rules (Confidence: 1.0, Source: "UserRule")
 * 2. Curated Indian merchant keyword dictionary (Confidence: 0.90, Source: "KeywordMap")
 * 3. Fallback / Unknown (Confidence: 0.50, Source: "Fallback")
 */
class CategoryEngine {

    @Volatile
    private var userRules: Map<String, String> = emptyMap()

    private val keywordMap: List<Pair<String, String>> = listOf(
        // 1. Food & Dining
        "swiggy" to "Food & Dining",
        "zomato" to "Food & Dining",
        "mcdonald" to "Food & Dining",
        "kfc" to "Food & Dining",
        "burger king" to "Food & Dining",
        "domino" to "Food & Dining",
        "pizza hut" to "Food & Dining",
        "starbucks" to "Food & Dining",
        "subway" to "Food & Dining",
        "barbeque nation" to "Food & Dining",
        "haldiram" to "Food & Dining",
        "chai point" to "Food & Dining",
        "chaayos" to "Food & Dining",
        "faasos" to "Food & Dining",
        "behrouz" to "Food & Dining",
        "eatfit" to "Food & Dining",
        "restaurant" to "Food & Dining",
        "cafe" to "Food & Dining",
        "dhaba" to "Food & Dining",
        "bistro" to "Food & Dining",
        "canteen" to "Food & Dining",
        "kitchen" to "Food & Dining",
        "biryani" to "Food & Dining",
        "sweets" to "Food & Dining",
        "bakery" to "Food & Dining",
        "dining" to "Food & Dining",
        "tea" to "Food & Dining",
        "coffee" to "Food & Dining",

        // 2. Groceries
        "blinkit" to "Groceries",
        "zepto" to "Groceries",
        "instamart" to "Groceries",
        "bigbasket" to "Groceries",
        "bb daily" to "Groceries",
        "dmart" to "Groceries",
        "spencer" to "Groceries",
        "nature's basket" to "Groceries",
        "supermarket" to "Groceries",
        "provision" to "Groceries",
        "kirana" to "Groceries",
        "vegetables" to "Groceries",
        "fruits" to "Groceries",
        "dairy" to "Groceries",
        "milk" to "Groceries",

        // 3. Transportation
        "uber" to "Transportation",
        "ola" to "Transportation",
        "rapido" to "Transportation",
        "bmtc" to "Transportation",
        "ksrtc" to "Transportation",
        "msrtc" to "Transportation",
        "irctc" to "Transportation",
        "metro" to "Transportation",
        "redbus" to "Transportation",
        "makemytrip" to "Transportation",
        "yatra" to "Transportation",
        "goibibo" to "Transportation",
        "cleartrip" to "Transportation",
        "indigo" to "Transportation",
        "air india" to "Transportation",
        "vistara" to "Transportation",
        "spicejet" to "Transportation",
        "akasa" to "Transportation",
        "petrol" to "Transportation",
        "fuel" to "Transportation",
        "hpcl" to "Transportation",
        "bpcl" to "Transportation",
        "indianoil" to "Transportation",
        "shell" to "Transportation",
        "fastag" to "Transportation",
        "toll" to "Transportation",
        "parking" to "Transportation",
        "auto" to "Transportation",
        "cab" to "Transportation",
        "taxi" to "Transportation",

        // 4. Shopping & Retail
        "amazon" to "Shopping & Retail",
        "flipkart" to "Shopping & Retail",
        "myntra" to "Shopping & Retail",
        "meesho" to "Shopping & Retail",
        "ajio" to "Shopping & Retail",
        "nykaa" to "Shopping & Retail",
        "tata cliq" to "Shopping & Retail",
        "reliancedigital" to "Shopping & Retail",
        "reliance" to "Shopping & Retail",
        "croma" to "Shopping & Retail",
        "ikea" to "Shopping & Retail",
        "decathlon" to "Shopping & Retail",
        "zara" to "Shopping & Retail",
        "h&m" to "Shopping & Retail",
        "uniqlo" to "Shopping & Retail",
        "westside" to "Shopping & Retail",
        "lifestyle" to "Shopping & Retail",
        "shoppers stop" to "Shopping & Retail",
        "mall" to "Shopping & Retail",
        "store" to "Shopping & Retail",
        "retail" to "Shopping & Retail",

        // 5. Bills & Utilities
        "bescom" to "Bills & Utilities",
        "tneb" to "Bills & Utilities",
        "mahadiscom" to "Bills & Utilities",
        "cesc" to "Bills & Utilities",
        "bsnl" to "Bills & Utilities",
        "airtel" to "Bills & Utilities",
        "jio" to "Bills & Utilities",
        "vi " to "Bills & Utilities",
        "vodafone" to "Bills & Utilities",
        "tata play" to "Bills & Utilities",
        "dish tv" to "Bills & Utilities",
        "sun direct" to "Bills & Utilities",
        "adani gas" to "Bills & Utilities",
        "indraprastha gas" to "Bills & Utilities",
        "mahanagar gas" to "Bills & Utilities",
        "electricity" to "Bills & Utilities",
        "power" to "Bills & Utilities",
        "water" to "Bills & Utilities",
        "piped gas" to "Bills & Utilities",
        "broadband" to "Bills & Utilities",
        "wifi" to "Bills & Utilities",
        "lpg" to "Bills & Utilities",
        "cylinder" to "Bills & Utilities",
        "billdesk" to "Bills & Utilities",
        "recharge" to "Bills & Utilities",

        // 6. Entertainment & Recreation
        "bookmyshow" to "Entertainment & Recreation",
        "pvr" to "Entertainment & Recreation",
        "inox" to "Entertainment & Recreation",
        "cinepolis" to "Entertainment & Recreation",
        "netflix" to "Entertainment & Recreation",
        "prime video" to "Entertainment & Recreation",
        "disney" to "Entertainment & Recreation",
        "hotstar" to "Entertainment & Recreation",
        "spotify" to "Entertainment & Recreation",
        "apple music" to "Entertainment & Recreation",
        "youtube" to "Entertainment & Recreation",
        "gaana" to "Entertainment & Recreation",
        "wynk" to "Entertainment & Recreation",
        "playstation" to "Entertainment & Recreation",
        "steam" to "Entertainment & Recreation",
        "gaming" to "Entertainment & Recreation",
        "cult.fit" to "Entertainment & Recreation",
        "gym" to "Entertainment & Recreation",
        "fitness" to "Entertainment & Recreation",
        "movie" to "Entertainment & Recreation",
        "cinema" to "Entertainment & Recreation",

        // 7. Healthcare & Medical
        "apollo" to "Healthcare & Medical",
        "1mg" to "Healthcare & Medical",
        "pharmeasy" to "Healthcare & Medical",
        "netmeds" to "Healthcare & Medical",
        "medplus" to "Healthcare & Medical",
        "practo" to "Healthcare & Medical",
        "max healthcare" to "Healthcare & Medical",
        "fortis" to "Healthcare & Medical",
        "dr lal" to "Healthcare & Medical",
        "metropolis" to "Healthcare & Medical",
        "clinic" to "Healthcare & Medical",
        "hospital" to "Healthcare & Medical",
        "pharmacy" to "Healthcare & Medical",
        "chemist" to "Healthcare & Medical",
        "diagnostic" to "Healthcare & Medical",
        "dental" to "Healthcare & Medical",
        "lenskart" to "Healthcare & Medical",

        // 8. Financial Services & Investments
        "zerodha" to "Financial Services & Investments",
        "groww" to "Financial Services & Investments",
        "upstox" to "Financial Services & Investments",
        "angel one" to "Financial Services & Investments",
        "indmoney" to "Financial Services & Investments",
        "kuvera" to "Financial Services & Investments",
        "smallcase" to "Financial Services & Investments",
        "coin" to "Financial Services & Investments",
        "etmoney" to "Financial Services & Investments",
        "lic" to "Financial Services & Investments",
        "hdfc life" to "Financial Services & Investments",
        "icici pru" to "Financial Services & Investments",
        "sbi life" to "Financial Services & Investments",
        "max life" to "Financial Services & Investments",
        "star health" to "Financial Services & Investments",
        "policybazaar" to "Financial Services & Investments",
        "insurance" to "Financial Services & Investments",
        "mutual fund" to "Financial Services & Investments",
        "brokerage" to "Financial Services & Investments",
        "securities" to "Financial Services & Investments",

        // 9. Education
        "byju" to "Education",
        "unacademy" to "Education",
        "vedantu" to "Education",
        "upgrad" to "Education",
        "udemy" to "Education",
        "coursera" to "Education",
        "school" to "Education",
        "college" to "Education",
        "university" to "Education",
        "institute" to "Education",
        "tuition" to "Education",
        "academy" to "Education",
        "classes" to "Education",
        "course" to "Education",
        "exam" to "Education"
    )

    /**
     * Load user-learned rules from local database into memory on startup
     */
    fun loadUserRules(rules: List<MerchantRuleEntry>) {
        val newMap = mutableMapOf<String, String>()
        for (entry in rules) {
            val key = entry.merchant.trim().lowercase()
            if (key.isNotEmpty()) {
                newMap[key] = entry.category
            }
        }
        userRules = newMap
    }

    /**
     * Learn a new correction immediately in memory (O(1) update)
     */
    fun learnCorrection(merchant: String, category: String) {
        val key = merchant.trim().lowercase()
        if (key.isEmpty() || category.isEmpty()) return
        userRules = userRules + (key to category)
    }

    /**
     * Priority-based category lookup:
     * 1. User learned rules (Confidence: 1.0, Source: "UserRule")
     * 2. Curated keyword map (Confidence: 0.90, Source: "KeywordMap")
     * 3. Fallback / Unknown (Confidence: 0.50, Source: "Fallback")
     */
    fun getCategory(merchant: String): CategoryMatch {
        val mLower = merchant.trim().lowercase()
        if (mLower.isEmpty()) {
            return CategoryMatch(category = "Unknown", confidence = 0.50f, source = "Fallback")
        }

        // Tier 1: User rules (exact or bidirectional substring match)
        val currentRules = userRules
        currentRules[mLower]?.let { cat ->
            return CategoryMatch(category = cat, confidence = 1.0f, source = "UserRule")
        }
        if (mLower.length >= 3) {
            for ((pattern, cat) in currentRules) {
                if (pattern.length >= 3 && (mLower.contains(pattern) || pattern.contains(mLower))) {
                    return CategoryMatch(category = cat, confidence = 1.0f, source = "UserRule")
                }
            }
        }

        // Tier 2: Curated Indian keyword map
        for ((keyword, cat) in keywordMap) {
            if (mLower.contains(keyword)) {
                return CategoryMatch(category = cat, confidence = 0.90f, source = "KeywordMap")
            }
        }

        // Tier 3: Fallback
        return CategoryMatch(category = "Unknown", confidence = 0.50f, source = "Fallback")
    }
}
