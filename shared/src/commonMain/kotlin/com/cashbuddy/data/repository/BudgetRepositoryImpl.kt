// NO-NETWORK
package com.cashbuddy.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.cashbuddy.db.AppDatabase
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
                    id = b.id.toLongOrNull() ?: 0L,
                    categoryId = b.category.toLongOrNull(),
                    amount = b.amount,
                    period = try { BudgetPeriod.valueOf(b.period) } catch (_: Throwable) { BudgetPeriod.MONTHLY },
                    startDate = b.start_date,
                    endDate = null,
                    isActive = b.is_active == 1L,
                    alertThreshold = 80.0,
                    createdAt = b.created_at,
                    categoryName = b.category,
                    categoryColor = null,
                    spentAmount = 0.0
                )
            }
        }

    override fun getAll(): Flow<List<Budget>> =
        queries.getAll().asFlow().mapToList(dispatcher).map { list ->
            list.map { b ->
                Budget(
                    id = b.id.toLongOrNull() ?: 0L,
                    categoryId = b.category.toLongOrNull(),
                    amount = b.amount,
                    period = try { BudgetPeriod.valueOf(b.period) } catch (_: Throwable) { BudgetPeriod.MONTHLY },
                    startDate = b.start_date,
                    endDate = null,
                    isActive = b.is_active == 1L,
                    alertThreshold = 80.0,
                    createdAt = b.created_at,
                    categoryName = b.category,
                    categoryColor = null,
                    spentAmount = 0.0
                )
            }
        }

    override fun getById(id: Long): Flow<Budget?> =
        queries.getById(id.toString()).asFlow().mapToOneOrNull(dispatcher).map { b ->
            b?.let {
                Budget(
                    id = it.id.toLongOrNull() ?: 0L,
                    categoryId = it.category.toLongOrNull(),
                    amount = it.amount,
                    period = try { BudgetPeriod.valueOf(it.period) } catch (_: Throwable) { BudgetPeriod.MONTHLY },
                    startDate = it.start_date,
                    endDate = null,
                    isActive = it.is_active == 1L,
                    alertThreshold = 80.0,
                    createdAt = it.created_at,
                    categoryName = it.category,
                    categoryColor = null,
                    spentAmount = 0.0
                )
            }
        }

    override suspend fun insert(budget: Budget): Long = withContext(dispatcher) {
        val id = budget.id.toString()
        queries.insert(
            id = id,
            category = budget.categoryId?.toString() ?: "",
            amount = budget.amount,
            period = budget.period.name,
            start_date = budget.startDate,
            is_active = if (budget.isActive) 1L else 0L,
            last_alert_state = null,
            last_alert_at = null,
            created_at = budget.createdAt,
            updated_at = budget.createdAt
        )
        budget.id
    }

    override suspend fun update(budget: Budget): Unit = withContext(dispatcher) {
        queries.update(
            category = budget.categoryId?.toString() ?: "",
            amount = budget.amount,
            period = budget.period.name,
            start_date = budget.startDate,
            is_active = if (budget.isActive) 1L else 0L,
            last_alert_state = null,
            last_alert_at = null,
            updated_at = budget.startDate,
            id = budget.id.toString()
        )
    }

    override suspend fun deleteById(id: Long): Unit = withContext(dispatcher) {
        queries.deleteById(id.toString())
    }
}
