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
    val hasDebit: Boolean,
    val hasCredit: Boolean,
    val hasOtp: Boolean,
    val hasPromo: Boolean,
    val hasOffer: Boolean,
    val hasUpiHandle: Boolean,
    val hasBalanceMention: Boolean,
    val hasTransactionVerb: Boolean,
    val hasSuccessWord: Boolean,
    val senderLooksBank: Boolean,
    val fromMerchantPackage: Boolean,
    val recentSameAmount: Boolean = false,
    val recentSameMerchant: Boolean = false,
    val velocityHigh: Boolean = false
) {
    fun toJson(): String = jsonInstance.encodeToString(this)

    companion object {
        private val jsonInstance = Json { ignoreUnknownKeys = true; prettyPrint = false }
        fun fromJson(json: String): Evidence = jsonInstance.decodeFromString(json)
    }
}
