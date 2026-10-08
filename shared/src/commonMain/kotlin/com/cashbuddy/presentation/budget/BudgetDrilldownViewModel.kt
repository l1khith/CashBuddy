// NO-NETWORK
package com.cashbuddy.presentation.budget

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cashbuddy.core.budget.BudgetEngine
import com.cashbuddy.domain.model.BudgetPeriod
import com.cashbuddy.domain.model.Transaction
import com.cashbuddy.domain.repository.TransactionRepository
import com.cashbuddy.platform.currentTimeMillis
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
data class BudgetDrilldownUiState(
    val category: String = "",
    val period: BudgetPeriod = BudgetPeriod.MONTHLY,
    val startTime: Long = 0L,
    val endTime: Long = 0L,
    val transactions: List<Transaction> = emptyList(),
    val totalSpent: Double = 0.0,
    val isLoading: Boolean = true
)

class BudgetDrilldownViewModel(
    val category: String,
    val periodName: String,
    private val transactionRepository: TransactionRepository,
    private val budgetEngine: BudgetEngine,
    coroutineScope: CoroutineScope? = null
) : ViewModel() {

    private val scope = coroutineScope ?: viewModelScope

    val period: BudgetPeriod = try {
        BudgetPeriod.valueOf(periodName.uppercase())
    } catch (_: Exception) {
        BudgetPeriod.MONTHLY
    }

    private val _uiState = MutableStateFlow(
        BudgetDrilldownUiState(
            category = category,
            period = period
        )
    )
    val uiState: StateFlow<BudgetDrilldownUiState> = _uiState.asStateFlow()

    init {
        loadTransactions()
    }

    fun loadTransactions(now: Long = currentTimeMillis()): Job {
        return scope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val range = budgetEngine.periodRange(period, now)
            val startTime = range.first
            val endTime = range.last
            transactionRepository.getByCategoryAndPeriod(category, startTime, endTime).collect { txns ->
                val total = txns.sumOf { it.amount }
                _uiState.update {
                    it.copy(
                        category = category,
                        period = period,
                        startTime = startTime,
                        endTime = endTime,
                        transactions = txns,
                        totalSpent = total,
                        isLoading = false
                    )
                }
            }
        }
    }
}
