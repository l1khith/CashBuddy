// NO-NETWORK
package com.cashbuddy.core.prob

import kotlin.math.abs

object DedupEngine {
    const val WINDOW_MS = 5 * 60 * 1000L
    const val AMOUNT_EPS = 0.01

    sealed interface Decision {
        object Insert : Decision
        object Skip : Decision
        data class Merge(val existingId: Long, val upgradedFields: UpgradedFields) : Decision
    }

    data class UpgradedFields(
        val accountLast4: String?,
        val merchant: String?,
        val pTransaction: Double,
        val clearReview: Boolean
    )

    data class DedupInput(
        val amount: Double,
        val merchant: String?,
        val accountLast4: String?,
        val pTransaction: Double,
        val timestamp: Long,
        val utr: String? = null
    )

    data class ExistingCandidate(
        val id: Long,
        val amount: Double,
        val merchant: String?,
        val accountLast4: String?,
        val pTransaction: Double,
        val timestamp: Long,
        val utr: String? = null
    )

    fun resolve(newTx: DedupInput, existing: List<ExistingCandidate>): Decision {
        // Tier 1: Strong Match via Global UTR / UPI Ref ID
        if (!newTx.utr.isNullOrBlank()) {
            val utrTarget = existing.firstOrNull {
                !it.utr.isNullOrBlank() && it.utr.equals(newTx.utr, ignoreCase = true)
            }
            if (utrTarget != null) {
                return buildMergeDecision(newTx, utrTarget)
            }
        }

        // Tier 2: Soft Match via Temporal Window & Exact Paise Equality
        val newPaise = com.cashbuddy.domain.model.Paise.fromRupees(newTx.amount)
        val candidates = existing.filter {
            com.cashbuddy.domain.model.Paise.fromRupees(it.amount) == newPaise &&
            abs(it.timestamp - newTx.timestamp) < WINDOW_MS
        }
        if (candidates.isEmpty()) return Decision.Insert

        val sameMerchant = candidates.firstOrNull {
            it.merchant != null && newTx.merchant != null &&
            (it.merchant.equals(newTx.merchant, ignoreCase = true) ||
             it.merchant.contains(newTx.merchant, ignoreCase = true) ||
             newTx.merchant.contains(it.merchant, ignoreCase = true))
        }
        val target = sameMerchant ?: candidates.first()

        return buildMergeDecision(newTx, target)
    }

    private fun buildMergeDecision(newTx: DedupInput, target: ExistingCandidate): Decision {
        // Pick best merchant: prioritize the more descriptive / longer name if not a generic label
        val chosenMerchant = pickBestMerchant(newTx.merchant, target.merchant)
        val chosenAccount = newTx.accountLast4 ?: target.accountLast4
        val maxP = maxOf(newTx.pTransaction, target.pTransaction)

        return Decision.Merge(
            existingId = target.id,
            upgradedFields = UpgradedFields(
                accountLast4 = chosenAccount,
                merchant = chosenMerchant,
                pTransaction = maxP,
                clearReview = maxP >= 0.90 && newTx.amount < 10000.0 && target.amount < 10000.0
            )
        )
    }

    private fun pickBestMerchant(m1: String?, m2: String?): String? {
        if (m1.isNullOrBlank()) return m2
        if (m2.isNullOrBlank()) return m1
        val generic = setOf("upi merchant", "merchant", "unknown merchant", "bank")
        val m1Gen = generic.contains(m1.lowercase())
        val m2Gen = generic.contains(m2.lowercase())
        if (m1Gen && !m2Gen) return m2
        if (m2Gen && !m1Gen) return m1
        // Prefer longer, more descriptive merchant names (e.g. "Bottle Lab Technologies Private Limited" over "Bottle L")
        return if (m1.length >= m2.length) m1 else m2
    }
}
