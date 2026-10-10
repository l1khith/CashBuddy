// NO-NETWORK
package com.cashbuddy.core

import com.cashbuddy.domain.model.DateRangeSummary
import com.cashbuddy.domain.model.Transaction
import com.cashbuddy.domain.model.TransactionStatus
import com.cashbuddy.domain.model.TransactionType
import com.cashbuddy.domain.repository.CategoryBreakdown
import com.cashbuddy.domain.repository.MergeLogEntry
import com.cashbuddy.domain.repository.MonthlySummary
import com.cashbuddy.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FraudDetectorTest {

    private class FakeTransactionRepo(var amountsToReturn: List<Double> = emptyList()) : TransactionRepository {
        override fun getAll(): Flow<List<Transaction>> = emptyFlow()
        override fun getById(id: Long): Flow<Transaction?> = emptyFlow()
        override fun getPending(): Flow<List<Transaction>> = emptyFlow()
        override fun getRecent(limit: Long): Flow<List<Transaction>> = emptyFlow()
        override fun getByDateRange(start: Long, end: Long): Flow<List<Transaction>> = emptyFlow()
        override fun getByDateRangeWithLimit(start: Long, end: Long, limit: Long): Flow<List<Transaction>> = emptyFlow()
        override fun getSummaryByDateRange(start: Long, end: Long): Flow<DateRangeSummary> = emptyFlow()
        override fun getByCategory(categoryId: Long): Flow<List<Transaction>> = emptyFlow()
        override fun getMonthlySummary(): Flow<List<MonthlySummary>> = emptyFlow()
        override fun getCategoryBreakdown(start: Long, end: Long): Flow<List<CategoryBreakdown>> = emptyFlow()
        override suspend fun insert(transaction: Transaction): Long = 1L
        override suspend fun updateStatus(id: Long, status: TransactionStatus) {}
        override suspend fun update(transaction: Transaction) {}
        override suspend fun deleteById(id: Long) {}
        override fun getBalance(): Flow<Double> = emptyFlow()
        override fun getAverageAmount(): Flow<Double> = emptyFlow()
        override fun getCount(): Flow<Long> = emptyFlow()
        override suspend fun findDuplicateCandidates(): List<Transaction> = emptyList()
        override suspend fun markMerged(id: Long, survivorId: Long) {}
        override suspend fun unmarkMerged(id: Long) {}
        override suspend fun insertMergeLog(survivorId: Long, mergedId: Long, timestamp: Long) {}
        override suspend fun getMergedTransactions(survivorId: Long): List<Transaction> = emptyList()
        override suspend fun deleteMergeLog(survivorId: Long) {}
        override suspend fun getRecentMergeLogs(): List<MergeLogEntry> = emptyList()
        override suspend fun getMergeLogCount(): Long = 0L
        override suspend fun sumByCategory(category: String, startTime: Long, endTime: Long): Double = 0.0
        override fun getByCategoryAndPeriod(category: String, startTime: Long, endTime: Long): Flow<List<Transaction>> = emptyFlow()
        override suspend fun sumAll(startTime: Long, endTime: Long, type: TransactionType): Double = 0.0

        override suspend fun getAmountsAtMerchant(
            merchant: String,
            fromTimestamp: Long,
            toTimestamp: Long
        ): List<Double> = amountsToReturn
    }

    @Test
    fun testInsufficientSamplesNotAnomalous() = runBlocking {
        // 5 samples around ₹300, new ₹3000 -> not anomalous (n < 10)
        val repo = FakeTransactionRepo(listOf(290.0, 300.0, 310.0, 295.0, 305.0))
        val detector = FraudDetector(repo)

        val result = detector.checkAnomaly("Swiggy", 3000.0, 1727600000000L)
        assertFalse(result.isAnomalous, "n < 10 should never be marked anomalous")
        assertTrue(result.sampleSize == 5)
    }

    @Test
    fun testTwelveSamplesOutlierIsAnomalous() = runBlocking {
        // 12 samples around ₹300, new ₹3000 -> anomalous
        val repo = FakeTransactionRepo(
            listOf(280.0, 290.0, 295.0, 300.0, 300.0, 300.0, 300.0, 300.0, 305.0, 310.0, 315.0, 320.0)
        )
        val detector = FraudDetector(repo)

        val result = detector.checkAnomaly("Swiggy", 3000.0, 1727600000000L)
        assertTrue(result.isAnomalous, "₹3000 against ₹300 median with n=12 should be anomalous")
        assertTrue(result.score > 3.5, "M_i score must be > 3.5")
    }

    @Test
    fun testTwelveSamplesNormalAmountNotAnomalous() = runBlocking {
        // 12 samples around ₹300, new ₹320 -> not anomalous
        val repo = FakeTransactionRepo(
            listOf(280.0, 290.0, 295.0, 300.0, 300.0, 300.0, 300.0, 300.0, 305.0, 310.0, 315.0, 320.0)
        )
        val detector = FraudDetector(repo)

        val result = detector.checkAnomaly("Swiggy", 320.0, 1727600000000L)
        assertFalse(result.isAnomalous, "₹320 against ₹300 median should not be anomalous")
        assertTrue(result.score <= 3.5, "M_i score should be <= 3.5")
    }

    @Test
    fun testZeroMadNotAnomalous() = runBlocking {
        // 12 samples all equal to ₹300, new ₹500 -> not anomalous (MAD = 0)
        val repo = FakeTransactionRepo(List(12) { 300.0 })
        val detector = FraudDetector(repo)

        val result = detector.checkAnomaly("Subscription", 500.0, 1727600000000L)
        assertFalse(result.isAnomalous, "Zero MAD should never be marked anomalous")
        assertTrue(result.mad == 0.0)
    }
}
