package com.cashbuddy.presentation.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cashbuddy.domain.model.DateRangeHelper
import com.cashbuddy.domain.model.TimePeriod
import com.cashbuddy.domain.repository.CategoryBreakdown
import com.cashbuddy.domain.repository.MonthlySummary
import com.cashbuddy.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

@Immutable
data class StatsUiState(
    val monthlySummaries: List<MonthlySummary> = emptyList(),
    val categoryBreakdowns: List<CategoryBreakdown> = emptyList(),
    val totalExpense: Double = 0.0,
    val totalIncome: Double = 0.0,
    val actualSpent: Double = 0.0,
    val cardOutstanding: Double = 0.0,
    val lentOut: Double = 0.0,
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
            val monthRange = DateRangeHelper.calculateDateRange(TimePeriod.THIS_MONTH, nowMillis = now)
            val startOfMonth = monthRange.startTimestamp
            val endOfMonth = monthRange.endTimestamp

            combine(
                transactionRepository.getMonthlySummary(),
                transactionRepository.getCategoryBreakdown(startOfMonth, endOfMonth),
                transactionRepository.transactionUpdates.onStart { emit(Unit) }
            ) { monthly, categories, _ ->
                val latest = monthly.firstOrNull()
                // The three month-range projections are independent queries; the combine
                // transform re-runs them on every upstream emission, so run them
                // concurrently instead of serializing three DB round-trips each time.
                val (actualSpent, cardOutstanding, lentOut) = coroutineScope {
                    val actual = async { transactionRepository.sumActualSpent(startOfMonth, endOfMonth) }
                    val card = async { transactionRepository.sumCardOutstanding(startOfMonth, endOfMonth) }
                    val lent = async { transactionRepository.sumLentOut(startOfMonth, endOfMonth) }
                    Triple(actual.await(), card.await(), lent.await())
                }

                StatsUiState(
                    monthlySummaries = monthly,
                    categoryBreakdowns = categories,
                    totalExpense = latest?.totalDebit ?: 0.0,
                    totalIncome = latest?.totalCredit ?: 0.0,
                    actualSpent = actualSpent,
                    cardOutstanding = cardOutstanding,
                    lentOut = lentOut,
                    isLoading = false
                )
            }.collect {
                _uiState.value = it
            }
        }
    }
}
