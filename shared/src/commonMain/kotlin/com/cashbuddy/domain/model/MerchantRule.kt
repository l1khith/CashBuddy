package com.cashbuddy.domain.model

data class MerchantRule(
    val id: Long = 0L,
    val pattern: String,
    val categoryId: Long,
    val categoryName: String? = null,
    val categoryColor: String? = null,
    val isRegex: Boolean = false,
    val priority: Long = 0L,
    val matchCount: Long = 0L,
    val createdAt: Long
)
