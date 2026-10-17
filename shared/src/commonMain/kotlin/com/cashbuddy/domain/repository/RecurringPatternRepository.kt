// NO-NETWORK
package com.cashbuddy.domain.repository

import com.cashbuddy.domain.model.RecurringPattern
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for managing cached recurring transaction patterns.
 */
interface RecurringPatternRepository {
    /**
     * Upserts a single pattern into the cache table.
     */
    suspend fun upsert(pattern: RecurringPattern)

    /**
     * Upserts a collection of patterns into the cache table.
     */
    suspend fun upsertAll(patterns: List<RecurringPattern>)

    /**
     * Streams all active recurring patterns ordered by most recently updated.
     */
    fun getAll(): Flow<List<RecurringPattern>>

    /**
     * Finds a pattern matching normalized merchant name and amount in paise.
     */
    suspend fun findByMerchantAndAmount(merchant: String, amountPaise: Long): RecurringPattern?

    /**
     * Deletes a specific pattern by id.
     */
    suspend fun deleteById(id: Long)

    /**
     * Clears all recurring patterns (e.g. on full data reset).
     */
    suspend fun deleteAll()

    /**
     * Removes stale patterns not seen or updated before [staleBefore] timestamp.
     */
    suspend fun deleteStale(staleBefore: Long)
}
