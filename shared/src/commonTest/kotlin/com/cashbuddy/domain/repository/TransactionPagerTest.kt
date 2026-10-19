// NO-NETWORK
package com.cashbuddy.domain.repository

import com.cashbuddy.domain.model.Transaction
import com.cashbuddy.domain.model.TransactionStatus
import com.cashbuddy.domain.model.TransactionType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TransactionPagerTest {

    private fun createTx(id: Long, timestamp: Long, merchant: String = "Merchant $id"): Transaction {
        return Transaction(
            id = id,
            amount = 100.0,
            type = TransactionType.DEBIT,
            currency = "INR",
            merchant = merchant,
            categoryId = 1L,
            accountId = null,
            sourceApp = "TEST",
            rawText = "Raw $id",
            confidence = 1.0f,
            status = TransactionStatus.CONFIRMED,
            notes = null,
            timestamp = timestamp
        )
    }

    private class FakeTransactionRepo(private val allTxs: List<Transaction>) : TransactionRepository {
        override fun getAll(): Flow<List<Transaction>> = emptyFlow()
        override fun getById(id: Long): Flow<Transaction?> = emptyFlow()
        override fun getPending(): Flow<List<Transaction>> = emptyFlow()
        override fun getRecent(limit: Long): Flow<List<Transaction>> = emptyFlow()
        override fun getByDateRange(start: Long, end: Long): Flow<List<Transaction>> = emptyFlow()
        override fun getByDateRangeWithLimit(start: Long, end: Long, limit: Long): Flow<List<Transaction>> = emptyFlow()
        override fun getSummaryByDateRange(start: Long, end: Long) = emptyFlow<com.cashbuddy.domain.model.DateRangeSummary>()
        override fun getByCategory(categoryId: Long): Flow<List<Transaction>> = emptyFlow()
        override fun getMonthlySummary(): Flow<List<MonthlySummary>> = emptyFlow()
        override fun getCategoryBreakdown(start: Long, end: Long): Flow<List<CategoryBreakdown>> = emptyFlow()
        override suspend fun insert(transaction: Transaction): Long = 0L
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

        override suspend fun getHistoryPage(
            searchQuery: String?,
            type: String?,
            categoryId: Long?,
            startTime: Long?,
            endTime: Long?,
            minAmount: Double?,
            maxAmount: Double?,
            cursorTimestamp: Long?,
            cursorId: Long?,
            limit: Long
        ): List<Transaction> {
            return allTxs
                .filter { tx ->
                    if (searchQuery != null && !tx.merchant.contains(searchQuery, ignoreCase = true)) return@filter false
                    if (cursorTimestamp != null && cursorId != null) {
                        if (tx.timestamp > cursorTimestamp) return@filter false
                        if (tx.timestamp == cursorTimestamp && tx.id >= cursorId) return@filter false
                    }
                    true
                }
                .sortedWith(compareByDescending<Transaction> { it.timestamp }.thenByDescending { it.id })
                .take(limit.toInt())
        }
    }

    @Test
    fun testKeysetPaginationAcrossMultiplePages() = runBlocking {
        // 12 transactions: ids 1..12 with timestamps 1000..12000
        val items = (1..12).map { id -> createTx(id.toLong(), timestamp = id * 1000L) }
        val repo = FakeTransactionRepo(items)
        val pager = TransactionPager(repo, pageSize = 5L)

        // Page 1: top 5 (timestamps 12000 down to 8000)
        val page1 = pager.loadNextPage()
        assertEquals(5, page1.size)
        assertEquals(12L, page1.first().id)
        assertEquals(8L, page1.last().id)
        assertFalse(pager.isEndReached)
        assertEquals(8000L, pager.cursorTimestamp)
        assertEquals(8L, pager.cursorId)

        // Page 2: next 5 (timestamps 7000 down to 3000)
        val page2 = pager.loadNextPage()
        assertEquals(5, page2.size)
        assertEquals(7L, page2.first().id)
        assertEquals(3L, page2.last().id)
        assertFalse(pager.isEndReached)

        // Page 3: next 2 (timestamps 2000 and 1000) -> reaches end
        val page3 = pager.loadNextPage()
        assertEquals(2, page3.size)
        assertEquals(2L, page3.first().id)
        assertEquals(1L, page3.last().id)
        assertTrue(pager.isEndReached)

        // Page 4: empty
        val page4 = pager.loadNextPage()
        assertTrue(page4.isEmpty())

        // Reset
        pager.reset()
        assertFalse(pager.isEndReached)
        val page1AfterReset = pager.loadNextPage()
        assertEquals(5, page1AfterReset.size)
        assertEquals(12L, page1AfterReset.first().id)
    }
}
