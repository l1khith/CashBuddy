// NO-NETWORK
package com.cashbuddy.data.repository

import com.cashbuddy.db.AppDatabase
import com.cashbuddy.domain.repository.RuleSource
import com.cashbuddy.domain.repository.UserRule
import com.cashbuddy.domain.repository.UserRuleRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class UserRuleRepositoryImpl(
    private val database: AppDatabase,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) : UserRuleRepository {

    private val queries = database.user_rulesQueries

    override suspend fun getAll(): List<UserRule> = withContext(dispatcher) {
        queries.getAll().executeAsList().map {
            UserRule(
                merchantNormalized = it.merchant_normalized,
                category = it.category,
                updatedAt = it.updated_at,
                source = try { RuleSource.valueOf(it.source) } catch (_: Exception) { RuleSource.USER_EXPLICIT }
            )
        }
    }

    override suspend fun getForMerchant(merchantNormalized: String): UserRule? = withContext(dispatcher) {
        queries.getForMerchant(merchantNormalized).executeAsOneOrNull()?.let {
            UserRule(
                merchantNormalized = it.merchant_normalized,
                category = it.category,
                updatedAt = it.updated_at,
                source = try { RuleSource.valueOf(it.source) } catch (_: Exception) { RuleSource.USER_EXPLICIT }
            )
        }
    }

    override suspend fun saveRule(rule: UserRule): Unit = withContext(dispatcher) {
        queries.upsertRule(
            merchant_normalized = rule.merchantNormalized,
            category = rule.category,
            updated_at = rule.updatedAt,
            source = rule.source.name
        )
    }

    override suspend fun deleteRule(merchantNormalized: String): Unit = withContext(dispatcher) {
        queries.deleteForMerchant(merchantNormalized)
    }

    override suspend fun deleteAll(): Unit = withContext(dispatcher) {
        queries.deleteAll()
    }
}
