// NO-NETWORK
package com.cashbuddy.domain.repository

import com.cashbuddy.domain.model.RecurringPattern
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for managing cached recurring transaction patterns.
 */
interface RecurringPatternRepository {
    /**
     * Upserts a collection of patterns into the cache table.
     */
    suspend fun upsertAll(patterns: List<RecurringPattern>)

    /**
     * Streams all active recurring patterns ordered by most recently updated.
     */
    fun getAll(): Flow<List<RecurringPattern>>

    /**
     * Retrieves all active patterns as a list directly.
     */
    suspend fun getAllList(): List<RecurringPattern>

    /**
     * Finds all patterns matching normalized merchant name.
     */
    suspend fun findByMerchant(merchant: String): List<RecurringPattern>

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
}
