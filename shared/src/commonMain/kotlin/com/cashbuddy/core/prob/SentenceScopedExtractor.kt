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

    /**
     * Computes the sentence validity indicator V(s_i) ∈ {0, 1}
     */
    fun isValidTransactionSentence(sentence: String): Boolean {
        if (sentence.isBlank()) return false

        // 1. P_amount(s_i) ≠ ∅: Valid amount pattern present
        val hasAmount = Regexes.AMOUNT.containsMatchIn(sentence) ||
                Regexes.AMOUNT_SUFFIX.containsMatchIn(sentence)
        if (!hasAmount) return false

        // 2. P_verb(s_i) ≠ ∅: Transaction action verb or payment confirmation
        val hasVerb = Regexes.DEBIT.containsMatchIn(sentence) ||
                Regexes.CREDIT.containsMatchIn(sentence) ||
                sentence.contains("payment", ignoreCase = true) ||
                sentence.contains("txn", ignoreCase = true) ||
                sentence.contains("transaction", ignoreCase = true)
        if (!hasVerb) return false

        // 3. ∑_{t ∈ N} 𝟙(t ∈ s_i) = 0: Discard OTP, promotional offers, and forbidden rate shapes
        val hasForbidden = Regexes.OTP.containsMatchIn(sentence) ||
                Regexes.PROMO.containsMatchIn(sentence) ||
                Regexes.OFFER.containsMatchIn(sentence) ||
                EvidenceExtractor.amountHasForbiddenShape(sentence)
        if (hasForbidden) return false

        return true
    }
}
