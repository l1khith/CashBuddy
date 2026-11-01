// NO-NETWORK
package com.cashbuddy.data.repository

import com.cashbuddy.core.prob.ConcurrentHashMap
import com.cashbuddy.db.AppDatabase
import com.cashbuddy.domain.repository.LearnedTokenRepository
import com.cashbuddy.domain.repository.TokenStats
import com.cashbuddy.platform.currentTimeMillis
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * SQLDelight implementation of [LearnedTokenRepository] storing token frequencies
 * in the encrypted local `learned_token` table with an in-memory L1 cache.
 */
class LearnedTokenRepositoryImpl(
    private val database: AppDatabase,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) : LearnedTokenRepository {

    private val queries = database.learned_tokenQueries
    private val cache = ConcurrentHashMap<String, TokenStats>()
    private var isCachePreloaded = false

    companion object {
        private const val MAX_CACHE_SIZE = 2048
    }

    private fun ensurePreload() {
        if (isCachePreloaded) return
        try {
            val topTokens = queries.getTopSignificant(limit = MAX_CACHE_SIZE.toLong()).executeAsList()
            for (row in topTokens) {
                cache[row.token] = TokenStats(
                    token = row.token,
                    transactionCount = row.transaction_count.toInt(),
                    nonTransactionCount = row.non_transaction_count.toInt()
                )
            }
            isCachePreloaded = true
        } catch (_: Throwable) {
            // Can retry on next access if table was not ready
        }
    }

    private fun putIntoCacheWithCap(token: String, stats: TokenStats) {
        if (cache.size >= MAX_CACHE_SIZE && !cache.containsKey(token)) {
            // Simple eviction: remove any entry to maintain memory cap
            val firstKey = cache.keys.firstOrNull()
            if (firstKey != null) {
                cache.remove(firstKey)
            }
        }
        cache[token] = stats
    }

    override suspend fun record(isTransaction: Boolean, tokens: Set<String>): Unit = withContext(dispatcher) {
        if (tokens.isEmpty()) return@withContext
        val now = currentTimeMillis()

        // 1. Update in-memory cache
        for (token in tokens) {
            val existing = cache[token]
            val newStats = if (existing != null) {
                if (isTransaction) {
                    existing.copy(transactionCount = existing.transactionCount + 1)
                } else {
                    existing.copy(nonTransactionCount = existing.nonTransactionCount + 1)
                }
            } else {
                TokenStats(
                    token = token,
                    transactionCount = if (isTransaction) 1 else 0,
                    nonTransactionCount = if (isTransaction) 0 else 1
                )
            }
            putIntoCacheWithCap(token, newStats)
        }

        // 2. Persist to database
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

    override suspend fun getTokenStats(tokens: Set<String>): Map<String, TokenStats> = withContext(dispatcher) {
        getTokenStatsSync(tokens)
    }

    override fun getTokenStatsSync(tokens: Set<String>): Map<String, TokenStats> {
        if (tokens.isEmpty()) return emptyMap()
        ensurePreload()

        val result = mutableMapOf<String, TokenStats>()
        val missingFromCache = mutableListOf<String>()

        for (token in tokens) {
            val cached = cache[token]
            if (cached != null) {
                if (cached.transactionCount + cached.nonTransactionCount >= 5) {
                    result[token] = cached
                }
            } else {
                missingFromCache.add(token)
            }
        }

        if (missingFromCache.isNotEmpty()) {
            try {
                val dbRows = queries.getByTokens(missingFromCache).executeAsList()
                for (row in dbRows) {
                    val stats = TokenStats(
                        token = row.token,
                        transactionCount = row.transaction_count.toInt(),
                        nonTransactionCount = row.non_transaction_count.toInt()
                    )
                    putIntoCacheWithCap(row.token, stats)
                    if (stats.transactionCount + stats.nonTransactionCount >= 5) {
                        result[row.token] = stats
                    }
                }
            } catch (_: Throwable) {
                // Return cache matches on DB query error
            }
        }

        return result
    }

    override suspend fun getAllSignificant(): Map<String, TokenStats> = withContext(dispatcher) {
        getAllSignificantSync()
    }

    override fun getAllSignificantSync(): Map<String, TokenStats> {
        ensurePreload()
        return try {
            queries.getSignificant().executeAsList().associate { row ->
                val stats = TokenStats(
                    token = row.token,
                    transactionCount = row.transaction_count.toInt(),
                    nonTransactionCount = row.non_transaction_count.toInt()
                )
                putIntoCacheWithCap(row.token, stats)
                row.token to stats
            }
        } catch (_: Throwable) {
            emptyMap()
        }
    }

    override suspend fun purgeExcessTokens(maxCount: Long): Int = withContext(dispatcher) {
        val totalCount = queries.getCount().executeAsOne()
        if (totalCount > maxCount) {
            val excess = totalCount - maxCount
            queries.purgeOldestLeastUsed(excess)
            cache.clear()
            isCachePreloaded = false
            ensurePreload()
            excess.toInt()
        } else {
            0
        }
    }

    override suspend fun clear(): Unit = withContext(dispatcher) {
        cache.clear()
        queries.deleteAll()
    }
}
