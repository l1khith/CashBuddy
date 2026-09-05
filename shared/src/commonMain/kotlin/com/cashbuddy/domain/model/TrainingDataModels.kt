package com.cashbuddy.domain.model

data class RawTrainingData(
    val id: Long = 0L,
    val rawText: String,
    val source: String = "notification",
    val sourceApp: String? = null,
    val extractedAmount: Double? = null,
    val extractedType: String? = null,
    val extractedMerchant: String? = null,
    val timestamp: Long
)

data class UserCorrection(
    val id: Long = 0L,
    val merchant: String,
    val sourceApp: String? = null,
    val rawText: String? = null,
    val oldCategory: String? = null,
    val newCategory: String,
    val timestamp: Long
)

data class TrainingStats(
    val rawCount: Long,
    val correctionsCount: Long
)
