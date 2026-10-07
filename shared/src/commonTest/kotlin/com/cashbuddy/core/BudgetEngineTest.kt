// NO-NETWORK
package com.cashbuddy.core

import com.cashbuddy.core.budget.BudgetEngine
import com.cashbuddy.domain.model.AlertState
import com.cashbuddy.domain.model.Budget
import com.cashbuddy.domain.model.BudgetPeriod
import com.cashbuddy.domain.model.Transaction
import com.cashbuddy.domain.model.TransactionStatus
import com.cashbuddy.domain.model.TransactionType
import com.cashbuddy.domain.repository.CategoryBreakdown
import com.cashbuddy.domain.repository.MergeLogEntry
import com.cashbuddy.domain.repository.MonthlySummary
import com.cashbuddy.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BudgetEngineTest {

    private class FakeTransactionRepo : TransactionRepository {
        val txs = mutableListOf<Transaction>()

        override suspend fun sumByCategory(category: String, startTime: Long, endTime: Long): Double =
            txs.filter {
                (it.categoryName == category || it.categoryId.toString() == category) &&
                it.type == TransactionType.DEBIT &&
                it.timestamp in startTime..endTime &&
                !it.isMerged
            }.sumOf { it.amount }

        override fun getByCategoryAndPeriod(category: String, startTime: Long, endTime: Long): Flow<List<Transaction>> =
            flowOf(txs.filter {
                (it.categoryName == category || it.categoryId.toString() == category) &&
                it.type == TransactionType.DEBIT &&
                it.timestamp in startTime..endTime &&
                !it.isMerged
            })

        override fun getAll(): Flow<List<Transaction>> = flowOf(txs.filter { !it.isMerged })
        override fun getById(id: Long): Flow<Transaction?> = flowOf(txs.find { it.id == id })
        override fun getPending(): Flow<List<Transaction>> = flowOf(emptyList())
        override fun getRecent(limit: Long): Flow<List<Transaction>> = flowOf(emptyList())
        override fun getByDateRange(start: Long, end: Long): Flow<List<Transaction>> = flowOf(emptyList())
        override fun getByDateRangeWithLimit(start: Long, end: Long, limit: Long): Flow<List<Transaction>> = flowOf(emptyList())
        override fun getSummaryByDateRange(start: Long, end: Long): Flow<com.cashbuddy.domain.model.DateRangeSummary> =
            flowOf(com.cashbuddy.domain.model.DateRangeSummary(0.0, 0.0, 0L))
        override fun getByCategory(categoryId: Long): Flow<List<Transaction>> = flowOf(emptyList())
        override fun getMonthlySummary(): Flow<List<MonthlySummary>> = flowOf(emptyList())
        override fun getCategoryBreakdown(start: Long, end: Long): Flow<List<CategoryBreakdown>> = flowOf(emptyList())
        override suspend fun insert(transaction: Transaction): Long {
            val id = (txs.size + 1).toLong()
            txs.add(transaction.copy(id = id))
            return id
        }
        override suspend fun update(transaction: Transaction) {}
        override suspend fun updateStatus(id: Long, status: TransactionStatus) {}
        override suspend fun deleteById(id: Long) {}
        override fun getBalance(): Flow<Double> = flowOf(0.0)
        override fun getAverageAmount(): Flow<Double> = flowOf(0.0)
        override fun getCount(): Flow<Long> = flowOf(0L)
        override suspend fun findDuplicateCandidates(): List<Transaction> = emptyList()
        override suspend fun markMerged(id: Long, survivorId: Long) {}
        override suspend fun unmarkMerged(id: Long) {}
        override suspend fun insertMergeLog(survivorId: Long, mergedId: Long, timestamp: Long) {}
        override suspend fun getMergedTransactions(survivorId: Long): List<Transaction> = emptyList()
        override suspend fun deleteMergeLog(survivorId: Long) {}
        override suspend fun getRecentMergeLogs(): List<MergeLogEntry> = emptyList()
        override suspend fun getMergeLogCount(): Long = 0L
    }

    private val fixedUtc = TimeZone.UTC
    // October 15, 2026, 12:00:00 UTC = 1792065600000L
    private val oct15_2026 = 1792065600000L

    @Test
    fun testBudgetWithZeroTransactions() = runBlocking {
        val repo = FakeTransactionRepo()
        val engine = BudgetEngine(repo, fixedUtc)

        val budget = Budget(
            id = "b1",
            category = "Food",
            amount = 10000.0,
            period = BudgetPeriod.MONTHLY,
            startDate = 1000L,
            isActive = true,
            createdAt = 1000L,
            updatedAt = 1000L
        )

        val status = engine.statusFor(budget, oct15_2026)
        assertEquals(0.0, status.spent)
        assertEquals(10000.0, status.remaining)
        assertEquals(0.0f, status.percentUsed)
        assertEquals(AlertState.ON_TRACK, status.state)
    }

    @Test
    fun testBudgetAtExactly100Percent() = runBlocking {
        val repo = FakeTransactionRepo()
        val engine = BudgetEngine(repo, fixedUtc)

        val budget = Budget(
            id = "b2",
            category = "Groceries",
            amount = 5000.0,
            period = BudgetPeriod.MONTHLY,
            startDate = 1000L,
            isActive = true,
            createdAt = 1000L,
            updatedAt = 1000L
        )

        repo.insert(
            Transaction(
                id = 1L,
                amount = 5000.0,
                type = TransactionType.DEBIT,
                currency = "INR",
                merchant = "Supermarket",
                categoryId = 1L,
                categoryName = "Groceries",
                sourceApp = "GPAY",
                rawText = "Paid 5000",
                confidence = 1.0f,
                status = TransactionStatus.CONFIRMED,
                timestamp = oct15_2026,
                createdAt = oct15_2026,
                updatedAt = oct15_2026
            )
        )

        val status = engine.statusFor(budget, oct15_2026)
        assertEquals(5000.0, status.spent)
        assertEquals(0.0, status.remaining)
        assertEquals(100.0f, status.percentUsed)
        assertEquals(AlertState.EXCEEDED, status.state)
    }

    @Test
    fun testBudgetCrossingPeriodBoundary() = runBlocking {
        val repo = FakeTransactionRepo()
        val engine = BudgetEngine(repo, fixedUtc)

        val budget = Budget(
            id = "b3",
            category = "Transport",
            amount = 3000.0,
            period = BudgetPeriod.MONTHLY,
            startDate = 1000L,
            isActive = true,
            createdAt = 1000L,
            updatedAt = 1000L
        )

        val octRange = engine.periodRange(BudgetPeriod.MONTHLY, oct15_2026)
        val octStartInstant = Instant.fromEpochMilliseconds(octRange.first).toLocalDateTime(fixedUtc)
        val octEndInstant = Instant.fromEpochMilliseconds(octRange.last).toLocalDateTime(fixedUtc)

        assertEquals(2026, octStartInstant.year)
        assertEquals(10, octStartInstant.monthNumber)
        assertEquals(1, octStartInstant.dayOfMonth)

        assertEquals(2026, octEndInstant.year)
        assertEquals(10, octEndInstant.monthNumber)
        assertEquals(31, octEndInstant.dayOfMonth)

        // Add transaction in September (previous period)
        val sepTimestamp = octRange.first - 1000L
        repo.insert(
            Transaction(
                id = 1L,
                amount = 2500.0,
                type = TransactionType.DEBIT,
                currency = "INR",
                merchant = "Metro",
                categoryId = 2L,
                categoryName = "Transport",
                sourceApp = "GPAY",
                rawText = "Paid 2500",
                confidence = 1.0f,
                status = TransactionStatus.CONFIRMED,
                timestamp = sepTimestamp,
                createdAt = sepTimestamp,
                updatedAt = sepTimestamp
            )
        )

        // In October, previous month spending is not counted
        val statusOct = engine.statusFor(budget, oct15_2026)
        assertEquals(0.0, statusOct.spent)
        assertEquals(AlertState.ON_TRACK, statusOct.state)

        // Add transaction in October
        repo.insert(
            Transaction(
                id = 2L,
                amount = 2500.0,
                type = TransactionType.DEBIT,
                currency = "INR",
                merchant = "Uber",
                categoryId = 2L,
                categoryName = "Transport",
                sourceApp = "GPAY",
                rawText = "Paid 2500",
                confidence = 1.0f,
                status = TransactionStatus.CONFIRMED,
                timestamp = oct15_2026,
                createdAt = oct15_2026,
                updatedAt = oct15_2026
            )
        )

        val statusOctAfter = engine.statusFor(budget, oct15_2026)
        assertEquals(2500.0, statusOctAfter.spent)
        // 2500 / 3000 = 83.33% -> WARNING
        assertEquals(AlertState.WARNING, statusOctAfter.state)

        // Check November timestamp: October spending rolls over (reset to zero for new month)
        val novTimestamp = octRange.last + 1000L
        val statusNov = engine.statusFor(budget, novTimestamp)
        assertEquals(0.0, statusNov.spent)
        assertEquals(AlertState.ON_TRACK, statusNov.state)
    }

    @Test
    fun testBudgetWithMergedTransactionsExcluded() = runBlocking {
        val repo = FakeTransactionRepo()
        val engine = BudgetEngine(repo, fixedUtc)

        val budget = Budget(
            id = "b4",
            category = "Dining",
            amount = 4000.0,
            period = BudgetPeriod.MONTHLY,
            startDate = 1000L,
            isActive = true,
            createdAt = 1000L,
            updatedAt = 1000L
        )

        // Active valid transaction: ₹1,500
        repo.insert(
            Transaction(
                id = 1L,
                amount = 1500.0,
                type = TransactionType.DEBIT,
                currency = "INR",
                merchant = "Cafe",
                categoryId = 3L,
                categoryName = "Dining",
                sourceApp = "GPAY",
                rawText = "Paid 1500",
                confidence = 1.0f,
                status = TransactionStatus.CONFIRMED,
                timestamp = oct15_2026,
                createdAt = oct15_2026,
                updatedAt = oct15_2026,
                isMerged = false
            )
        )

        // Duplicate/merged transaction: ₹3,000 (should be excluded)
        repo.insert(
            Transaction(
                id = 2L,
                amount = 3000.0,
                type = TransactionType.DEBIT,
                currency = "INR",
                merchant = "Cafe Duplicate",
                categoryId = 3L,
                categoryName = "Dining",
                sourceApp = "GPAY",
                rawText = "Paid 3000",
                confidence = 1.0f,
                status = TransactionStatus.CONFIRMED,
                timestamp = oct15_2026,
                createdAt = oct15_2026,
                updatedAt = oct15_2026,
                isMerged = true,
                mergedIntoId = 1L
            )
        )

        val status = engine.statusFor(budget, oct15_2026)
        // Only 1500 should be counted, not 4500
        assertEquals(1500.0, status.spent)
        assertEquals(2500.0, status.remaining)
        assertEquals(AlertState.ON_TRACK, status.state)
    }
}
