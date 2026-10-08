// NO-NETWORK
package com.cashbuddy.domain.repository

import com.cashbuddy.domain.model.AlertState
import com.cashbuddy.domain.model.Budget
import kotlinx.coroutines.flow.Flow

interface BudgetRepository {
    suspend fun create(budget: Budget)
    suspend fun update(budget: Budget)
    suspend fun delete(id: String)
    suspend fun getActive(): List<Budget>
    suspend fun getById(id: String): Budget?
    suspend fun findByCategory(category: String, period: String): Budget?
    suspend fun markAlerted(id: String, state: AlertState, at: Long)
    suspend fun resetAlertState(id: String)

    // Flow & transitional support for reactive UI and compatibility
    fun getAll(): Flow<List<Budget>>
    suspend fun insert(budget: Budget): Long = 0L
    suspend fun deleteById(id: Long) = delete(id.toString())
}
