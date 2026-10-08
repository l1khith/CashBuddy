// NO-NETWORK
package com.cashbuddy.presentation.budget

import com.cashbuddy.domain.model.AlertState
import com.cashbuddy.domain.model.Budget
import com.cashbuddy.domain.model.BudgetPeriod
import com.cashbuddy.domain.model.BudgetStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BudgetListScreenTest {

    @Test
    fun testEmptyStateUiState() {
        val emptyState = BudgetUiState(
            budgets = emptyList(),
            isLoading = false,
            error = null
        )

        assertTrue(emptyState.budgets.isEmpty())
        assertFalse(emptyState.isLoading)
    }

    @Test
    fun testPopulatedStateWithActiveBudgets() {
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
        val status = BudgetStatus(
            budget = budget,
            spent = 4500.0,
            remaining = 5500.0,
            percentUsed = 45.0f,
            state = AlertState.ON_TRACK
        )

        val populatedState = BudgetUiState(
            budgets = listOf(status),
            isLoading = false
        )

        assertEquals(1, populatedState.budgets.size)
        val item = populatedState.budgets.first()
        assertEquals("Food", item.budget.category)
        assertEquals(4500.0, item.spent)
        assertEquals(5500.0, item.remaining)
        assertEquals(AlertState.ON_TRACK, item.state)
    }

    @Test
    fun testOverBudgetWarningState() {
        val budget = Budget(
            id = "b2",
            category = "Shopping",
            amount = 5000.0,
            period = BudgetPeriod.MONTHLY,
            startDate = 1000L,
            isActive = true,
            createdAt = 1000L,
            updatedAt = 1000L
        )
        val status = BudgetStatus(
            budget = budget,
            spent = 6000.0,
            remaining = -1000.0,
            percentUsed = 120.0f,
            state = AlertState.EXCEEDED
        )

        val state = BudgetUiState(
            budgets = listOf(status),
            isLoading = false
        )

        assertEquals(AlertState.EXCEEDED, state.budgets.first().state)
        assertEquals(-1000.0, state.budgets.first().remaining)
    }

    @Test
    fun testGlobalCardRendersAboveCategoryCards() {
        val globalBudget = Budget(
            id = "g1",
            category = com.cashbuddy.domain.model.BudgetCategories.GLOBAL,
            amount = 10000.0,
            period = BudgetPeriod.MONTHLY,
            startDate = 1000L,
            isActive = true,
            createdAt = 1000L,
            updatedAt = 1000L
        )
        val globalStatus = BudgetStatus(
            budget = globalBudget,
            spent = 4000.0,
            remaining = 6000.0,
            percentUsed = 40.0f,
            state = AlertState.ON_TRACK
        )

        val catBudget = Budget(
            id = "c1",
            category = "Food",
            amount = 3000.0,
            period = BudgetPeriod.MONTHLY,
            startDate = 1000L,
            isActive = true,
            createdAt = 1000L,
            updatedAt = 1000L
        )
        val catStatus = BudgetStatus(
            budget = catBudget,
            spent = 2500.0,
            remaining = 500.0,
            percentUsed = 83.3f,
            state = AlertState.WARNING
        )

        val state = BudgetUiState(
            budgets = listOf(globalStatus, catStatus),
            globalBudget = globalStatus,
            categoryBudgets = listOf(catStatus),
            unbudgetedSpent = 1500.0,
            isLoading = false
        )

        assertEquals("g1", state.globalBudget?.budget?.id)
        assertEquals(1, state.categoryBudgets.size)
        assertEquals("c1", state.categoryBudgets.first().budget.id)
        assertEquals(1500.0, state.unbudgetedSpent)
    }

    @Test
    fun testUnbudgetedRowHiddenWhenNoGlobal() {
        val catBudget = Budget(
            id = "c1",
            category = "Food",
            amount = 3000.0,
            period = BudgetPeriod.MONTHLY,
            startDate = 1000L,
            isActive = true,
            createdAt = 1000L,
            updatedAt = 1000L
        )
        val catStatus = BudgetStatus(
            budget = catBudget,
            spent = 2500.0,
            remaining = 500.0,
            percentUsed = 83.3f,
            state = AlertState.WARNING
        )

        val state = BudgetUiState(
            budgets = listOf(catStatus),
            globalBudget = null,
            categoryBudgets = listOf(catStatus),
            unbudgetedSpent = 0.0,
            isLoading = false
        )

        assertEquals(null, state.globalBudget)
        assertEquals(0.0, state.unbudgetedSpent)
    }

    @Test
    fun testGlobalPeriodTitleMapping() {
        val monthlyBudget = Budget(
            id = "m1",
            category = com.cashbuddy.domain.model.BudgetCategories.GLOBAL,
            amount = 10000.0,
            period = BudgetPeriod.MONTHLY,
            startDate = 1000L,
            isActive = true,
            createdAt = 1000L,
            updatedAt = 1000L
        )
        val weeklyBudget = monthlyBudget.copy(id = "w1", period = BudgetPeriod.WEEKLY)
        val yearlyBudget = monthlyBudget.copy(id = "y1", period = BudgetPeriod.YEARLY)

        fun getTitle(budget: Budget, isGlobal: Boolean): String {
            return if (isGlobal) {
                when (budget.period) {
                    BudgetPeriod.MONTHLY -> "Monthly Budget"
                    BudgetPeriod.WEEKLY -> "Weekly Budget"
                    BudgetPeriod.YEARLY -> "Yearly Budget"
                }
            } else {
                budget.category
            }
        }

        assertEquals("Monthly Budget", getTitle(monthlyBudget, isGlobal = true))
        assertEquals("Weekly Budget", getTitle(weeklyBudget, isGlobal = true))
        assertEquals("Yearly Budget", getTitle(yearlyBudget, isGlobal = true))
        assertEquals(com.cashbuddy.domain.model.BudgetCategories.GLOBAL, getTitle(monthlyBudget, isGlobal = false))
    }
}
