package com.cashbuddy.presentation.budget

import androidx.compose.runtime.Immutable
import com.cashbuddy.domain.model.BudgetPeriod
import com.cashbuddy.domain.model.BudgetStatus
import com.cashbuddy.domain.model.Category

@Immutable
data class BudgetUiState(
    val budgets: List<BudgetStatus> = emptyList(),
    val categories: List<Category> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null
)

sealed interface BudgetIntent {
    data object LoadBudgets : BudgetIntent
    data class SetBudget(val categoryId: Long, val amount: Double, val period: BudgetPeriod) : BudgetIntent
    data class DeleteBudget(val budgetId: Long) : BudgetIntent
}

typealias BudgetState = BudgetUiState
