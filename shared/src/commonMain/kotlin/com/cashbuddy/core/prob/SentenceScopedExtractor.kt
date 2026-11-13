// NO-NETWORK
package com.cashbuddy.core.prob

import kotlin.math.roundToLong

/**
 * Stage 1: Sentence Isolation & Filter Operator V(s_i)
 *
 * Mathematical formulation:
 * V(s_i) = 𝟙( P_amount(s_i) ≠ ∅ ∧ P_verb(s_i) ≠ ∅ ∧ ∑_{t ∈ N} 𝟙(t ∈ s_i) = 0 )
 *
 * Discards non-transactional sentences, promotional alerts, job/salary ads,
 * OTPs, and status cascades while extracting valid transactional amounts and clauses.
 */
object SentenceScopedExtractor {

    private const val CURR_PREFIX = """(?:₹|Rs[.:]?|INR|[$€£¥]|USD|EUR|GBP|CAD|AUD|AED|SGD|JPY|C\$|A\$|S\$)"""
    private const val CURR_SUFFIX = """(?:₹|Rs[.:]?|INR|[$€£¥]|USD|EUR|GBP|CAD|AUD|AED|SGD|JPY|paise|paisa)"""

    // P_amount(s_i): Pattern matcher for continuous currency sequences
    val AMOUNT_PREFIX_REGEX = Regex("""(?i)$CURR_PREFIX\s*(\d+(?:,\d{2,3})*(?:\.\d{1,2})?|\d+(?:\.\d{1,2})?)""")
    val AMOUNT_SUFFIX_REGEX = Regex("""(?i)(\d+(?:,\d{2,3})*(?:\.\d{1,2})?|\d+(?:\.\d{1,2})?)\s*$CURR_SUFFIX""")
    val PAISE_ONLY_REGEX = Regex("""(?i)\b(\d+)\s*(?:paise|paisa)\b""")

    // P_verb(s_i): Transactional state verbs and noun-phrase completions
    val TRANSACTION_STATE_VERBS = Regex(
        """(?i)\b(debited|withdrawn|spent|paid(?!\s+(?:to\s+)?you\b)|payment(?:\s+of|\s+for|\s+successful|\s+done|\s+completed)?|sent(?!\s+you\b)|transferred(?!\s+to\s+you\b)|deducted|charged|purchase\s+at|credited|received|deposited|added|refunded|cashback|fare(?:\s+of)?|bill(?:\s+of)?)\b"""
    )

    // N: Negative forbidden tokens (Promotions, salary, OTP, discounts, reminders)
    private val FORBIDDEN_TOKENS = listOf(
        Regex("""(?i)\b(?:lpa|ctc|stipend)\b"""),
        Regex("""(?i)\b(?:per\s+(?:annum|month|year)|a\s+year|annually)\b"""),
        Regex("""(?i)\b(?:salary\s+(?:up\s*to\s+)?$CURR_PREFIX)"""),
        Regex("""(?i)$CURR_PREFIX\s*\d[\d,.]*[kKlL]?\s*(?:off|discount|voucher)\b"""),
        Regex("""(?i)\b(?:up\s*to|upto)\s*$CURR_PREFIX\s*\d"""),
        Regex("""(?i)$CURR_PREFIX\s*\d[\d,.]*[kKlL]?\s*%"""),
        Regex("""(?i)\b(?:otp|one time password|verification code|secret code|do not share)\b"""),
        Regex("""(?i)\b(?:statement\s+for|statement\s+ready|your\s+monthly\s+statement)\b"""),
        Regex("""(?i)\b(?:bill\s+is\s+due|emi\s+due|upcoming\s+bill)\b"""),
        Regex("""(?i)\b(?:pre-approved|win\s+cash|apply\s+now|apply\s+for)\b""")
    )

    /**
     * Computes the sentence validity indicator V(s_i) ∈ {0, 1}
     */
    fun isValidTransactionSentence(sentence: String): Boolean {
        if (sentence.isBlank()) return false

        // 1. P_amount(s_i) ≠ ∅
        val hasAmount = AMOUNT_PREFIX_REGEX.containsMatchIn(sentence) ||
                AMOUNT_SUFFIX_REGEX.containsMatchIn(sentence) ||
                PAISE_ONLY_REGEX.containsMatchIn(sentence)
        if (!hasAmount) return false

        // 2. P_verb(s_i) ≠ ∅
        val hasVerb = TRANSACTION_STATE_VERBS.containsMatchIn(sentence)
        if (!hasVerb) return false

        // 3. ∑_{t ∈ N} 𝟙(t ∈ s_i) = 0
        val hasForbidden = FORBIDDEN_TOKENS.any { it.containsMatchIn(sentence) }
        if (hasForbidden) return false

        return true
    }
}
