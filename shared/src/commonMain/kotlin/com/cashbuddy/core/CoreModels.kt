// NO-NETWORK
package com.cashbuddy.core

enum class TransactionType {
    DEBIT,
    CREDIT
}


data class ScreenshotTransaction(
    val amount: Double,
    val transactionType: TransactionType,
    val merchant: String,
    val category: String,
    val utrOrRef: String?,
    val appName: String? = null,
    val confidence: Float,
    val rawText: String
)

data class CategoryMatch(
    val category: String,
    val confidence: Float,
    val source: String
)

data class MerchantRuleEntry(
    val merchant: String,
    val category: String
)
