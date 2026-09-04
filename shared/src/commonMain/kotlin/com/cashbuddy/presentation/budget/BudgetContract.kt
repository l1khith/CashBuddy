package com.cashbuddy.presentation.budget

import com.cashbuddy.domain.model.Budget
import com.cashbuddy.domain.model.BudgetPeriod
import com.cashbuddy.domain.model.Category

sealed interface BudgetIntent {
    data object LoadBudgets : BudgetIntent
    data class SetBudget(val categoryId: Long, val amount: Double, val period: BudgetPeriod) : BudgetIntent
    data class DeleteBudget(val budgetId: Long) : BudgetIntent
}

data class BudgetState(
    val budgets: List<Budget> = emptyList(),
    val categories: List<Category> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null
)
