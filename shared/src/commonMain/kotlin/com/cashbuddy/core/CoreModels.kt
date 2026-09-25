// NO-NETWORK
package com.cashbuddy.core

enum class TransactionType {
    DEBIT,
    CREDIT
}

enum class Category {
    Food,
    Transport,
    Shopping,
    Bills,
    Entertainment,
    Health,
    Education,
    Housing,
    Insurance,
    Investments,
    Salary,
    Refund,
    Gift,
    Unknown
}

data class ParsedTransaction(
    val amount: Double,
    val transactionType: TransactionType,
    val category: Category,
    val merchant: String,
    val accountId: String?,
    val sourceApp: String,
    val rawText: String,
    val confidence: Float,
    val timestamp: Long
)

data class RawNotification(
    val packageName: String,
    val title: String,
    val text: String,
    val timestamp: Long
)

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
