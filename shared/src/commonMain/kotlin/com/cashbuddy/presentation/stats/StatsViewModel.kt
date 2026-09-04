package com.cashbuddy.presentation.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cashbuddy.domain.repository.CategoryBreakdown
import com.cashbuddy.domain.repository.MonthlySummary
import com.cashbuddy.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class StatsUiState(
    val monthlySummaries: List<MonthlySummary> = emptyList(),
    val categoryBreakdowns: List<CategoryBreakdown> = emptyList(),
    val totalExpense: Double = 0.0,
    val totalIncome: Double = 0.0,
    val isLoading: Boolean = true
)

class StatsViewModel(
    private val transactionRepository: TransactionRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(StatsUiState())
    val uiState: StateFlow<StatsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val now = com.cashbuddy.platform.currentTimeMillis()
            val thirtyDaysAgo = now - (30L * 24 * 60 * 60 * 1000)

            combine(
                transactionRepository.getMonthlySummary(),
                transactionRepository.getCategoryBreakdown(thirtyDaysAgo, now)
            ) { monthly, categories ->
                val latest = monthly.firstOrNull()
                StatsUiState(
                    monthlySummaries = monthly,
                    categoryBreakdowns = categories,
                    totalExpense = latest?.totalDebit ?: 0.0,
                    totalIncome = latest?.totalCredit ?: 0.0,
                    isLoading = false
                )
            }.collect {
                _uiState.value = it
            }
        }
    }
}
