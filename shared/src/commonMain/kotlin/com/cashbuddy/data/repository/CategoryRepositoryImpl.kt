package com.cashbuddy.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.cashbuddy.core.CategoryEngine
import com.cashbuddy.core.MerchantRuleEntry
import com.cashbuddy.db.AppDatabase
import com.cashbuddy.domain.model.Category
import com.cashbuddy.domain.model.CategoryConfig
import com.cashbuddy.domain.model.CategoryRegistry
import com.cashbuddy.domain.model.CategoryType
import com.cashbuddy.domain.repository.CategoryRepository
import com.cashbuddy.platform.currentTimeMillis
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext

class CategoryRepositoryImpl(
    private val db: AppDatabase,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val categoryEngine: CategoryEngine? = null
) : CategoryRepository {

    private val queries = db.categoriesQueries

    override fun getAll(): Flow<List<Category>> =
        queries.getAll(::mapCategory)
            .asFlow()
            .mapToList(dispatcher)
            .onEach { categories ->
                CategoryRegistry.registerCategories(categories)
            }

    override fun getByType(type: CategoryType): Flow<List<Category>> =
        queries.getByType(type.name, ::mapCategory).asFlow().mapToList(dispatcher)

    override fun getById(id: Long): Flow<Category?> =
        queries.getById(id, ::mapCategory).asFlow().mapToOneOrNull(dispatcher)

    override suspend fun getByName(name: String): Category? = withContext(dispatcher) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return@withContext null
        queries.getByName(trimmed, ::mapCategory).executeAsOneOrNull()
    }

    override suspend fun isNameTaken(name: String, excludeId: Long?): Boolean = withContext(dispatcher) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return@withContext false
        val count = if (excludeId != null) {
            queries.countByNameCaseInsensitiveExcludingId(name = trimmed, id = excludeId).executeAsOne()
        } else {
            queries.countByNameCaseInsensitive(name = trimmed).executeAsOne()
        }
        count > 0L
    }

    override suspend fun insertCustom(
        name: String,
        type: CategoryType,
        icon: String,
        color: String
    ): Long = withContext(dispatcher) {
        val trimmed = name.trim()
        require(trimmed.length in 2..30) { "Category name must be between 2 and 30 characters" }
        require(!isNameTaken(trimmed)) { "A category named '$trimmed' already exists" }

        val now = currentTimeMillis()
        queries.insertCustom(
            name = trimmed,
            type = type.name,
            icon = icon.ifBlank { "category" },
            color = color.ifBlank { "#FF6B6B" },
            sort_order = 99L,
            created_at = now
        )
        val created = queries.getByName(trimmed, ::mapCategory).executeAsOne()
        created.id
    }

    override suspend fun updateCustom(
        id: Long,
        name: String,
        type: CategoryType,
        icon: String,
        color: String
    ): Unit = withContext(dispatcher) {
        val trimmed = name.trim()
        require(trimmed.length in 2..30) { "Category name must be between 2 and 30 characters" }
        require(!isNameTaken(trimmed, excludeId = id)) { "A category named '$trimmed' already exists" }

        val existing = queries.getById(id, ::mapCategory).executeAsOneOrNull()
            ?: throw IllegalArgumentException("Category with ID $id does not exist")
        check(!existing.isDefault) { "Cannot modify a default system category" }

        queries.updateCustom(
            name = trimmed,
            type = type.name,
            icon = icon.ifBlank { "category" },
            color = color.ifBlank { "#FF6B6B" },
            id = id
        )
    }

    override suspend fun deleteCustom(id: Long): Unit = withContext(dispatcher) {
        db.transaction {
            val target = queries.getById(id, ::mapCategory).executeAsOneOrNull()
                ?: throw IllegalArgumentException("Category with ID $id does not exist")

            check(!target.isDefault) { "Cannot delete protected system category: ${target.name}" }

            // Dynamic resolution of fallback Uncategorized category ID (no hardcoded IDs!)
            val fallbackId = queries.getUncategorizedFallbackId().executeAsOneOrNull()
                ?: queries.getFirstDefaultId().executeAsOneOrNull()
                ?: throw IllegalStateException("Database corruption: No default fallback category found")

            val now = currentTimeMillis()

            // 1. Reassign transactions
            db.transactionsQueries.reassignCategory(fallbackId = fallbackId, targetId = id)

            // 2. Reassign merchant rules
            db.merchant_rulesQueries.reassignCategory(fallbackId = fallbackId, targetId = id)

            // 3. Update budgets targeting the deleted category name
            db.budgetQueries.reassignCategory(
                newCategory = "Uncategorized",
                oldCategory = target.name,
                updatedAt = now
            )

            // 5. Delete category from database
            queries.deleteById(id)
        }

        // Post-commit: Reload CategoryEngine rules and invalidate in-memory cache to eliminate ghost categories
        categoryEngine?.let { engine ->
            engine.invalidateCache()
            val freshRules = db.merchant_rulesQueries.getAll { _, pattern, _, _, _, _, _, categoryName, _ ->
                MerchantRuleEntry(
                    merchant = pattern,
                    category = categoryName
                )
            }.executeAsList()
            engine.loadUserRules(freshRules)
        }
    }

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
                created_at_____________ = currentTimestamp,
                created_at______________ = currentTimestamp
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
