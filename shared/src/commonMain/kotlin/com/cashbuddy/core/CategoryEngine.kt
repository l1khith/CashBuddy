// NO-NETWORK
package com.cashbuddy.core

import com.cashbuddy.core.prob.ConcurrentHashMap
import com.cashbuddy.core.prob.JaroWinkler

class CategoryEngine {
    private val userRules = ConcurrentHashMap<String, String>()
    private val cache = ConcurrentHashMap<String, CategoryMatch>()

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
        if (norm.isNotEmpty() && !isGenericMerchantToken(norm)) {
            userRules[norm] = category
            cache.clear()
        }
    }

    fun learnCorrection(merchant: String, category: String) = learn(merchant, category)

    fun getCategory(
        rawMerchant: String?,
        timestamp: Long = 0L,
        amount: Double = 0.0,
        vpa: String? = null
    ): CategoryMatch {
        if (rawMerchant.isNullOrBlank()) {
            return CategoryMatch(category = "Unknown", confidence = 0.0f, source = "Fallback")
        }

        val m = normalize(rawMerchant)
        cache[m]?.let { return it }

        val match = resolveCategory(m, rawMerchant, timestamp, amount, vpa)
        // Only cache deterministic rules (user rules, financial rails, VPA grammar)
        // Contextual priors (time + amount) should evaluate dynamically
        if (match.source != "ContextHeuristic" && match.source != "Fallback") {
            if (cache.size > 500) cache.clear()
            cache[m] = match
        }
        return match
    }

    private fun resolveCategory(
        m: String,
        rawMerchant: String,
        timestamp: Long,
        amount: Double,
        vpa: String? = null
    ): CategoryMatch {
        // Tier 1. Direct user rule lookup from DB (Confidence 1.0)
        userRules[m]?.let {
            return CategoryMatch(category = it, confidence = 1.0f, source = "UserRule")
        }

        // Tier 1b. Substring / Token matching against learned user rules
        for ((pattern, category) in userRules) {
            if (pattern.length >= 3 && (m.contains(pattern) || pattern.contains(m))) {
                return CategoryMatch(category = category, confidence = 1.0f, source = "UserRule")
            }
        }

        // Tier 1c. Jaro-Winkler fuzzy match against user rules (Threshold 0.88)
        if (m.length >= 4) {
            var bestCategory: String? = null
            var bestScore = 0.0
            for ((pattern, category) in userRules) {
                val sim = JaroWinkler.similarity(m, pattern)
                if (sim >= 0.88 && sim > bestScore) {
                    bestScore = sim
                    bestCategory = category
                }
            }
            if (bestCategory != null) {
                return CategoryMatch(category = bestCategory, confidence = 1.0f, source = "UserRuleFuzzy")
            }
        }

        // Tier 2. Fallback to Unknown for user review confirmation
        return CategoryMatch(category = "Unknown", confidence = 0.0f, source = "Fallback")
    }

    private fun normalize(s: String): String = s
        .lowercase()
        .replace(Regex("[^a-z0-9@\\s]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    private fun isGenericMerchantToken(token: String): Boolean {
        val norm = token.trim()
        return norm.length < 2 || norm.equals("unknown", ignoreCase = true)
    }
}
