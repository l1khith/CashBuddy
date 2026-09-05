package com.cashbuddy.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.cashbuddy.db.AppDatabase
import com.cashbuddy.domain.model.MerchantRule
import com.cashbuddy.domain.repository.MerchantRuleRepository
import com.cashbuddy.platform.currentTimeMillis
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class MerchantRuleRepositoryImpl(
    private val db: AppDatabase,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) : MerchantRuleRepository {

    private val queries = db.merchant_rulesQueries

    override fun getAll(): Flow<List<MerchantRule>> =
        queries.getAll(::mapRuleWithCategory).asFlow().mapToList(dispatcher)

    override suspend fun findMatch(merchant: String): MerchantRule? = withContext(dispatcher) {
        val trimmed = merchant.trim()
        if (trimmed.isEmpty()) return@withContext null

        val matched = queries.findMatch(trimmed, trimmed, ::mapRuleWithCategory).executeAsOneOrNull()
        if (matched != null) {
            queries.incrementMatchCount(matched.id)
        }
        matched
    }

    override suspend fun learnRule(merchant: String, categoryId: Long, priority: Long): Unit = withContext(dispatcher) {
        val pattern = merchant.trim()
        if (pattern.isEmpty()) return@withContext

        val existing = queries.findByPattern(pattern).executeAsOneOrNull()
        if (existing != null) {
            queries.updateByPattern(
                category_id = categoryId,
                priority = priority,
                pattern = pattern
            )
        } else {
            queries.insert(
                pattern = pattern,
                category_id = categoryId,
                is_regex = false,
                priority = priority,
                created_at = currentTimeMillis()
            )
        }
    }

    override suspend fun deleteById(id: Long): Unit = withContext(dispatcher) {
        queries.deleteById(id)
    }

    private fun mapRuleWithCategory(
        id: Long,
        pattern: String,
        categoryId: Long,
        isRegex: Boolean,
        priority: Long,
        matchCount: Long,
        createdAt: Long,
        categoryName: String,
        categoryColor: String
    ): MerchantRule {
        return MerchantRule(
            id = id,
            pattern = pattern,
            categoryId = categoryId,
            categoryName = categoryName,
            categoryColor = categoryColor,
            isRegex = isRegex,
            priority = priority,
            matchCount = matchCount,
            createdAt = createdAt
        )
    }
}
