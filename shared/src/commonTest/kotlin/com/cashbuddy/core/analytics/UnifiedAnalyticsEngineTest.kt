// NO-NETWORK
package com.cashbuddy.core.analytics

import com.cashbuddy.core.AnomalyResult
import com.cashbuddy.core.FraudDetector
import com.cashbuddy.core.budget.BudgetPacer
import com.cashbuddy.domain.model.DateRangeSummary
import com.cashbuddy.domain.model.Transaction
import com.cashbuddy.domain.model.TransactionStatus
import com.cashbuddy.domain.model.TransactionType
import com.cashbuddy.domain.repository.CategoryBreakdown
import com.cashbuddy.domain.repository.MergeLogEntry
import com.cashbuddy.domain.repository.MonthlySummary
import com.cashbuddy.domain.repository.RecurringPatternRepository
import com.cashbuddy.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class UnifiedAnalyticsEngineTest {

    private class MockTransactionRepo(var amounts: List<Double> = emptyList()) : TransactionRepository {
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
        ): List<Double> = amounts
    }

    // ================================================================
    // PART 1 — VERIFICATION: Budget Pacing (Linear Regression)
    // ================================================================
    @Test
    fun testRegressionForecastsMonthEndTotal() {
        // Transactions average ₹500/day over 10 days (total spent: ₹5,000)
        val spentSoFar = 5000.0
        val dayOfMonth = 10
        val totalDaysInMonth = 30

        val dailyAvg = BudgetPacer.calculateDailyAverage(spentSoFar, dayOfMonth)
        assertEquals(500.0, dailyAvg, "Daily average should be ₹500/day")

        val projectedTotal = BudgetPacer.projectMonthEndTotal(dailyAvg, totalDaysInMonth)
        assertEquals(15000.0, projectedTotal, "Projected month-end spend should be ₹15,000 for a 30-day month")

        // Exceeding Food budget of ₹13,800
        val warning = BudgetPacer.checkBudgetWarning(
            spentSoFar = spentSoFar,
            budgetLimit = 13800.0,
            daysElapsed = dayOfMonth,
            totalDaysInMonth = totalDaysInMonth,
            category = "Food"
        )
        assertTrue(warning.isExceeded, "Warning must trigger when projected total exceeds budget limit")
        assertEquals(1200.0, warning.overage, "Overage must be ₹1,200")
        assertNotNull(warning.warningMessage)
        assertTrue(
            warning.warningMessage!!.contains("Food") && warning.warningMessage!!.contains("1200"),
            "Message should match: 'At this rate, you will exceed your Food budget by ₹1,200.'"
        )
    }

    // ================================================================
    // PART 2 — VERIFICATION: Histogram Percentile Anomaly Detection
    // ================================================================
    @Test
    fun testHistogramFlagsUnusualSpendOutlier() = runBlocking {
        // Insert 20 Swiggy orders around ₹300 (e.g., between ₹280 and ₹320)
        val swiggyOrders = listOf(
            280.0, 285.0, 290.0, 292.0, 295.0, 298.0, 300.0, 300.0, 302.0, 305.0,
            305.0, 308.0, 310.0, 310.0, 312.0, 315.0, 315.0, 318.0, 320.0, 320.0
        )
        val repo = MockTransactionRepo(swiggyOrders)
        val detector = FraudDetector(repo, windowDays = 30)

        // Evaluate one ₹5,000 order
        val result = detector.checkAnomaly("Swiggy", 5000.0, 1727600000000L)

        assertTrue(result.isAnomalous, "₹5,000 against ₹300 orders must be flagged as anomalous")
        assertTrue(5000.0 > result.p95, "₹5,000 must exceed P95 percentile bucket")
        assertNotNull(result.warningMessage, "Warning message must be generated")
        assertTrue(
            result.warningMessage!!.contains("Swiggy") && result.warningMessage!!.contains("5000"),
            "Message should explain unusual spend at Swiggy"
        )
    }

    // ================================================================
    // PART 3 — VERIFICATION: Point Process Recurrence Detection
    // ================================================================
    @Test
    fun testPointProcessIdentifiesDailyPattern() {
        val baseTime = 1727600000000L
        val hourMs = 3600_000L

        // Insert 10 BMTC transactions exactly 24 hours apart
        val bmtcTransactions = (0 until 10).map { i ->
            Transaction(
                id = i.toLong(),
                accountId = 1L,
                categoryId = 1L,
                amount = 18.0,
                type = TransactionType.DEBIT,
                status = TransactionStatus.PENDING,
                rawText = "BMTC bus pass",
                sourceApp = "com.phonepe.app",
                merchant = "BMTC",
                confidence = 0.95f,
                timestamp = baseTime + (i * 24L * hourMs),
                createdAt = baseTime + (i * 24L * hourMs),
                updatedAt = baseTime + (i * 24L * hourMs)
            )
        }

        // Test Point Process IAT analyzer directly
        val timestamps = bmtcTransactions.map { it.timestamp }
        val iatResult = RecurrenceDetector.analyzeIAT(timestamps)

        assertTrue(iatResult.isRecurring, "10 transactions 24h apart must be recurring under Point Process")
        assertEquals(24.0, iatResult.meanIATHours, "Mean IAT must be 24.0 hours")
        assertEquals(0.0, iatResult.varianceIATHours, "Variance of exact 24h IAT must be 0.0")
        assertEquals(0.0, iatResult.fanoFactor, "Fano factor must be 0.0 (< 0.3)")
        assertEquals(24, iatResult.detectedPeriodHours, "Detected period must be 24h (daily)")

        // Test end-to-end RecurrenceDetector.detect()
        val patterns = RecurrenceDetector.detect(bmtcTransactions, now = bmtcTransactions.last().timestamp)
        assertEquals(1, patterns.size, "Must detect exactly 1 recurring pattern")
        val pattern = patterns.first()
        assertEquals("bmtc", pattern.merchantNormalized)
        assertEquals(1800L, pattern.amountPaise)
        assertEquals(24, pattern.periodHours, "Pattern period must be 24h (daily)")
        assertEquals(10, pattern.occurrenceCount)
    }
}
