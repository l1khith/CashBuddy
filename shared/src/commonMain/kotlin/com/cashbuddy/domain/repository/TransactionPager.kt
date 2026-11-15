// NO-NETWORK
package com.cashbuddy.domain.repository

import com.cashbuddy.domain.model.Transaction

/**
 * Keyset-based pagination controller for financial transactions.
 * Uses (timestamp, id) compound cursor to guarantee stable, O(1) page lookups
 * without offset degradation or skipping during real-time insertions.
 */
class TransactionPager(
    private val repository: TransactionRepository,
    val pageSize: Long = 50L
) {
    var cursorTimestamp: Long? = null
        private set

    var cursorId: Long? = null
        private set

    var isEndReached: Boolean = false
        private set

    suspend fun loadNextPage(
        searchQuery: String? = null,
        type: String? = null,
        categoryId: Long? = null,
        startTime: Long? = null,
        endTime: Long? = null,
        minAmount: Double? = null,
        maxAmount: Double? = null,
        limit: Long = pageSize
    ): List<Transaction> {
        if (isEndReached) return emptyList()

        val results = repository.getHistoryPage(
            searchQuery = searchQuery,
            type = type,
            categoryId = categoryId,
            startTime = startTime,
            endTime = endTime,
            minAmount = minAmount,
            maxAmount = maxAmount,
            cursorTimestamp = cursorTimestamp,
            cursorId = cursorId,
            limit = limit
        )
        return applyPageResult(results, limit)
    }

    /**
     * Clears cursor state and reloads the currently loaded window (at least [pageSize] items)
     * so deletes, edits, and inserts are reflected without losing scroll depth.
     */
    suspend fun reload(
        searchQuery: String? = null,
        type: String? = null,
        categoryId: Long? = null,
        startTime: Long? = null,
        endTime: Long? = null,
        minAmount: Double? = null,
        maxAmount: Double? = null,
        currentCount: Int = pageSize.toInt()
    ): List<Transaction> {
        reset()
        val limit = maxOf(pageSize, currentCount.toLong())
        val results = repository.getHistoryPage(
            searchQuery = searchQuery,
            type = type,
            categoryId = categoryId,
            startTime = startTime,
            endTime = endTime,
            minAmount = minAmount,
            maxAmount = maxAmount,
            cursorTimestamp = null,
            cursorId = null,
            limit = limit
        )
        return applyPageResult(results, limit)
    }

    private fun applyPageResult(results: List<Transaction>, limit: Long): List<Transaction> {
        if (results.isNotEmpty()) {
            val last = results.last()
            cursorTimestamp = last.timestamp
            cursorId = last.id
        }
        if (results.size < limit) {
            isEndReached = true
        }
        return results
    }

    fun invalidate() = reset()

    fun reset() {
        cursorTimestamp = null
        cursorId = null
        isEndReached = false
    }
}
