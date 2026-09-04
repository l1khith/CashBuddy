package com.cashbuddy.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.cashbuddy.db.Accounts
import com.cashbuddy.db.AppDatabase
import com.cashbuddy.domain.model.Account
import com.cashbuddy.domain.model.AccountType
import com.cashbuddy.domain.repository.AccountRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class AccountRepositoryImpl(
    private val db: AppDatabase,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) : AccountRepository {

    private val queries = db.accountsQueries

    override fun getAll(): Flow<List<Account>> =
        queries.getAll(::mapAccount).asFlow().mapToList(dispatcher)

    override fun getById(id: Long): Flow<Account?> =
        queries.getById(id, ::mapAccount).asFlow().mapToOneOrNull(dispatcher)

    override fun getByType(type: AccountType): Flow<List<Account>> =
        queries.getByType(type.name, ::mapAccount).asFlow().mapToList(dispatcher)

    override suspend fun insert(account: Account): Long = withContext(dispatcher) {
        queries.insert(
            name = account.name,
            type = account.type.name,
            number = account.number,
            bank = account.bank,
            balance = account.balance,
            currency = account.currency,
            is_active = account.isActive,
            sort_order = account.sortOrder,
            created_at = account.createdAt,
            updated_at = account.updatedAt
        )
        account.id
    }

    override suspend fun updateBalance(id: Long, balance: Double): Unit = withContext(dispatcher) {
        queries.updateBalance(balance = balance, updated_at = com.cashbuddy.platform.currentTimeMillis(), id = id)
    }

    override suspend fun update(account: Account): Unit = withContext(dispatcher) {
        queries.update(
            name = account.name,
            type = account.type.name,
            number = account.number,
            bank = account.bank,
            is_active = account.isActive,
            sort_order = account.sortOrder,
            updated_at = com.cashbuddy.platform.currentTimeMillis(),
            id = account.id
        )
    }

    override suspend fun deleteById(id: Long): Unit = withContext(dispatcher) {
        queries.deleteById(id)
    }

    private fun mapAccount(
        id: Long,
        name: String,
        type: String,
        number: String?,
        bank: String?,
        balance: Double,
        currency: String,
        isActive: Boolean,
        sortOrder: Long,
        createdAt: Long,
        updatedAt: Long
    ): Account {
        return Account(
            id = id,
            name = name,
            type = AccountType.valueOf(type),
            number = number,
            bank = bank,
            balance = balance,
            currency = currency,
            isActive = isActive,
            sortOrder = sortOrder,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }
}
