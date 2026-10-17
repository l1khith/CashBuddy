// NO-NETWORK
package com.cashbuddy.domain.repository

import com.cashbuddy.domain.model.RawTrainingData
import com.cashbuddy.domain.model.UserCorrection

/**
 * Keyset-based pagination controller for Raw Training Data notifications.
 */
class RawTrainingDataPager(
    private val repository: TrainingDataRepository,
    val pageSize: Long = 50L
) {
    var cursorTimestamp: Long? = null
        private set
    var cursorId: Long? = null
        private set
    var isEndReached: Boolean = false
        private set

    suspend fun loadNextPage(limit: Long = pageSize): List<RawTrainingData> {
        if (isEndReached) return emptyList()
        val results = repository.getRawPage(cursorTimestamp, cursorId, limit)
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

/**
 * Keyset-based pagination controller for User Corrections.
 */
class UserCorrectionPager(
    private val repository: TrainingDataRepository,
    val pageSize: Long = 50L
) {
    var cursorTimestamp: Long? = null
        private set
    var cursorId: Long? = null
        private set
    var isEndReached: Boolean = false
        private set

    suspend fun loadNextPage(limit: Long = pageSize): List<UserCorrection> {
        if (isEndReached) return emptyList()
        val results = repository.getCorrectionsPage(cursorTimestamp, cursorId, limit)
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
