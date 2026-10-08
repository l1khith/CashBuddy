// NO-NETWORK
package com.cashbuddy.core

import com.cashbuddy.core.budget.AlertEvent
import com.cashbuddy.core.budget.BudgetAlertScheduler
import com.cashbuddy.core.budget.BudgetEngine
import com.cashbuddy.core.budget.BudgetNotifier
import com.cashbuddy.domain.model.AlertState
import com.cashbuddy.domain.model.Budget
import com.cashbuddy.domain.model.BudgetCategories
import com.cashbuddy.domain.model.BudgetPeriod
import com.cashbuddy.domain.model.DateRangeSummary
import com.cashbuddy.domain.model.Transaction
import com.cashbuddy.domain.model.TransactionStatus
import com.cashbuddy.domain.model.TransactionType
import com.cashbuddy.domain.repository.BudgetRepository
import com.cashbuddy.domain.repository.CategoryBreakdown
import com.cashbuddy.domain.repository.MergeLogEntry
import com.cashbuddy.domain.repository.MonthlySummary
import com.cashbuddy.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BudgetAlertSchedulerTest {

    private class FakeBudgetRepo : BudgetRepository {
        val budgets = mutableListOf<Budget>()

        override suspend fun create(budget: Budget) {
            budgets.add(budget)
        }

        override suspend fun update(budget: Budget) {
            val idx = budgets.indexOfFirst { it.id == budget.id }
            if (idx != -1) budgets[idx] = budget
        }

        override suspend fun delete(id: String) {
            budgets.removeAll { it.id == id }
        }

        override suspend fun getActive(): List<Budget> = budgets.filter { it.isActive }

        override suspend fun getById(id: String): Budget? = budgets.find { it.id == id }

        override suspend fun findByCategory(category: String, period: String): Budget? =
            budgets.find { it.category == category && it.period.name == period && it.isActive }

        override suspend fun markAlerted(id: String, state: AlertState, at: Long) {
            val idx = budgets.indexOfFirst { it.id == id }
            if (idx != -1) {
                budgets[idx] = budgets[idx].copy(lastAlertState = state, lastAlertAt = at)
            }
        }

        override suspend fun resetAlertState(id: String) {
            val idx = budgets.indexOfFirst { it.id == id }
            if (idx != -1) {
                budgets[idx] = budgets[idx].copy(lastAlertState = AlertState.ON_TRACK, lastAlertAt = null)
            }
        }

        override fun getAll(): Flow<List<Budget>> = flowOf(budgets)
    }

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

        override suspend fun sumAll(startTime: Long, endTime: Long, type: TransactionType): Double =
            txs.filter {
                it.type == type &&
                it.timestamp in startTime..endTime &&
                !it.isMerged
            }.sumOf { it.amount }

        override fun getAll(): Flow<List<Transaction>> = flowOf(txs)
        override fun getById(id: Long): Flow<Transaction?> = flowOf(txs.find { it.id == id })
        override fun getPending(): Flow<List<Transaction>> = flowOf(emptyList())
        override fun getRecent(limit: Long): Flow<List<Transaction>> = flowOf(emptyList())
        override fun getByDateRange(start: Long, end: Long): Flow<List<Transaction>> = flowOf(emptyList())
        override fun getByDateRangeWithLimit(start: Long, end: Long, limit: Long): Flow<List<Transaction>> = flowOf(emptyList())
        override fun getSummaryByDateRange(start: Long, end: Long): Flow<DateRangeSummary> = flowOf(DateRangeSummary(0.0, 0.0, 0L))
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

    private class RecordingNotifier : BudgetNotifier {
        val events = mutableListOf<AlertEvent>()
        override fun sendAlert(event: AlertEvent) {
            events.add(event)
        }
    }

    private fun createTx(
        id: Long,
        amount: Double,
        category: String,
        timestamp: Long
    ): Transaction = Transaction(
        id = id,
        amount = amount,
        type = TransactionType.DEBIT,
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
        isMerged = false,
        mergedIntoId = null,
        categoryName = category,
        categoryColor = "#FF0000",
        categoryIcon = "icon",
        accountName = "HDFC",
        accountType = "BANK"
    )

    @Test
    fun testOnTrackProducesNoAlert() = runBlocking {
        val budgetRepo = FakeBudgetRepo()
        val txRepo = FakeTxRepo()
        val engine = BudgetEngine(txRepo, TimeZone.UTC)
        val notifier = RecordingNotifier()

        val now = 1713182400000L // 2024-04-15
        val budget = Budget(
            id = "b1",
            category = "Food",
            amount = 10000.0,
            period = BudgetPeriod.MONTHLY,
            startDate = now - 100000L,
            isActive = true,
            createdAt = now,
            updatedAt = now
        )
        budgetRepo.create(budget)
        txRepo.txs.add(createTx(1L, 5000.0, "Food", now)) // 50% spent

        val scheduler = BudgetAlertScheduler(
            budgetRepository = budgetRepo,
            budgetEngine = engine,
            notifier = notifier,
            clock = { now }
        )

        val alerts = scheduler.checkAndAlert(now)
        assertTrue(alerts.isEmpty())
        assertTrue(notifier.events.isEmpty())
        assertNull(budgetRepo.getById("b1")?.lastAlertState)
    }

    @Test
    fun testWarningAlertFiresAt80PercentAndMarksAlerted() = runBlocking {
        val budgetRepo = FakeBudgetRepo()
        val txRepo = FakeTxRepo()
        val engine = BudgetEngine(txRepo, TimeZone.UTC)
        val notifier = RecordingNotifier()

        val now = 1713182400000L // 2024-04-15
        val budget = Budget(
            id = "b1",
            category = "Food",
            amount = 10000.0,
            period = BudgetPeriod.MONTHLY,
            startDate = now - 100000L,
            isActive = true,
            createdAt = now,
            updatedAt = now
        )
        budgetRepo.create(budget)
        txRepo.txs.add(createTx(1L, 8500.0, "Food", now)) // 85% spent

        val scheduler = BudgetAlertScheduler(
            budgetRepository = budgetRepo,
            budgetEngine = engine,
            notifier = notifier,
            clock = { now }
        )

        val alerts = scheduler.checkAndAlert(now)
        assertEquals(1, alerts.size)
        assertEquals("Food budget is at 80%", alerts.first().message)
        assertEquals(AlertState.WARNING, alerts.first().state)
        assertEquals(1, notifier.events.size)
        assertEquals(AlertState.WARNING, budgetRepo.getById("b1")?.lastAlertState)
        assertEquals(now, budgetRepo.getById("b1")?.lastAlertAt)
    }

    @Test
    fun testAtMostOncePerTransitionDoesNotDuplicateAlert() = runBlocking {
        val budgetRepo = FakeBudgetRepo()
        val txRepo = FakeTxRepo()
        val engine = BudgetEngine(txRepo, TimeZone.UTC)
        val notifier = RecordingNotifier()

        val now = 1713182400000L
        val budget = Budget(
            id = "b1",
            category = "Food",
            amount = 10000.0,
            period = BudgetPeriod.MONTHLY,
            startDate = now - 100000L,
            isActive = true,
            createdAt = now,
            updatedAt = now
        )
        budgetRepo.create(budget)
        txRepo.txs.add(createTx(1L, 8500.0, "Food", now))

        val scheduler = BudgetAlertScheduler(budgetRepo, engine, notifier, { now })

        // First pass: fires WARNING
        val alerts1 = scheduler.checkAndAlert(now)
        assertEquals(1, alerts1.size)

        // Spending increases to 90%, still in WARNING
        txRepo.txs.add(createTx(2L, 500.0, "Food", now + 1000L))
        val alerts2 = scheduler.checkAndAlert(now + 1000L)
        assertTrue(alerts2.isEmpty()) // No duplicate alert!
        assertEquals(1, notifier.events.size)
    }

    @Test
    fun testTransitionFromWarningToExceeded() = runBlocking {
        val budgetRepo = FakeBudgetRepo()
        val txRepo = FakeTxRepo()
        val engine = BudgetEngine(txRepo, TimeZone.UTC)
        val notifier = RecordingNotifier()

        val now = 1713182400000L
        val budget = Budget(
            id = "b1",
            category = "Food",
            amount = 10000.0,
            period = BudgetPeriod.MONTHLY,
            startDate = now - 100000L,
            isActive = true,
            lastAlertState = AlertState.WARNING,
            lastAlertAt = now - 5000L,
            createdAt = now,
            updatedAt = now
        )
        budgetRepo.create(budget)
        // Spending is now 10500 (105% - EXCEEDED)
        txRepo.txs.add(createTx(1L, 10500.0, "Food", now))

        val scheduler = BudgetAlertScheduler(budgetRepo, engine, notifier, { now })

        val alerts = scheduler.checkAndAlert(now)
        assertEquals(1, alerts.size)
        assertEquals("Food budget exceeded", alerts.first().message)
        assertEquals(AlertState.EXCEEDED, alerts.first().state)
        assertEquals(AlertState.EXCEEDED, budgetRepo.getById("b1")?.lastAlertState)

        // Further spending beyond 105% does not re-alert EXCEEDED
        txRepo.txs.add(createTx(2L, 500.0, "Food", now + 1000L))
        val alerts2 = scheduler.checkAndAlert(now + 1000L)
        assertTrue(alerts2.isEmpty())
    }

    @Test
    fun testDirectJumpFromOnTrackToExceeded() = runBlocking {
        val budgetRepo = FakeBudgetRepo()
        val txRepo = FakeTxRepo()
        val engine = BudgetEngine(txRepo, TimeZone.UTC)
        val notifier = RecordingNotifier()

        val now = 1713182400000L
        val budget = Budget(
            id = "b1",
            category = "Shopping",
            amount = 5000.0,
            period = BudgetPeriod.MONTHLY,
            startDate = now - 100000L,
            isActive = true,
            createdAt = now,
            updatedAt = now
        )
        budgetRepo.create(budget)
        txRepo.txs.add(createTx(1L, 6000.0, "Shopping", now)) // Jumps straight to 120%

        val scheduler = BudgetAlertScheduler(budgetRepo, engine, notifier, { now })

        val alerts = scheduler.checkAndAlert(now)
        assertEquals(1, alerts.size)
        assertEquals("Shopping budget exceeded", alerts.first().message)
        assertEquals(AlertState.EXCEEDED, alerts.first().state)
    }

    @Test
    fun testPeriodRolloverResetsAlertState() = runBlocking {
        val budgetRepo = FakeBudgetRepo()
        val txRepo = FakeTxRepo()
        val engine = BudgetEngine(txRepo, TimeZone.UTC)
        val notifier = RecordingNotifier()

        // Month 1: April 2024 (e.g. Apr 15 = 1713182400000L)
        val aprilNow = 1713182400000L
        val budget = Budget(
            id = "b1",
            category = "Food",
            amount = 10000.0,
            period = BudgetPeriod.MONTHLY,
            startDate = aprilNow - 100000L,
            isActive = true,
            lastAlertState = AlertState.EXCEEDED,
            lastAlertAt = aprilNow, // alerted in April
            createdAt = aprilNow,
            updatedAt = aprilNow
        )
        budgetRepo.create(budget)

        // Month 2: May 2024 (e.g. May 10 = 1715342400000L)
        val mayNow = 1715342400000L

        // In May, user spends 8500 (85%)
        txRepo.txs.add(createTx(10L, 8500.0, "Food", mayNow))

        val scheduler = BudgetAlertScheduler(budgetRepo, engine, notifier, { mayNow })

        // Check alerts in May: April alert should roll over and May 80% alert should fire!
        val alerts = scheduler.checkAndAlert(mayNow)
        assertEquals(1, alerts.size)
        assertEquals("Food budget is at 80%", alerts.first().message)
        assertEquals(AlertState.WARNING, alerts.first().state)
        assertEquals(AlertState.WARNING, budgetRepo.getById("b1")?.lastAlertState)
        assertEquals(mayNow, budgetRepo.getById("b1")?.lastAlertAt)
    }

    @Test
    fun testCategoryAt90AndGlobalAt85FiresOnlyCategoryAlert() = runBlocking {
        val budgetRepo = FakeBudgetRepo()
        val txRepo = FakeTxRepo()
        val engine = BudgetEngine(txRepo, TimeZone.UTC)
        val notifier = RecordingNotifier()

        val now = 1713182400000L // 2024-04-15
        val categoryBudget = Budget(
            id = "b_cat",
            category = "Food",
            amount = 10000.0,
            period = BudgetPeriod.MONTHLY,
            startDate = now - 100000L,
            isActive = true,
            createdAt = now,
            updatedAt = now
        )
        val globalBudget = Budget(
            id = "b_global",
            category = BudgetCategories.GLOBAL,
            amount = 50000.0,
            period = BudgetPeriod.MONTHLY,
            startDate = now - 100000L,
            isActive = true,
            createdAt = now,
            updatedAt = now
        )
        // Add global first to verify sorting checks category first
        budgetRepo.create(globalBudget)
        budgetRepo.create(categoryBudget)

        // Category spend = 9000 (90%), other spend = 33500. Total = 42500 (85% of 50000)
        txRepo.txs.add(createTx(1L, 9000.0, "Food", now))
        txRepo.txs.add(createTx(2L, 33500.0, "Travel", now))

        val scheduler = BudgetAlertScheduler(budgetRepo, engine, notifier, { now })

        val alerts = scheduler.checkAndAlert(now)
        // Exactly one notification fires: category alert
        assertEquals(1, alerts.size)
        assertEquals("Food budget is at 80%", alerts.first().message)
        assertEquals("Food", alerts.first().category)
        assertEquals(AlertState.WARNING, alerts.first().state)

        assertEquals(1, notifier.events.size)
        assertEquals("Food budget is at 80%", notifier.events.first().message)

        // Category marked alerted
        assertEquals(AlertState.WARNING, budgetRepo.getById("b_cat")?.lastAlertState)
        // Global state updated silently
        assertEquals(AlertState.WARNING, budgetRepo.getById("b_global")?.lastAlertState)
    }

    @Test
    fun testGlobalAt85WithNoCategoryAlertsFiresGlobalAlert() = runBlocking {
        val budgetRepo = FakeBudgetRepo()
        val txRepo = FakeTxRepo()
        val engine = BudgetEngine(txRepo, TimeZone.UTC)
        val notifier = RecordingNotifier()

        val now = 1713182400000L // 2024-04-15
        val categoryBudget = Budget(
            id = "b_cat",
            category = "Food",
            amount = 10000.0,
            period = BudgetPeriod.MONTHLY,
            startDate = now - 100000L,
            isActive = true,
            createdAt = now,
            updatedAt = now
        )
        val globalBudget = Budget(
            id = "b_global",
            category = BudgetCategories.GLOBAL,
            amount = 50000.0,
            period = BudgetPeriod.MONTHLY,
            startDate = now - 100000L,
            isActive = true,
            createdAt = now,
            updatedAt = now
        )
        budgetRepo.create(categoryBudget)
        budgetRepo.create(globalBudget)

        // Food spent = 5000 (50% - ON_TRACK), other spend = 37500. Total = 42500 (85% of 50000)
        txRepo.txs.add(createTx(1L, 5000.0, "Food", now))
        txRepo.txs.add(createTx(2L, 37500.0, "Shopping", now))

        val scheduler = BudgetAlertScheduler(budgetRepo, engine, notifier, { now })

        val alerts = scheduler.checkAndAlert(now)
        assertEquals(1, alerts.size)
        assertEquals("You've used 80% of your monthly budget", alerts.first().message)
        assertEquals(BudgetCategories.GLOBAL, alerts.first().category)
        assertEquals(AlertState.WARNING, alerts.first().state)

        assertEquals(1, notifier.events.size)
        assertEquals(AlertState.WARNING, budgetRepo.getById("b_global")?.lastAlertState)
        assertNull(budgetRepo.getById("b_cat")?.lastAlertState)
    }
}
