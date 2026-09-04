package com.cashbuddy.domain.model

data class MerchantRule(
    val id: Long = 0,
    val pattern: String,
    val categoryId: Long,
    val isRegex: Boolean = false,
    val priority: Long = 0,
    val matchCount: Long = 0,
    val createdAt: Long = 0,
    val categoryName: String? = null,
    val categoryColor: String? = null
)
