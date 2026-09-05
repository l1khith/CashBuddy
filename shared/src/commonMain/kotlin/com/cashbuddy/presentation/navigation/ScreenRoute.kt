package com.cashbuddy.presentation.navigation

import kotlinx.serialization.Serializable

sealed interface ScreenRoute {
    @Serializable
    data object Home : ScreenRoute

    @Serializable
    data object Transactions : ScreenRoute

    @Serializable
    data class TransactionDetail(val transactionId: Long) : ScreenRoute

    @Serializable
    data object ReviewInbox : ScreenRoute

    @Serializable
    data object Stats : ScreenRoute

    @Serializable
    data object Accounts : ScreenRoute

    @Serializable
    data object Budgets : ScreenRoute

    @Serializable
    data object Goals : ScreenRoute

    @Serializable
    data object Settings : ScreenRoute

    @Serializable
    data class AddTransaction(val prefilledId: Long? = null) : ScreenRoute

    @Serializable
    data object PersonalizationDashboard : ScreenRoute

    @Serializable
    data object DataView : ScreenRoute
}
