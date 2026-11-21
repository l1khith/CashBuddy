// NO-NETWORK
package com.cashbuddy.core.prob

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class Evidence(
    val hasAmount: Boolean = false,
    val hasAccount: Boolean = false,
    val hasUtr: Boolean = false,
    val hasOtp: Boolean = false,
    val hasPromo: Boolean = false,
    val hasOffer: Boolean = false,
    val hasUpiHandle: Boolean = false,
    val hasBalanceMention: Boolean = false,
    val hasSuccessWord: Boolean = false,
    val senderLooksBank: Boolean = false,
    val fromMerchantPackage: Boolean = false,

    // Contextual sentence-scoped signals
    val amountInTransactionContext: Boolean = false,
    val amountAndDebitSameSentence: Boolean = false,
    val amountAndCreditSameSentence: Boolean = false,
    val amountHasForbiddenShape: Boolean = false,

    // Time-based signals
    val recentSimilarAmount: Boolean = false,
    val recentSameMerchant: Boolean = false,
    val burstDetected: Boolean = false,

    // Learned token Naive Bayes log-odds
    val tokenLogOdds: Double = 0.0,
    val tokenCount: Int = 0,

    // Failure / negation marker on an otherwise transaction-shaped alert. Declined and
    // reversed messages reuse the amount + account + reference layout of a real debit, so
    // the trained gate needs this signal to separate them from successful transactions.
    val hasFailureWord: Boolean = false,

    // In-flight marker (pending / processing / on hold). Also reuses the transaction layout:
    // "Payment of ₹X to Y is processing" must not become a logged transaction yet.
    val hasPendingWord: Boolean = false
) {
    fun toJson(): String = jsonInstance.encodeToString(this)

    companion object {
        val EMPTY = Evidence()
        private val jsonInstance = Json { ignoreUnknownKeys = true; prettyPrint = false }
        fun fromJson(json: String): Evidence = jsonInstance.decodeFromString(json)
    }
}
