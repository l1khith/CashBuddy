// NO-NETWORK
package com.cashbuddy.core.prob

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
)
