package com.cashbuddy.domain.model

enum class CategoryType {
    EXPENSE,
    INCOME
}

data class Category(
    val id: Long = 0,
    val name: String,
    val type: CategoryType,
    val icon: String = "category",
    val color: String = "#FF6B6B",
    val isDefault: Boolean = false,
    val sortOrder: Long = 0,
    val parentId: Long? = null,
    val createdAt: Long = 0
)
