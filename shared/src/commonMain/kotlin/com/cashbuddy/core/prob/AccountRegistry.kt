// NO-NETWORK
package com.cashbuddy.core.prob

import com.cashbuddy.domain.model.Account
import com.cashbuddy.domain.model.AccountType
import com.cashbuddy.domain.repository.AccountRepository
import kotlinx.coroutines.flow.firstOrNull

class AccountRegistry(
    private val accountRepository: AccountRepository
) {
    suspend fun findOrCreate(last4: String, bankHint: String?): Long {
        val accounts = accountRepository.getAll().firstOrNull() ?: emptyList()
        val existing = accounts.find { it.number?.takeLast(4) == last4 }
        if (existing != null) return existing.id

        val displayName = if (!bankHint.isNullOrBlank()) "$bankHint ••$last4" else "Account ••$last4"
        val now = com.cashbuddy.platform.currentTimeMillis()
        val newAccount = Account(
            id = 0L,
            name = displayName,
            type = AccountType.BANK,
            number = "XX$last4",
            bank = bankHint,
            balance = 0.0,
            currency = "INR",
            isActive = true,
            sortOrder = accounts.size.toLong(),
            createdAt = now,
            updatedAt = now
        )
        return accountRepository.insert(newAccount)
    }
}
