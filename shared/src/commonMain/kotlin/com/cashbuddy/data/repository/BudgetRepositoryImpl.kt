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
import kotlinx.coroutines.withContext

class BudgetRepositoryImpl(
    private val db: AppDatabase,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) : BudgetRepository {

    private val queries = db.budgetsQueries

    override fun getActive(currentTimestamp: Long): Flow<List<Budget>> =
        queries.getActive(currentTimestamp) { id, categoryId, amount, period, startDate, endDate, isActive, alertThreshold, createdAt, categoryName, categoryColor, spentAmount ->
            Budget(
                id = id,
                categoryId = categoryId,
                amount = amount,
                period = BudgetPeriod.valueOf(period),
                startDate = startDate,
                endDate = endDate,
                isActive = isActive,
                alertThreshold = alertThreshold,
                createdAt = createdAt,
                categoryName = categoryName,
                categoryColor = categoryColor,
                spentAmount = spentAmount
            )
        }.asFlow().mapToList(dispatcher)

    override fun getAll(): Flow<List<Budget>> =
        queries.getAll { id, categoryId, amount, period, startDate, endDate, isActive, alertThreshold, createdAt, categoryName, categoryColor ->
            Budget(
                id = id,
                categoryId = categoryId,
                amount = amount,
                period = BudgetPeriod.valueOf(period),
                startDate = startDate,
                endDate = endDate,
                isActive = isActive,
                alertThreshold = alertThreshold,
                createdAt = createdAt,
                categoryName = categoryName,
                categoryColor = categoryColor,
                spentAmount = 0.0
            )
        }.asFlow().mapToList(dispatcher)

    override fun getById(id: Long): Flow<Budget?> =
        queries.getById(id) { bId, categoryId, amount, period, startDate, endDate, isActive, alertThreshold, createdAt, categoryName, categoryColor ->
            Budget(
                id = bId,
                categoryId = categoryId,
                amount = amount,
                period = BudgetPeriod.valueOf(period),
                startDate = startDate,
                endDate = endDate,
                isActive = isActive,
                alertThreshold = alertThreshold,
                createdAt = createdAt,
                categoryName = categoryName,
                categoryColor = categoryColor,
                spentAmount = 0.0
            )
        }.asFlow().mapToOneOrNull(dispatcher)

    override suspend fun insert(budget: Budget): Long = withContext(dispatcher) {
        queries.insert(
            category_id = budget.categoryId,
            amount = budget.amount,
            period = budget.period.name,
            start_date = budget.startDate,
            end_date = budget.endDate,
            is_active = budget.isActive,
            alert_threshold = budget.alertThreshold,
            created_at = budget.createdAt
        )
        budget.id
    }

    override suspend fun update(budget: Budget): Unit = withContext(dispatcher) {
        queries.update(
            category_id = budget.categoryId,
            amount = budget.amount,
            period = budget.period.name,
            start_date = budget.startDate,
            end_date = budget.endDate,
            is_active = budget.isActive,
            alert_threshold = budget.alertThreshold,
            id = budget.id
        )
    }

    override suspend fun deleteById(id: Long): Unit = withContext(dispatcher) {
        queries.deleteById(id)
    }
}
