// NO-NETWORK
package com.cashbuddy.data.repository

import com.cashbuddy.db.AppDatabase
import com.cashbuddy.domain.repository.LearnedTokenRepository
import com.cashbuddy.domain.repository.TokenStats
import com.cashbuddy.platform.currentTimeMillis
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * SQLDelight implementation of [LearnedTokenRepository] storing token frequencies
 * in the encrypted local `learned_token` table.
 */
class LearnedTokenRepositoryImpl(
    private val database: AppDatabase,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) : LearnedTokenRepository {

    private val queries = database.learned_tokenQueries

    override suspend fun record(isTransaction: Boolean, tokens: Set<String>): Unit = withContext(dispatcher) {
        if (tokens.isEmpty()) return@withContext
        val now = currentTimeMillis()
        queries.transaction {
            for (token in tokens) {
                if (isTransaction) {
                    queries.incrementTransaction(
                        token = token,
                        first_seen = now,
                        last_updated = now
                    )
                } else {
                    queries.incrementNonTransaction(
                        token = token,
                        first_seen = now,
                        last_updated = now
                    )
                }
            }
        }
    }

    override suspend fun getAllSignificant(): Map<String, TokenStats> = withContext(dispatcher) {
        getAllSignificantSync()
    }

    override fun getAllSignificantSync(): Map<String, TokenStats> {
        return try {
            queries.getSignificant().executeAsList().associate { row ->
                row.token to TokenStats(
                    token = row.token,
                    transactionCount = row.transaction_count.toInt(),
                    nonTransactionCount = row.non_transaction_count.toInt()
                )
            }
        } catch (_: Throwable) {
            emptyMap()
        }
    }

    override suspend fun clear(): Unit = withContext(dispatcher) {
        queries.deleteAll()
    }
}
