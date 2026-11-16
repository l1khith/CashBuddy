// NO-NETWORK
package com.cashbuddy.core

/**
 * Single source of truth for generic merchant label literals.
 *
 * Previously three independent hardcoded copies drifted apart:
 *  - MessagePipeline defaulted missing merchants to "Unknown Merchant"
 *  - ScreenshotParserEngine fell back to "UPI Merchant"
 *  - DedupEngine kept its own private generic set for merge decisions
 *
 * Any new generic label must be added to [GENERIC_LABELS] so dedup/merge
 * and rule-learning treat it consistently.
 */
object MerchantLabels {
    /** Fallback when the pipeline could not extract any merchant name. */
    const val UNKNOWN_MERCHANT = "Unknown Merchant"

    /** Fallback when OCR found no recognizable payee on a screenshot. */
    const val UPI_MERCHANT = "UPI Merchant"

    /** Name of the bucket account for transactions whose account could not be resolved. */
    const val UNLINKED_ACCOUNT = "Unlinked"

    /** Generic/non-identifying labels that must never win a dedup "best merchant" vote or seed a learned rule. */
    val GENERIC_LABELS: Set<String> = setOf(
        "upi merchant",
        "merchant",
        "unknown merchant",
        "bank",
        "unknown",
        "unknown payee"
    )

    fun isGeneric(name: String?): Boolean {
        if (name.isNullOrBlank()) return true
        return name.trim().lowercase() in GENERIC_LABELS
    }
}
