package com.cashbuddy.domain.repository

import com.cashbuddy.domain.model.Goal
import com.cashbuddy.domain.model.GoalStatus
import kotlinx.coroutines.flow.Flow

interface GoalRepository {
    fun getAll(): Flow<List<Goal>>
    fun getActive(): Flow<List<Goal>>
    fun getById(id: Long): Flow<Goal?>
    suspend fun insert(goal: Goal): Long
    suspend fun updateProgress(id: Long, currentAmount: Double)
    suspend fun updateStatus(id: Long, status: GoalStatus)
    suspend fun deleteById(id: Long)
}
