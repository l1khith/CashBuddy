package com.cashbuddy.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cashbuddy.domain.model.DateRangeHelper
import com.cashbuddy.domain.model.TimePeriod
import com.cashbuddy.domain.usecase.CalculateBalanceUseCase
import com.cashbuddy.domain.usecase.GetDateRangeSummaryUseCase
import com.cashbuddy.domain.usecase.GetRecentTransactionsUseCase
import com.cashbuddy.domain.usecase.GetTransactionsByDateRangeWithLimitUseCase
import com.cashbuddy.domain.usecase.GetUnreviewedCountUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(
    private val calculateBalanceUseCase: CalculateBalanceUseCase,
    private val getRecentTransactionsUseCase: GetRecentTransactionsUseCase,
    private val getUnreviewedCountUseCase: GetUnreviewedCountUseCase,
    private val getDateRangeSummaryUseCase: GetDateRangeSummaryUseCase,
    private val getTransactionsByDateRangeWithLimitUseCase: GetTransactionsByDateRangeWithLimitUseCase
) : ViewModel() {

    private val _selectedPeriod = MutableStateFlow(TimePeriod.TODAY)

    private val _effects = MutableSharedFlow<HomeUiEffect>(extraBufferCapacity = 1)
    val effects: SharedFlow<HomeUiEffect> = _effects.asSharedFlow()

    private val periodDataFlow = _selectedPeriod.flatMapLatest { period ->
        val range = DateRangeHelper.calculateDateRange(period)
        combine(
            getDateRangeSummaryUseCase(range.startTimestamp, range.endTimestamp),
            if (period == TimePeriod.ALL_TIME) {
                getRecentTransactionsUseCase(limit = 15)
            } else {
                getTransactionsByDateRangeWithLimitUseCase(range.startTimestamp, range.endTimestamp, limit = 15)
            }
        ) { summary, txs ->
            Triple(period, summary, txs)
        }
    }

    val uiState: StateFlow<HomeUiState> = combine(
        calculateBalanceUseCase(),
        getUnreviewedCountUseCase(),
        periodDataFlow
    ) { balance, unreviewed, (period, summary, txs) ->
        HomeUiState(
            isLoading = false,
            balance = balance,
            recentTransactions = txs,
            unreviewedCount = unreviewed,
            selectedPeriod = period,
            periodDebit = summary.totalDebit,
            periodCredit = summary.totalCredit,
            periodTransactionCount = summary.transactionCount,
            error = null
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HomeUiState()
    )

    fun onPeriodSelected(period: TimePeriod) {
        _selectedPeriod.value = period
    }

    fun onReviewBannerClicked() {
        viewModelScope.launch {
            _effects.emit(HomeUiEffect.NavigateToReview())
        }
    }

    fun onTransactionClicked(transactionId: Long) {
        viewModelScope.launch {
            _effects.emit(HomeUiEffect.NavigateToTransactionDetail(transactionId))
        }
    }

    fun onAddTransactionClicked() {
        viewModelScope.launch {
            _effects.emit(HomeUiEffect.NavigateToAddTransaction)
        }
    }
}
