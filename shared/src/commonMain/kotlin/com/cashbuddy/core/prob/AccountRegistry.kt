// NO-NETWORK
package com.cashbuddy.core.prob

import com.cashbuddy.core.MerchantLabels
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

    /**
     * Bucket account for transactions whose account could not be resolved from
     * the message. Previously the pipeline fell back to hardcoded account ID 1,
     * silently attaching unresolvable transactions to whatever account happened
     * to be row 1. Reuses a single named "Unlinked" account if it exists.
     */
    suspend fun findOrCreateUnlinked(): Long {
        val accounts = accountRepository.getAll().firstOrNull() ?: emptyList()
        accounts.firstOrNull { it.name.equals(MerchantLabels.UNLINKED_ACCOUNT, ignoreCase = true) }
            ?.let { return it.id }

        val now = com.cashbuddy.platform.currentTimeMillis()
        return accountRepository.insert(
            Account(
                id = 0L,
                name = MerchantLabels.UNLINKED_ACCOUNT,
                type = AccountType.BANK,
                number = null,
                bank = null,
                balance = 0.0,
                currency = "INR",
                isActive = true,
                sortOrder = accounts.size.toLong(),
                createdAt = now,
                updatedAt = now
            )
        )
    }
}
