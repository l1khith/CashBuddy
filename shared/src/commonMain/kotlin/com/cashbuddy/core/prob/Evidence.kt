// NO-NETWORK
package com.cashbuddy.core.prob

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class Evidence(
    val hasAmount: Boolean,
    val hasAccount: Boolean,
    val hasUtr: Boolean,
    val hasOtp: Boolean,
    val hasPromo: Boolean,
    val hasOffer: Boolean,
    val hasUpiHandle: Boolean,
    val hasBalanceMention: Boolean,
    val hasSuccessWord: Boolean,
    val senderLooksBank: Boolean,
    val fromMerchantPackage: Boolean,

    // Contextual sentence-scoped signals
    val amountInTransactionContext: Boolean = false,
    val amountAndDebitSameSentence: Boolean = false,
    val amountAndCreditSameSentence: Boolean = false,
    val amountHasForbiddenShape: Boolean = false,

    // Time-based signals
    val recentSimilarAmount: Boolean = false,
    val recentSameMerchant: Boolean = false,
    val burstDetected: Boolean = false
) {
    val hasDebit: Boolean get() = amountAndDebitSameSentence
    val hasCredit: Boolean get() = amountAndCreditSameSentence
    val hasTransactionVerb: Boolean get() = amountInTransactionContext
    val recentSameAmount: Boolean get() = recentSimilarAmount
    val velocityHigh: Boolean get() = burstDetected

    fun toJson(): String = jsonInstance.encodeToString(this)

    companion object {
        private val jsonInstance = Json { ignoreUnknownKeys = true; prettyPrint = false }
        fun fromJson(json: String): Evidence = jsonInstance.decodeFromString(json)
    }
}
