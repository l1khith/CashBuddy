package com.cashbuddy.presentation.budget

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cashbuddy.core.budget.BudgetEngine
import com.cashbuddy.domain.model.Budget
import com.cashbuddy.domain.model.BudgetCategories
import com.cashbuddy.domain.model.BudgetPeriod
import com.cashbuddy.domain.model.BudgetStatus
import com.cashbuddy.domain.repository.BudgetRepository
import com.cashbuddy.domain.repository.CategoryRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class BudgetViewModel(
    private val budgetRepository: BudgetRepository,
    private val categoryRepository: CategoryRepository,
    private val budgetEngine: BudgetEngine,
    coroutineScope: CoroutineScope? = null
) : ViewModel() {

    constructor(
        budgetRepository: BudgetRepository,
        categoryRepository: CategoryRepository,
        budgetEngine: BudgetEngine
    ) : this(budgetRepository, categoryRepository, budgetEngine, null)

    private val scope: CoroutineScope = coroutineScope ?: viewModelScope

    private val _globalBudget = MutableStateFlow<BudgetStatus?>(null)
    val globalBudget: StateFlow<BudgetStatus?> = _globalBudget.asStateFlow()

    private val _categoryBudgets = MutableStateFlow<List<BudgetStatus>>(emptyList())
    val categoryBudgets: StateFlow<List<BudgetStatus>> = _categoryBudgets.asStateFlow()

    val unbudgetedSpent: StateFlow<Double> = combine(_globalBudget, _categoryBudgets) { global, cats ->
        if (global == null) 0.0
        else global.spent - cats.sumOf { it.spent }
    }.stateIn(scope, SharingStarted.Eagerly, 0.0)

    private val _uiState = MutableStateFlow(BudgetUiState())
    val uiState: StateFlow<BudgetUiState> = _uiState.asStateFlow()

    init {
        loadBudgets()
    }

    fun loadBudgets(): Job = scope.launch {
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)
        try {
            val now = com.cashbuddy.platform.currentTimeMillis()
            val activeBudgets = budgetRepository.getActive()
            val statuses = activeBudgets.map { budget ->
                budgetEngine.statusFor(budget, now)
            }
            val global = statuses.firstOrNull { it.budget.category == BudgetCategories.GLOBAL }
            val categoriesOnly = statuses.filter { it.budget.category != BudgetCategories.GLOBAL }
            val unbudgeted = if (global == null) 0.0 else global.spent - categoriesOnly.sumOf { it.spent }

            _globalBudget.value = global
            _categoryBudgets.value = categoriesOnly

            val categories = categoryRepository.getAll().first()
            _uiState.value = BudgetUiState(
                budgets = statuses,
                globalBudget = global,
                categoryBudgets = categoriesOnly,
                unbudgetedSpent = unbudgeted,
                categories = categories,
                isLoading = false,
                error = null
            )
        } catch (e: Throwable) {
            _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
        }
    }

    fun createBudget(category: String, amount: Double, period: BudgetPeriod): Job = scope.launch {
        val now = com.cashbuddy.platform.currentTimeMillis()
        val id = "${now}-${kotlin.random.Random.nextLong().toString(16)}"
        val budget = Budget(
            id = id,
            category = category,
            amount = amount,
            period = period,
            startDate = now,
            isActive = true,
            createdAt = now,
            updatedAt = now
        )
        budgetRepository.create(budget)
        loadBudgets().join()
    }

    fun updateBudget(budget: Budget): Job = scope.launch {
        val now = com.cashbuddy.platform.currentTimeMillis()
        budgetRepository.update(budget.copy(updatedAt = now))
        loadBudgets().join()
    }

    fun deleteBudget(id: String): Job = scope.launch {
        budgetRepository.delete(id)
        loadBudgets().join()
    }

    fun processIntent(intent: BudgetIntent) {
        when (intent) {
            is BudgetIntent.LoadBudgets -> loadBudgets()
            is BudgetIntent.SetBudget -> createBudget(intent.categoryId.toString(), intent.amount, intent.period)
            is BudgetIntent.DeleteBudget -> deleteBudget(intent.budgetId.toString())
        }
    }
}
