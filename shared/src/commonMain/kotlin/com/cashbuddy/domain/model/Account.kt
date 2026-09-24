package com.cashbuddy.domain.model

import androidx.compose.runtime.Immutable

enum class AccountType {
    BANK,
    WALLET,
    CASH,
    CREDIT_CARD,
    INVESTMENT
}

@Immutable
data class Account(
    val id: Long = 0,
    val name: String,
    val type: AccountType,
    val number: String? = null,
    val bank: String? = null,
    val balance: Double = 0.0,
    val currency: String = "INR",
    val isActive: Boolean = true,
    val sortOrder: Long = 0,
    val createdAt: Long,
    val updatedAt: Long
)
