// NO-NETWORK
package com.cashbuddy.presentation.budget

import com.cashbuddy.core.budget.BudgetEngine
import com.cashbuddy.domain.model.AlertState
import com.cashbuddy.domain.model.Budget
import com.cashbuddy.domain.model.BudgetPeriod
import com.cashbuddy.domain.model.Category
import com.cashbuddy.domain.model.CategoryType
import com.cashbuddy.domain.model.Transaction
import com.cashbuddy.domain.model.TransactionStatus
import com.cashbuddy.domain.model.TransactionType
import com.cashbuddy.domain.repository.BudgetRepository
import com.cashbuddy.domain.repository.CategoryBreakdown
import com.cashbuddy.domain.repository.CategoryRepository
import com.cashbuddy.domain.repository.MergeLogEntry
import com.cashbuddy.domain.repository.MonthlySummary
import com.cashbuddy.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class BudgetViewModelTest {

    private class FakeBudgetRepo : BudgetRepository {
        val budgets = mutableListOf<Budget>()

        override suspend fun create(budget: Budget) {
            budgets.add(budget)
        }

        override suspend fun update(budget: Budget) {
            val idx = budgets.indexOfFirst { it.id == budget.id }
            if (idx != -1) {
                budgets[idx] = budget
            }
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

    private class FakeCategoryRepo : CategoryRepository {
        val categories = listOf(
            Category(id = 1L, name = "Food", type = CategoryType.EXPENSE, icon = "food", color = "#FF0000", isDefault = true),
            Category(id = 2L, name = "Transport", type = CategoryType.EXPENSE, icon = "car", color = "#00FF00", isDefault = true)
        )

        override fun getAll(): Flow<List<Category>> = flowOf(categories)
        override fun getByType(type: CategoryType): Flow<List<Category>> = flowOf(categories.filter { it.type == type })
        override fun getById(id: Long): Flow<Category?> = flowOf(categories.find { it.id == id })
        override suspend fun seedDefaults(currentTimestamp: Long) {}
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

        override fun getAll(): Flow<List<Transaction>> = flowOf(txs)
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

    @Test
    fun testInitialLoadLoadsActiveBudgets() = runBlocking {
        val budgetRepo = FakeBudgetRepo()
        val catRepo = FakeCategoryRepo()
        val txRepo = FakeTxRepo()
        val engine = BudgetEngine(txRepo, TimeZone.UTC)

        val budget = Budget(
            id = "b1",
            category = "Food",
            amount = 5000.0,
            period = BudgetPeriod.MONTHLY,
            startDate = 1000L,
            isActive = true,
            createdAt = 1000L,
            updatedAt = 1000L
        )
        budgetRepo.create(budget)

        val viewModel = BudgetViewModel(budgetRepo, catRepo, engine, coroutineScope = this)
        viewModel.loadBudgets().join()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals(1, state.budgets.size)
        assertEquals("b1", state.budgets.first().budget.id)
        assertEquals(5000.0, state.budgets.first().budget.amount)
        assertEquals(2, state.categories.size)
    }

    @Test
    fun testCreateBudgetAction() = runBlocking {
        val budgetRepo = FakeBudgetRepo()
        val catRepo = FakeCategoryRepo()
        val txRepo = FakeTxRepo()
        val engine = BudgetEngine(txRepo, TimeZone.UTC)

        val viewModel = BudgetViewModel(budgetRepo, catRepo, engine, coroutineScope = this)
        viewModel.createBudget("Food", 7000.0, BudgetPeriod.MONTHLY).join()

        assertEquals(1, budgetRepo.budgets.size)
        val created = budgetRepo.budgets.first()
        assertEquals("Food", created.category)
        assertEquals(7000.0, created.amount)
        assertEquals(BudgetPeriod.MONTHLY, created.period)
    }

    @Test
    fun testUpdateBudgetAction() = runBlocking {
        val budgetRepo = FakeBudgetRepo()
        val catRepo = FakeCategoryRepo()
        val txRepo = FakeTxRepo()
        val engine = BudgetEngine(txRepo, TimeZone.UTC)

        val budget = Budget(
            id = "b1",
            category = "Transport",
            amount = 3000.0,
            period = BudgetPeriod.MONTHLY,
            startDate = 1000L,
            isActive = true,
            createdAt = 1000L,
            updatedAt = 1000L
        )
        budgetRepo.create(budget)

        val viewModel = BudgetViewModel(budgetRepo, catRepo, engine, coroutineScope = this)
        viewModel.updateBudget(budget.copy(amount = 4500.0)).join()

        val updated = budgetRepo.getById("b1")
        assertNotNull(updated)
        assertEquals(4500.0, updated.amount)
    }

    @Test
    fun testDeleteBudgetAction() = runBlocking {
        val budgetRepo = FakeBudgetRepo()
        val catRepo = FakeCategoryRepo()
        val txRepo = FakeTxRepo()
        val engine = BudgetEngine(txRepo, TimeZone.UTC)

        val budget = Budget(
            id = "b1",
            category = "Dining",
            amount = 2000.0,
            period = BudgetPeriod.MONTHLY,
            startDate = 1000L,
            isActive = true,
            createdAt = 1000L,
            updatedAt = 1000L
        )
        budgetRepo.create(budget)

        val viewModel = BudgetViewModel(budgetRepo, catRepo, engine, coroutineScope = this)
        viewModel.deleteBudget("b1").join()

        assertTrue(budgetRepo.budgets.isEmpty())
    }
}
