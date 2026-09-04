package com.cashbuddy.domain.model

enum class BudgetPeriod {
    DAILY,
    WEEKLY,
    MONTHLY,
    YEARLY
}

data class Budget(
    val id: Long = 0,
    val categoryId: Long? = null,
    val amount: Double,
    val period: BudgetPeriod = BudgetPeriod.MONTHLY,
    val startDate: Long,
    val endDate: Long? = null,
    val isActive: Boolean = true,
    val alertThreshold: Double = 80.0,
    val createdAt: Long = 0,
    val categoryName: String? = null,
    val categoryColor: String? = null,
    val spentAmount: Double = 0.0
)
