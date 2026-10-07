// NO-NETWORK
package com.cashbuddy.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.cashbuddy.db.AppDatabase
import com.cashbuddy.domain.model.AlertState
import com.cashbuddy.domain.model.Budget
import com.cashbuddy.domain.model.BudgetPeriod
import com.cashbuddy.domain.repository.BudgetRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class SqlDelightBudgetRepository(
    private val db: AppDatabase,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) : BudgetRepository {

    private val queries = db.budgetQueries

    private fun mapRow(
        id: String,
        category: String,
        amount: Double,
        period: String,
        startDate: Long,
        isActive: Long,
        lastAlertState: String?,
        lastAlertAt: Long?,
        createdAt: Long,
        updatedAt: Long
    ): Budget = Budget(
        id = id,
        category = category,
        amount = amount,
        period = try { BudgetPeriod.valueOf(period) } catch (_: Throwable) { BudgetPeriod.MONTHLY },
        startDate = startDate,
        isActive = isActive == 1L,
        lastAlertState = lastAlertState?.let { try { AlertState.valueOf(it) } catch (_: Throwable) { null } },
        lastAlertAt = lastAlertAt,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    override suspend fun create(budget: Budget): Unit = withContext(dispatcher) {
        queries.insert(
            id = budget.id,
            category = budget.category,
            amount = budget.amount,
            period = budget.period.name,
            start_date = budget.startDate,
            is_active = if (budget.isActive) 1L else 0L,
            last_alert_state = budget.lastAlertState?.name,
            last_alert_at = budget.lastAlertAt,
            created_at = budget.createdAt,
            updated_at = budget.updatedAt
        )
    }

    override suspend fun update(budget: Budget): Unit = withContext(dispatcher) {
        queries.update(
            category = budget.category,
            amount = budget.amount,
            period = budget.period.name,
            start_date = budget.startDate,
            is_active = if (budget.isActive) 1L else 0L,
            last_alert_state = budget.lastAlertState?.name,
            last_alert_at = budget.lastAlertAt,
            updated_at = budget.updatedAt,
            id = budget.id
        )
    }

    override suspend fun delete(id: String): Unit = withContext(dispatcher) {
        queries.deleteById(id)
    }

    override suspend fun getActive(): List<Budget> = withContext(dispatcher) {
        queries.getActive(::mapRow).executeAsList()
    }

    override suspend fun getById(id: String): Budget? = withContext(dispatcher) {
        queries.getById(id, ::mapRow).executeAsOneOrNull()
    }

    override suspend fun findByCategory(category: String, period: String): Budget? = withContext(dispatcher) {
        queries.findByCategory(category, period, ::mapRow).executeAsOneOrNull()
    }

    override suspend fun markAlerted(id: String, state: AlertState, at: Long): Unit = withContext(dispatcher) {
        queries.markAlerted(
            lastAlertState = state.name,
            lastAlertAt = at,
            updatedAt = at,
            id = id
        )
    }

    override suspend fun resetAlertState(id: String): Unit = withContext(dispatcher) {
        queries.resetAlertState(
            updatedAt = com.cashbuddy.platform.currentTimeMillis(),
            id = id
        )
    }

    override fun getAll(): Flow<List<Budget>> =
        queries.getAll(::mapRow).asFlow().mapToList(dispatcher)

    override suspend fun insert(budget: Budget): Long {
        create(budget)
        return budget.id.toLongOrNull() ?: 0L
    }

    override suspend fun deleteById(id: Long) {
        delete(id.toString())
    }
}
