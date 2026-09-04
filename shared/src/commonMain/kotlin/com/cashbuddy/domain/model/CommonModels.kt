package com.cashbuddy.domain.model

data class DateRange(
    val startTimestamp: Long,
    val endTimestamp: Long
)

data class Money(
    val amount: Double,
    val currency: String = "INR"
)
