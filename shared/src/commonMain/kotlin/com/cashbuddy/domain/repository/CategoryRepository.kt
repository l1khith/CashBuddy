package com.cashbuddy.domain.repository

import com.cashbuddy.domain.model.Category
import com.cashbuddy.domain.model.CategoryType
import kotlinx.coroutines.flow.Flow

interface CategoryRepository {
    fun getAll(): Flow<List<Category>>
    fun getByType(type: CategoryType): Flow<List<Category>>
    fun getById(id: Long): Flow<Category?>
    suspend fun seedDefaults(currentTimestamp: Long)
}
