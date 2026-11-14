package com.cashbuddy.domain.repository

import com.cashbuddy.domain.model.Category
import com.cashbuddy.domain.model.CategoryType
import kotlinx.coroutines.flow.Flow

interface CategoryRepository {
    fun getAll(): Flow<List<Category>>
    fun getByType(type: CategoryType): Flow<List<Category>>
    fun getById(id: Long): Flow<Category?>
    suspend fun getByName(name: String): Category? = null
    suspend fun isNameTaken(name: String, excludeId: Long? = null): Boolean = false
    suspend fun insertCustom(name: String, type: CategoryType, icon: String, color: String): Long = 0L
    suspend fun updateCustom(id: Long, name: String, type: CategoryType, icon: String, color: String) {}
    suspend fun deleteCustom(id: Long) {}
    suspend fun seedDefaults(currentTimestamp: Long)
}
