// NO-NETWORK
package com.cashbuddy.core

import com.cashbuddy.core.prob.ConcurrentHashMap
import com.cashbuddy.core.prob.MerchantMap
import com.cashbuddy.domain.repository.UserRuleRepository

/**
 * Categorization Engine for CashBuddy:
 * 1. User learned rules (Confidence: 1.0, Source: "UserRule")
 * 2. Curated Indian merchant keyword dictionary (Confidence: 0.90, Source: "KeywordMap")
 * 3. Fallback / Unknown (Confidence: 0.50, Source: "Fallback")
 */
class CategoryEngine(
    private val userRuleRepository: UserRuleRepository? = null
) {
    private val userRules = ConcurrentHashMap<String, String>()
    private val cache = ConcurrentHashMap<String, CategoryMatch>()

    suspend fun loadRules() {
        val repo = userRuleRepository ?: return
        userRules.clear()
        cache.clear()
        repo.getAll().forEach {
            val norm = normalize(it.merchantNormalized)
            if (norm.isNotEmpty()) {
                userRules[norm] = it.category
            }
        }
    }

    fun loadUserRules(rules: List<MerchantRuleEntry>) {
        userRules.clear()
        cache.clear()
        rules.forEach {
            val norm = normalize(it.merchant)
            if (norm.isNotEmpty()) {
                userRules[norm] = it.category
            }
        }
    }

    fun learn(merchant: String, category: String) {
        val norm = normalize(merchant)
        if (norm.isNotEmpty()) {
            userRules[norm] = category
            cache.clear()
        }
    }

    fun learnCorrection(merchant: String, category: String) = learn(merchant, category)

    fun getCategory(rawMerchant: String?): CategoryMatch {
        if (rawMerchant.isNullOrBlank()) {
            return CategoryMatch(category = "Unknown", confidence = 0.50f, source = "Fallback")
        }
        val m = normalize(rawMerchant)
        cache[m]?.let { return it }

        val match = resolveCategory(m)
        cache[m] = match
        return match
    }

    private fun resolveCategory(m: String): CategoryMatch {
        // 1. Direct user rule lookup
        userRules[m]?.let {
            return CategoryMatch(category = it, confidence = 1.0f, source = "UserRule")
        }

        // 2. Tokenized subphrase match for user rules (e.g. "payment to chaiwala uncle" -> "chaiwala uncle")
        val tokens = m.split(" ").filter { it.isNotEmpty() }
        for (len in tokens.size downTo 1) {
            for (start in 0..(tokens.size - len)) {
                val phrase = tokens.subList(start, start + len).joinToString(" ")
                userRules[phrase]?.let {
                    return CategoryMatch(category = it, confidence = 1.0f, source = "UserRule")
                }
            }
        }

        // 3. VPA handle check
        if (m.contains('@')) {
            val handle = m.substringBefore('@').trim()
            val handleMatch = resolveCategory(handle)
            if (handleMatch.source != "Fallback") {
                return handleMatch
            }
        }

        // 4. MerchantMap lookup
        val mm = MerchantMap.lookup(m)
        if (mm.source != MerchantMap.CategorySource.FALLBACK) {
            return CategoryMatch(
                category = mm.category,
                confidence = mm.confidence,
                source = "KeywordMap"
            )
        }

        // 5. Standard financial keywords
        if (m.contains("salary") || m.contains("payroll") || m.contains("stipend")) {
            return CategoryMatch(category = "Salary", confidence = 0.95f, source = "KeywordMap")
        }
        if (m.contains("refund") || m.contains("reversal")) {
            return CategoryMatch(category = "Refund", confidence = 0.95f, source = "KeywordMap")
        }
        if (m.contains("cashback") || m.contains("reward")) {
            return CategoryMatch(category = "Gift", confidence = 0.90f, source = "KeywordMap")
        }

        // 6. Fallback
        return CategoryMatch(category = "Unknown", confidence = 0.50f, source = "Fallback")
    }

    private fun normalize(s: String): String = s
        .lowercase()
        .replace(Regex("[^a-z0-9@\\s]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
}
