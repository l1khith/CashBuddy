package com.cashbuddy.domain.model

data class Budget(
    val id: String,
    val category: String,
    val amount: Double,
    val period: BudgetPeriod,
    val startDate: Long,
    val isActive: Boolean = true,
    val lastAlertState: AlertState? = null,
    val lastAlertAt: Long? = null,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
) {
    val categoryName: String get() = category
    val spentAmount: Double get() = 0.0
}
