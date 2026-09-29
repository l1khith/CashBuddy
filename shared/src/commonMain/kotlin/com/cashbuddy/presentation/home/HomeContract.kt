package com.cashbuddy.presentation.home

import androidx.compose.runtime.Immutable
import com.cashbuddy.domain.model.TimePeriod
import com.cashbuddy.domain.model.Transaction

@Immutable
data class HomeUiState(
    val isLoading: Boolean = true,
    val balance: Double = 0.0,
    val recentTransactions: List<Transaction> = emptyList(),
    val unreviewedCount: Int = 0,
    val selectedPeriod: TimePeriod = TimePeriod.TODAY,
    val periodDebit: Double = 0.0,
    val periodCredit: Double = 0.0,
    val periodTransactionCount: Long = 0,
    val monthlyDebit: Double = periodDebit,
    val monthlyCredit: Double = periodCredit,
    val error: String? = null
)

sealed interface HomeUiEffect {
    data class NavigateToReview(val initialTransactionId: Long? = null) : HomeUiEffect
    data class NavigateToTransactionDetail(val transactionId: Long) : HomeUiEffect
    data object NavigateToAddTransaction : HomeUiEffect
}
