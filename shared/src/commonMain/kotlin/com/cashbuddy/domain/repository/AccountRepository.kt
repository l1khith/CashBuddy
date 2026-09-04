package com.cashbuddy.domain.repository

import com.cashbuddy.domain.model.Account
import com.cashbuddy.domain.model.AccountType
import kotlinx.coroutines.flow.Flow

interface AccountRepository {
    fun getAll(): Flow<List<Account>>
    fun getById(id: Long): Flow<Account?>
    fun getByType(type: AccountType): Flow<List<Account>>
    suspend fun insert(account: Account): Long
    suspend fun updateBalance(id: Long, balance: Double)
    suspend fun update(account: Account)
    suspend fun deleteById(id: Long)
}
