// NO-NETWORK
package com.cashbuddy.core.prob

/**
 * Tokenized substring check against generic financial vocabulary.
 * Used ONLY as a weak hint feeding the probabilistic classifier's senderLooksBank evidence field.
 * NEVER gates acceptance and NEVER contains specific bank sender IDs.
 */
object SenderFingerprint {
    private val GENERIC_BANK_TOKENS = setOf(
        "bank", "bnk", "crd", "sms", "alert", "alrt", "notify", "upi", "pay", "fin", "money", "loan"
    )

    fun isBankLike(sender: String?): Boolean {
        if (sender.isNullOrBlank()) return false
        val clean = sender.lowercase().trim()
        val core = if (clean.contains('-')) clean.substringAfter('-') else clean
        return GENERIC_BANK_TOKENS.any { core.contains(it) }
    }
}
