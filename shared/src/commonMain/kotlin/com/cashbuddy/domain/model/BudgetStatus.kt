package com.cashbuddy.domain.model

data class BudgetStatus(
    val budget: Budget,
    val spent: Double,
    val remaining: Double,
    val percentUsed: Float,
    val state: AlertState
)
