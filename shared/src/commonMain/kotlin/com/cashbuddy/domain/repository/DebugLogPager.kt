// NO-NETWORK
package com.cashbuddy.domain.repository

import com.cashbuddy.domain.model.DebugLogEntry
import com.cashbuddy.domain.model.DebugLogFilter

/**
 * Keyset-based pagination controller for Debug Logs.
 * Uses (timestamp, id) compound cursor to stream logs with zero offset degradation.
 */
class DebugLogPager(
    private val repository: DebugLogRepository,
    val pageSize: Long = 50L
) {
    var cursorTimestamp: Long? = null
        private set

    var cursorId: String? = null
        private set

    var isEndReached: Boolean = false
        private set

    suspend fun loadNextPage(
        filter: DebugLogFilter = DebugLogFilter(),
        limit: Long = pageSize
    ): List<DebugLogEntry> {
        if (isEndReached) return emptyList()

        val results = repository.filterLogsPage(
            filter = filter,
            cursorTimestamp = cursorTimestamp,
            cursorId = cursorId,
            limit = limit
        )

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

    fun reset() {
        cursorTimestamp = null
        cursorId = null
        isEndReached = false
    }
}
