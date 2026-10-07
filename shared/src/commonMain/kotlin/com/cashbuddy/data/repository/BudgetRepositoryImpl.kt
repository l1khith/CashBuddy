// NO-NETWORK
package com.cashbuddy.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.cashbuddy.db.AppDatabase
import com.cashbuddy.domain.model.AlertState
import com.cashbuddy.domain.model.Budget
import com.cashbuddy.domain.model.BudgetPeriod
import com.cashbuddy.domain.repository.BudgetRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class BudgetRepositoryImpl(
    private val db: AppDatabase,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) : BudgetRepository {

    private val queries = db.budgetQueries

    override fun getActive(currentTimestamp: Long): Flow<List<Budget>> =
        queries.getActive().asFlow().mapToList(dispatcher).map { list ->
            list.map { b ->
                Budget(
                    id = b.id,
                    category = b.category,
                    amount = b.amount,
                    period = try { BudgetPeriod.valueOf(b.period) } catch (_: Throwable) { BudgetPeriod.MONTHLY },
                    startDate = b.start_date,
                    isActive = b.is_active == 1L,
                    lastAlertState = b.last_alert_state?.let { try { AlertState.valueOf(it) } catch (_: Throwable) { null } },
                    lastAlertAt = b.last_alert_at,
                    createdAt = b.created_at,
                    updatedAt = b.updated_at
                )
            }
        }

    override fun getAll(): Flow<List<Budget>> =
        queries.getAll().asFlow().mapToList(dispatcher).map { list ->
            list.map { b ->
                Budget(
                    id = b.id,
                    category = b.category,
                    amount = b.amount,
                    period = try { BudgetPeriod.valueOf(b.period) } catch (_: Throwable) { BudgetPeriod.MONTHLY },
                    startDate = b.start_date,
                    isActive = b.is_active == 1L,
                    lastAlertState = b.last_alert_state?.let { try { AlertState.valueOf(it) } catch (_: Throwable) { null } },
                    lastAlertAt = b.last_alert_at,
                    createdAt = b.created_at,
                    updatedAt = b.updated_at
                )
            }
        }

    override fun getById(id: Long): Flow<Budget?> =
        queries.getById(id.toString()).asFlow().mapToOneOrNull(dispatcher).map { b ->
            b?.let {
                Budget(
                    id = it.id,
                    category = it.category,
                    amount = it.amount,
                    period = try { BudgetPeriod.valueOf(it.period) } catch (_: Throwable) { BudgetPeriod.MONTHLY },
                    startDate = it.start_date,
                    isActive = it.is_active == 1L,
                    lastAlertState = it.last_alert_state?.let { s -> try { AlertState.valueOf(s) } catch (_: Throwable) { null } },
                    lastAlertAt = it.last_alert_at,
                    createdAt = it.created_at,
                    updatedAt = it.updated_at
                )
            }
        }

    override suspend fun insert(budget: Budget): Long = withContext(dispatcher) {
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
        budget.id.toLongOrNull() ?: 0L
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

    override suspend fun deleteById(id: Long): Unit = withContext(dispatcher) {
        queries.deleteById(id.toString())
    }
}
