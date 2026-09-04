package com.cashbuddy.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.cashbuddy.db.AppDatabase
import com.cashbuddy.domain.model.Goal
import com.cashbuddy.domain.model.GoalStatus
import com.cashbuddy.domain.repository.GoalRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class GoalRepositoryImpl(
    private val db: AppDatabase,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) : GoalRepository {

    private val queries = db.goalsQueries

    override fun getAll(): Flow<List<Goal>> =
        queries.getAll(::mapGoal).asFlow().mapToList(dispatcher)

    override fun getActive(): Flow<List<Goal>> =
        queries.getActive(::mapGoal).asFlow().mapToList(dispatcher)

    override fun getById(id: Long): Flow<Goal?> =
        queries.getById(id, ::mapGoal).asFlow().mapToOneOrNull(dispatcher)

    override suspend fun insert(goal: Goal): Long = withContext(dispatcher) {
        queries.insert(
            name = goal.name,
            target_amount = goal.targetAmount,
            current_amount = goal.currentAmount,
            deadline = goal.deadline,
            category_id = goal.categoryId,
            icon = goal.icon,
            color = goal.color,
            status = goal.status.name,
            created_at = goal.createdAt,
            updated_at = goal.updatedAt
        )
        goal.id
    }

    override suspend fun updateProgress(id: Long, currentAmount: Double): Unit = withContext(dispatcher) {
        queries.updateProgress(current_amount = currentAmount, updated_at = com.cashbuddy.platform.currentTimeMillis(), id = id)
    }

    override suspend fun updateStatus(id: Long, status: GoalStatus): Unit = withContext(dispatcher) {
        queries.updateStatus(status = status.name, updated_at = com.cashbuddy.platform.currentTimeMillis(), id = id)
    }

    override suspend fun deleteById(id: Long): Unit = withContext(dispatcher) {
        queries.deleteById(id)
    }

    private fun mapGoal(
        id: Long,
        name: String,
        targetAmount: Double,
        currentAmount: Double,
        deadline: Long?,
        categoryId: Long?,
        icon: String,
        color: String,
        status: String,
        createdAt: Long,
        updatedAt: Long
    ): Goal {
        return Goal(
            id = id,
            name = name,
            targetAmount = targetAmount,
            currentAmount = currentAmount,
            deadline = deadline,
            categoryId = categoryId,
            icon = icon,
            color = color,
            status = GoalStatus.valueOf(status),
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }
}
