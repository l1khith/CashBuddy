package com.cashbuddy.presentation.budget

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cashbuddy.domain.model.Budget
import com.cashbuddy.domain.model.BudgetPeriod
import com.cashbuddy.domain.repository.BudgetRepository
import com.cashbuddy.domain.repository.CategoryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class BudgetViewModel(
    private val budgetRepository: BudgetRepository,
    private val categoryRepository: CategoryRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(BudgetState())
    val uiState: StateFlow<BudgetState> = _uiState.asStateFlow()

    init {
        processIntent(BudgetIntent.LoadBudgets)
    }

    fun processIntent(intent: BudgetIntent) {
        when (intent) {
            is BudgetIntent.LoadBudgets -> loadBudgets()
            is BudgetIntent.SetBudget -> setBudget(intent.categoryId, intent.amount, intent.period)
            is BudgetIntent.DeleteBudget -> deleteBudget(intent.budgetId)
        }
    }

    private fun loadBudgets() {
        viewModelScope.launch {
            combine(
                budgetRepository.getAll(),
                categoryRepository.getAll()
            ) { budgetsList, categoriesList ->
                BudgetState(
                    budgets = budgetsList,
                    categories = categoriesList,
                    isLoading = false
                )
            }.collect {
                _uiState.value = it
            }
        }
    }

    private fun setBudget(categoryId: Long, amount: Double, period: BudgetPeriod) {
        viewModelScope.launch {
            val now = com.cashbuddy.platform.currentTimeMillis()
            val budget = Budget(
                id = 0L,
                categoryId = categoryId,
                amount = amount,
                period = period,
                startDate = now,
                endDate = null,
                createdAt = now
            )
            budgetRepository.insert(budget)
        }
    }

    private fun deleteBudget(budgetId: Long) {
        viewModelScope.launch {
            budgetRepository.deleteById(budgetId)
        }
    }
}
