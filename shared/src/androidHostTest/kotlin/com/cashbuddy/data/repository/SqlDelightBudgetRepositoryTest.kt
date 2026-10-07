// NO-NETWORK
package com.cashbuddy.data.repository

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.cashbuddy.db.AppDatabase
import com.cashbuddy.domain.model.AlertState
import com.cashbuddy.domain.model.Budget
import com.cashbuddy.domain.model.BudgetPeriod
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SqlDelightBudgetRepositoryTest {

    private lateinit var database: AppDatabase
    private lateinit var repository: SqlDelightBudgetRepository

    @BeforeTest
    fun setup() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        AppDatabase.Schema.create(driver)
        database = AppDatabase(driver)
        repository = SqlDelightBudgetRepository(database)
    }

    @Test
    fun testCreateAndGetById() = runBlocking {
        val budget = Budget(
            id = "b1",
            category = "Food",
            amount = 5000.0,
            period = BudgetPeriod.MONTHLY,
            startDate = 1000L,
            isActive = true,
            lastAlertState = null,
            lastAlertAt = null,
            createdAt = 1000L,
            updatedAt = 1000L
        )
        repository.create(budget)

        val fetched = repository.getById("b1")
        assertNotNull(fetched)
        assertEquals("b1", fetched.id)
        assertEquals("Food", fetched.category)
        assertEquals(5000.0, fetched.amount)
        assertEquals(BudgetPeriod.MONTHLY, fetched.period)
        assertEquals(1000L, fetched.startDate)
        assertTrue(fetched.isActive)
        assertNull(fetched.lastAlertState)
    }

    @Test
    fun testGetActiveFiltersInactiveBudgets() = runBlocking {
        val b1 = Budget(
            id = "b1",
            category = "Food",
            amount = 5000.0,
            period = BudgetPeriod.MONTHLY,
            startDate = 1000L,
            isActive = true,
            createdAt = 1000L,
            updatedAt = 1000L
        )
        val b2 = Budget(
            id = "b2",
            category = "Travel",
            amount = 2000.0,
            period = BudgetPeriod.MONTHLY,
            startDate = 1000L,
            isActive = false,
            createdAt = 1000L,
            updatedAt = 1000L
        )
        repository.create(b1)
        repository.create(b2)

        val active = repository.getActive()
        assertEquals(1, active.size)
        assertEquals("b1", active.first().id)
    }

    @Test
    fun testFindByCategory() = runBlocking {
        val budget = Budget(
            id = "b1",
            category = "Shopping",
            amount = 8000.0,
            period = BudgetPeriod.MONTHLY,
            startDate = 1000L,
            isActive = true,
            createdAt = 1000L,
            updatedAt = 1000L
        )
        repository.create(budget)

        val found = repository.findByCategory("Shopping", "MONTHLY")
        assertNotNull(found)
        assertEquals("b1", found.id)

        val notFound = repository.findByCategory("Shopping", "WEEKLY")
        assertNull(notFound)
    }

    @Test
    fun testUpdate() = runBlocking {
        val budget = Budget(
            id = "b1",
            category = "Groceries",
            amount = 3000.0,
            period = BudgetPeriod.MONTHLY,
            startDate = 1000L,
            isActive = true,
            createdAt = 1000L,
            updatedAt = 1000L
        )
        repository.create(budget)

        val updated = budget.copy(amount = 4500.0, updatedAt = 2000L)
        repository.update(updated)

        val fetched = repository.getById("b1")
        assertNotNull(fetched)
        assertEquals(4500.0, fetched.amount)
        assertEquals(2000L, fetched.updatedAt)
    }

    @Test
    fun testMarkAlertedAndResetAlertState() = runBlocking {
        val budget = Budget(
            id = "b1",
            category = "Dining",
            amount = 5000.0,
            period = BudgetPeriod.MONTHLY,
            startDate = 1000L,
            isActive = true,
            createdAt = 1000L,
            updatedAt = 1000L
        )
        repository.create(budget)

        repository.markAlerted("b1", AlertState.WARNING, 1500L)
        val alerted = repository.getById("b1")
        assertNotNull(alerted)
        assertEquals(AlertState.WARNING, alerted.lastAlertState)
        assertEquals(1500L, alerted.lastAlertAt)

        repository.resetAlertState("b1")
        val reset = repository.getById("b1")
        assertNotNull(reset)
        assertEquals(AlertState.ON_TRACK, reset.lastAlertState)
        assertNull(reset.lastAlertAt)
    }

    @Test
    fun testDelete() = runBlocking {
        val budget = Budget(
            id = "b1",
            category = "Bills",
            amount = 2000.0,
            period = BudgetPeriod.MONTHLY,
            startDate = 1000L,
            isActive = true,
            createdAt = 1000L,
            updatedAt = 1000L
        )
        repository.create(budget)
        assertNotNull(repository.getById("b1"))

        repository.delete("b1")
        assertNull(repository.getById("b1"))
    }

    @Test
    fun testGetAllFlow() = runBlocking {
        val b1 = Budget(
            id = "b1",
            category = "Rent",
            amount = 15000.0,
            period = BudgetPeriod.MONTHLY,
            startDate = 1000L,
            isActive = true,
            createdAt = 1000L,
            updatedAt = 1000L
        )
        repository.create(b1)

        val all = repository.getAll().first()
        assertEquals(1, all.size)
        assertEquals("b1", all.first().id)
    }
}
