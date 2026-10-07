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
}
