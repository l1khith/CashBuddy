package com.cashbuddy.presentation.home

import com.cashbuddy.domain.model.Transaction

data class HomeUiState(
    val isLoading: Boolean = true,
    val balance: Double = 0.0,
    val recentTransactions: List<Transaction> = emptyList(),
    val unreviewedCount: Int = 0,
    val monthlyDebit: Double = 0.0,
    val monthlyCredit: Double = 0.0,
    val error: String? = null
)

sealed interface HomeUiEffect {
    data class NavigateToReview(val initialTransactionId: Long? = null) : HomeUiEffect
    data class NavigateToTransactionDetail(val transactionId: Long) : HomeUiEffect
    data object NavigateToAddTransaction : HomeUiEffect
}
