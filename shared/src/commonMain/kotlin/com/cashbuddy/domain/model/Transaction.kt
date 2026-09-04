package com.cashbuddy.domain.model

enum class TransactionType {
    DEBIT,
    CREDIT
}

enum class TransactionStatus {
    PENDING,
    CONFIRMED,
    REJECTED,
    MODIFIED
}

data class Transaction(
    val id: Long = 0,
    val amount: Double,
    val type: TransactionType,
    val currency: String = "INR",
    val merchant: String,
    val categoryId: Long,
    val accountId: Long? = null,
    val sourceApp: String,
    val rawText: String,
    val confidence: Float,
    val status: TransactionStatus = TransactionStatus.PENDING,
    val notes: String? = null,
    val timestamp: Long,
    val createdAt: Long = timestamp,
    val updatedAt: Long = timestamp,
    val categoryName: String? = null,
    val categoryColor: String? = null,
    val categoryIcon: String? = null,
    val accountName: String? = null,
    val accountType: String? = null
)
