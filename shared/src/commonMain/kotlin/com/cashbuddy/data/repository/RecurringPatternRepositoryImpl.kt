// NO-NETWORK
package com.cashbuddy.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.cashbuddy.db.AppDatabase
import com.cashbuddy.domain.model.RecurringPattern
import com.cashbuddy.domain.repository.RecurringPatternRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * SQLDelight backed implementation of [RecurringPatternRepository].
 */
class RecurringPatternRepositoryImpl(
    private val db: AppDatabase,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) : RecurringPatternRepository {

    private val queries = db.recurring_patternQueries

    private fun mapRow(
        id: Long,
        merchantNormalized: String,
        amountPaise: Long,
        occurrenceCount: Long,
        medianIntervalHours: Double,
        cv: Double,
        periodHours: Long,
        autocorrelationPeak: Double,
        firstSeen: Long,
        lastSeen: Long,
        lastUpdated: Long
    ): RecurringPattern = RecurringPattern(
        id = id,
        merchantNormalized = merchantNormalized,
        amountPaise = amountPaise,
        occurrenceCount = occurrenceCount.toInt(),
        medianIntervalHours = medianIntervalHours,
        cv = cv,
        periodHours = periodHours.toInt(),
        autocorrelationPeak = autocorrelationPeak,
        firstSeen = firstSeen,
        lastSeen = lastSeen,
        lastUpdated = lastUpdated
    )

    override suspend fun upsert(pattern: RecurringPattern): Unit = withContext(dispatcher) {
        queries.upsert(
            merchantNormalized = pattern.merchantNormalized.lowercase().trim(),
            amountPaise = pattern.amountPaise,
            occurrenceCount = pattern.occurrenceCount.toLong(),
            medianIntervalHours = pattern.medianIntervalHours,
            cv = pattern.cv,
            periodHours = pattern.periodHours.toLong(),
            autocorrelationPeak = pattern.autocorrelationPeak,
            firstSeen = pattern.firstSeen,
            lastSeen = pattern.lastSeen,
            lastUpdated = pattern.lastUpdated
        )
    }

    override suspend fun upsertAll(patterns: List<RecurringPattern>): Unit = withContext(dispatcher) {
        db.transaction {
            for (p in patterns) {
                queries.upsert(
                    merchantNormalized = p.merchantNormalized.lowercase().trim(),
                    amountPaise = p.amountPaise,
                    occurrenceCount = p.occurrenceCount.toLong(),
                    medianIntervalHours = p.medianIntervalHours,
                    cv = p.cv,
                    periodHours = p.periodHours.toLong(),
                    autocorrelationPeak = p.autocorrelationPeak,
                    firstSeen = p.firstSeen,
                    lastSeen = p.lastSeen,
                    lastUpdated = p.lastUpdated
                )
            }
        }
    }

    override fun getAll(): Flow<List<RecurringPattern>> {
        return queries.getAll(::mapRow)
            .asFlow()
            .mapToList(dispatcher)
    }

    override suspend fun findByMerchantAndAmount(
        merchant: String,
        amountPaise: Long
    ): RecurringPattern? = withContext(dispatcher) {
        queries.findByMerchantAndAmount(
            merchantNormalized = merchant.lowercase().trim(),
            amountPaise = amountPaise,
            mapper = ::mapRow
        ).executeAsOneOrNull()
    }

    override suspend fun deleteById(id: Long): Unit = withContext(dispatcher) {
        queries.deleteById(id)
    }

    override suspend fun deleteAll(): Unit = withContext(dispatcher) {
        queries.deleteAll()
    }

    override suspend fun deleteStale(staleBefore: Long): Unit = withContext(dispatcher) {
        queries.deleteStale(staleBefore)
    }
}
