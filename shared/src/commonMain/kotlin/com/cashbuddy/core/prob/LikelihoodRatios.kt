// NO-NETWORK
package com.cashbuddy.core.prob

object Priors {
    const val P_TRANSACTION: Double = 0.20
}

object LikelihoodRatios {
    // Strong contextual positive signals
    const val AMOUNT_IN_CONTEXT = 5.0
    const val AMOUNT_AND_DEBIT_SAME_SENT = 8.0
    const val AMOUNT_AND_CREDIT_SAME_SENT = 8.0
    const val ACCOUNT_PRESENT = 8.0
    const val UTR_PRESENT = 3.0
    const val BANK_SENDER_HINT = 2.5
    const val UPI_HANDLE_PRESENT = 2.5
    const val BALANCE_MENTIONED = 2.0
    const val MERCHANT_APP_PACKAGE = 1.6
    const val SUCCESS_WORD = 1.8

    // Weaker amount signal when not in transaction context
    const val AMOUNT_ANYWHERE = 1.4

    // Strong negatives
    const val AMOUNT_FORBIDDEN_SHAPE = 0.05
    const val OTP_KEYWORD = 0.02
    const val PROMO_KEYWORD = 0.08
    const val OFFER_KEYWORD = 0.10

    // Time-based signals
    const val RECENT_SIMILAR_AMOUNT = 0.30
    const val RECENT_SAME_MERCHANT = 0.55
    const val BURST_DETECTED = 0.60
}
