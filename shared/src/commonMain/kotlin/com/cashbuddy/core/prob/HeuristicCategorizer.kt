// NO-NETWORK
package com.cashbuddy.core.prob


/**
 * Pure contextual and structural heuristics engine.
 * Eliminates hardcoded brand lists (e.g. Swiggy, Zomato, Uber) in favor of:
 * 1. Structural VPA (UPI handle) grammar keywords.
 * 2. Temporal & monetary contextual priors (Time + Amount) as Review Inbox suggestions.
 */
object HeuristicCategorizer {

    data class HeuristicMatch(
        val category: String,
        val confidence: Float,
        val reason: String
    )

    /**
     * Tier 2: Structural VPA handle inspection.
     * In Zero-Map architecture, categories are learned from user confirmations
     * rather than static hardcoded keyword dictionaries.
     */
    fun matchVpa(vpa: String?): HeuristicMatch? {
        return null
    }

    /**
     * Tier 3: Contextual Prior based on Time of Day & Amount.
     * In Zero-Map architecture, we NEVER assume categories based on clock hours
     * (e.g. 1 PM != always Food, 8 AM != always Transport).
     * Categories are learned dynamically from actual user patterns and confirmations.
     */
    fun matchContext(timestamp: Long, amount: Double): HeuristicMatch? {
        // Zero-Map: Do not hallucinate categories based on clock time.
        // Return null so the transaction enters Review as "Unknown" for user confirmation.
        return null
    }
}
