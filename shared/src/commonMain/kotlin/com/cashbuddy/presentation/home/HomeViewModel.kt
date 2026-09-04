package com.cashbuddy.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cashbuddy.domain.usecase.CalculateBalanceUseCase
import com.cashbuddy.domain.usecase.GetMonthlySummaryUseCase
import com.cashbuddy.domain.usecase.GetRecentTransactionsUseCase
import com.cashbuddy.domain.usecase.GetUnreviewedCountUseCase
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class HomeViewModel(
    calculateBalanceUseCase: CalculateBalanceUseCase,
    getRecentTransactionsUseCase: GetRecentTransactionsUseCase,
    getUnreviewedCountUseCase: GetUnreviewedCountUseCase,
    getMonthlySummaryUseCase: GetMonthlySummaryUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<HomeUiEffect>()
    val effects: SharedFlow<HomeUiEffect> = _effects.asSharedFlow()

    init {
        viewModelScope.launch {
            combine(
                calculateBalanceUseCase(),
                getRecentTransactionsUseCase(limit = 15),
                getUnreviewedCountUseCase(),
                getMonthlySummaryUseCase()
            ) { balance, recentTx, unreviewed, monthlySummaries ->
                val latestMonth = monthlySummaries.firstOrNull()
                HomeUiState(
                    isLoading = false,
                    balance = balance,
                    recentTransactions = recentTx,
                    unreviewedCount = unreviewed,
                    monthlyDebit = latestMonth?.totalDebit ?: 0.0,
                    monthlyCredit = latestMonth?.totalCredit ?: 0.0,
                    error = null
                )
            }.collect { state ->
                _uiState.value = state
            }
        }
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
