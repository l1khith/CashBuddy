package com.cashbuddy.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.cashbuddy.db.AppDatabase
import com.cashbuddy.domain.model.Category
import com.cashbuddy.domain.model.CategoryType
import com.cashbuddy.domain.repository.CategoryRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class CategoryRepositoryImpl(
    private val db: AppDatabase,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) : CategoryRepository {

    private val queries = db.categoriesQueries

    override fun getAll(): Flow<List<Category>> =
        queries.getAll(::mapCategory).asFlow().mapToList(dispatcher)

    override fun getByType(type: CategoryType): Flow<List<Category>> =
        queries.getByType(type.name, ::mapCategory).asFlow().mapToList(dispatcher)

    override fun getById(id: Long): Flow<Category?> =
        queries.getById(id, ::mapCategory).asFlow().mapToOneOrNull(dispatcher)

    override suspend fun seedDefaults(currentTimestamp: Long): Unit = withContext(dispatcher) {
        val existing = queries.getAll(::mapCategory).executeAsList()
        if (existing.isEmpty()) {
            queries.insertDefaults(
                created_at = currentTimestamp,
                created_at_ = currentTimestamp,
                created_at__ = currentTimestamp,
                created_at___ = currentTimestamp,
                created_at____ = currentTimestamp,
                created_at_____ = currentTimestamp,
                created_at______ = currentTimestamp,
                created_at_______ = currentTimestamp,
                created_at________ = currentTimestamp,
                created_at_________ = currentTimestamp,
                created_at__________ = currentTimestamp,
                created_at___________ = currentTimestamp,
                created_at____________ = currentTimestamp,
                created_at_____________ = currentTimestamp
            )
        }
    }

    private fun mapCategory(
        id: Long,
        name: String,
        type: String,
        icon: String,
        color: String,
        isDefault: Boolean,
        sortOrder: Long,
        parentId: Long?,
        createdAt: Long
    ): Category {
        return Category(
            id = id,
            name = name,
            type = CategoryType.valueOf(type),
            icon = icon,
            color = color,
            isDefault = isDefault,
            sortOrder = sortOrder,
            parentId = parentId,
            createdAt = createdAt
        )
    }
}
