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
        val timestamp: Long
    )

    data class ExistingCandidate(
        val id: Long,
        val amount: Double,
        val merchant: String?,
        val accountLast4: String?,
        val pTransaction: Double,
        val timestamp: Long
    )

    fun resolve(newTx: DedupInput, existing: List<ExistingCandidate>): Decision {
        val candidates = existing.filter {
            abs(it.amount - newTx.amount) < AMOUNT_EPS &&
            abs(it.timestamp - newTx.timestamp) < WINDOW_MS
        }
        if (candidates.isEmpty()) return Decision.Insert

        val sameMerchant = candidates.firstOrNull {
            it.merchant != null && newTx.merchant != null &&
            it.merchant.equals(newTx.merchant, ignoreCase = true)
        }
        val target = sameMerchant ?: candidates.first()

        return if (newTx.pTransaction > target.pTransaction) {
            Decision.Merge(
                existingId = target.id,
                upgradedFields = UpgradedFields(
                    accountLast4 = newTx.accountLast4 ?: target.accountLast4,
                    merchant = newTx.merchant ?: target.merchant,
                    pTransaction = newTx.pTransaction,
                    clearReview = newTx.pTransaction >= 0.90
                )
            )
        } else {
            Decision.Skip
        }
    }
}
