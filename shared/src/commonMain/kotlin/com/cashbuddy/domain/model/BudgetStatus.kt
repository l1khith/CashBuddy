package com.cashbuddy.domain.model

data class BudgetStatus(
    val budget: Budget,
    val spent: Double,
    val remaining: Double,
    val percentUsed: Float,
    val state: AlertState,
    val committed: Double = 0.0,
    val discretionary: Double = 0.0,
    val recurringPatternCount: Int = 0
)
