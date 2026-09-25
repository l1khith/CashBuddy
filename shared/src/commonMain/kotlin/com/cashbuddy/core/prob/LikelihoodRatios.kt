// NO-NETWORK
package com.cashbuddy.core.prob

object Priors {
    const val P_TRANSACTION: Double = 0.20
}

object LikelihoodRatios {
    const val AMOUNT_PRESENT = 3.0
    const val ACCOUNT_PRESENT = 3.5
    const val UTR_PRESENT = 3.0
    const val DEBIT_KEYWORD = 3.0
    const val CREDIT_KEYWORD = 3.0
    const val AMOUNT_AND_TYPE = 5.0
    const val BANK_SENDER_HINT = 2.5
    const val UPI_HANDLE_PRESENT = 2.5
    const val BALANCE_MENTIONED = 2.0
    const val TRANSACTION_VERB = 1.8
    const val MERCHANT_APP_PACKAGE = 1.6
    const val SUCCESS_WORD = 1.8
    const val OTP_KEYWORD = 0.05
    const val PROMO_KEYWORD = 0.08
    const val OFFER_KEYWORD = 0.10
    const val SAME_AMOUNT_RECENT = 1.2
    const val SAME_MERCHANT_RECENT = 1.2
    const val VELOCITY_HIGH = 0.5
}
