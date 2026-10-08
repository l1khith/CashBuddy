// NO-NETWORK
package com.cashbuddy.presentation.budget

import com.cashbuddy.core.budget.BudgetEngine
import com.cashbuddy.domain.model.BudgetPeriod
import com.cashbuddy.domain.model.DateRangeSummary
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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BudgetDrilldownScreenTest {

    private class FakeTxRepo : TransactionRepository {
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

        override fun getAll(): Flow<List<Transaction>> = flowOf(txs)
        override fun getById(id: Long): Flow<Transaction?> = flowOf(txs.find { it.id == id })
        override fun getPending(): Flow<List<Transaction>> = flowOf(emptyList())
        override fun getRecent(limit: Long): Flow<List<Transaction>> = flowOf(emptyList())
        override fun getByDateRange(start: Long, end: Long): Flow<List<Transaction>> = flowOf(emptyList())
        override fun getByDateRangeWithLimit(start: Long, end: Long, limit: Long): Flow<List<Transaction>> = flowOf(emptyList())
        override fun getSummaryByDateRange(start: Long, end: Long): Flow<DateRangeSummary> =
            flowOf(DateRangeSummary(0.0, 0.0, 0L))
        override fun getByCategory(categoryId: Long): Flow<List<Transaction>> = flowOf(emptyList())
        override fun getMonthlySummary(): Flow<List<MonthlySummary>> = flowOf(emptyList())
        override fun getCategoryBreakdown(start: Long, end: Long): Flow<List<CategoryBreakdown>> = flowOf(emptyList())
        override suspend fun insert(transaction: Transaction): Long = 1L
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

    private fun createTx(
        id: Long,
        amount: Double,
        category: String,
        type: TransactionType = TransactionType.DEBIT,
        timestamp: Long,
        isMerged: Boolean = false
    ): Transaction {
        return Transaction(
            id = id,
            amount = amount,
            type = type,
            currency = "INR",
            merchant = "Merchant $id",
            categoryId = 1L,
            accountId = 1L,
            sourceApp = "GPay",
            rawText = "Paid $amount",
            confidence = 0.95f,
            status = TransactionStatus.CONFIRMED,
            notes = null,
            timestamp = timestamp,
            createdAt = timestamp,
            updatedAt = timestamp,
            isMerged = isMerged,
            mergedIntoId = null,
            categoryName = category,
            categoryColor = "#FF0000",
            categoryIcon = "icon",
            accountName = "HDFC",
            accountType = "BANK"
        )
    }

    @Test
    fun testEmptyStateUiState() {
        val state = BudgetDrilldownUiState(
            category = "Food",
            period = BudgetPeriod.MONTHLY,
            transactions = emptyList(),
            totalSpent = 0.0,
            isLoading = false
        )

        assertEquals("Food", state.category)
        assertEquals(BudgetPeriod.MONTHLY, state.period)
        assertTrue(state.transactions.isEmpty())
        assertEquals(0.0, state.totalSpent)
        assertFalse(state.isLoading)
    }

    @Test
    fun testPopulatedUiState() {
        val tx = createTx(id = 1L, amount = 1200.0, category = "Food", timestamp = 1713180000000L)
        val state = BudgetDrilldownUiState(
            category = "Food",
            period = BudgetPeriod.MONTHLY,
            transactions = listOf(tx),
            totalSpent = 1200.0,
            isLoading = false
        )

        assertEquals(1, state.transactions.size)
        assertEquals(1200.0, state.totalSpent)
        assertEquals("Food", state.transactions.first().categoryName)
        assertFalse(state.isLoading)
    }

    @Test
    fun testViewModelLoadsFilteredTransactions() = runBlocking {
        val txRepo = FakeTxRepo()
        val budgetEngine = BudgetEngine(txRepo, TimeZone.UTC)

        // Fixed date: 2024-04-15 12:00:00 UTC = 1713182400000L
        val now = 1713182400000L
        val monthRange = budgetEngine.periodRange(BudgetPeriod.MONTHLY, now)

        // 1. Valid matching debit in period
        txRepo.txs.add(createTx(id = 1L, amount = 1500.0, category = "Food", timestamp = monthRange.first + 1000L))
        // 2. Credit in period (should be excluded)
        txRepo.txs.add(createTx(id = 2L, amount = 800.0, category = "Food", type = TransactionType.CREDIT, timestamp = monthRange.first + 2000L))
        // 3. Merged debit in period (should be excluded)
        txRepo.txs.add(createTx(id = 3L, amount = 300.0, category = "Food", timestamp = monthRange.first + 3000L, isMerged = true))
        // 4. Other category in period (should be excluded)
        txRepo.txs.add(createTx(id = 4L, amount = 500.0, category = "Travel", timestamp = monthRange.first + 4000L))
        // 5. Food debit outside month range (should be excluded)
        txRepo.txs.add(createTx(id = 5L, amount = 999.0, category = "Food", timestamp = monthRange.first - 10000L))

        val vm = BudgetDrilldownViewModel(
            category = "Food",
            periodName = "MONTHLY",
            transactionRepository = txRepo,
            budgetEngine = budgetEngine,
            coroutineScope = this
        )

        vm.loadTransactions(now).join()

        val state = vm.uiState.value
        assertEquals("Food", state.category)
        assertEquals(BudgetPeriod.MONTHLY, state.period)
        assertEquals(1, state.transactions.size)
        assertEquals(1L, state.transactions.first().id)
        assertEquals(1500.0, state.totalSpent)
        assertFalse(state.isLoading)
    }

    @Test
    fun testViewModelPeriodParsingFallback() = runBlocking {
        val txRepo = FakeTxRepo()
        val budgetEngine = BudgetEngine(txRepo, TimeZone.UTC)

        val vmWeekly = BudgetDrilldownViewModel(
            category = "Transport",
            periodName = "WEEKLY",
            transactionRepository = txRepo,
            budgetEngine = budgetEngine,
            coroutineScope = this
        )
        assertEquals(BudgetPeriod.WEEKLY, vmWeekly.period)

        val vmInvalid = BudgetDrilldownViewModel(
            category = "Transport",
            periodName = "UNKNOWN_PERIOD",
            transactionRepository = txRepo,
            budgetEngine = budgetEngine,
            coroutineScope = this
        )
        assertEquals(BudgetPeriod.MONTHLY, vmInvalid.period)
    }
}
