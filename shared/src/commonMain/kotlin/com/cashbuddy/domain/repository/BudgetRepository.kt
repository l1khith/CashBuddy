package com.cashbuddy.domain.repository

import com.cashbuddy.domain.model.Budget
import kotlinx.coroutines.flow.Flow

interface BudgetRepository {
    fun getActive(currentTimestamp: Long): Flow<List<Budget>>
    fun getAll(): Flow<List<Budget>>
    fun getById(id: Long): Flow<Budget?>
    suspend fun insert(budget: Budget): Long
    suspend fun update(budget: Budget)
    suspend fun deleteById(id: Long)
}
