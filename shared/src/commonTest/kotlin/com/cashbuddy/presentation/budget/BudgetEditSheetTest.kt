// NO-NETWORK
package com.cashbuddy.presentation.budget

import com.cashbuddy.domain.model.AlertState
import com.cashbuddy.domain.model.Budget
import com.cashbuddy.domain.model.BudgetPeriod
import com.cashbuddy.domain.model.BudgetStatus
import com.cashbuddy.domain.model.Category
import com.cashbuddy.domain.model.CategoryType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BudgetEditSheetTest {

    private val categories = listOf(
        Category(id = 1L, name = "Food", type = CategoryType.EXPENSE, icon = "food", color = "#FF0000", isDefault = true),
        Category(id = 2L, name = "Bills", type = CategoryType.EXPENSE, icon = "bill", color = "#00FF00", isDefault = true)
    )

    private fun validateBudgetInput(
        category: String,
        amountText: String,
        period: BudgetPeriod,
        isEditing: Boolean,
        existingBudgets: List<BudgetStatus>
    ): String? {
        val amount = amountText.toDoubleOrNull()
        return when {
            category.isBlank() -> "Please select a category"
            amount == null || amount <= 0.0 -> "Please enter an amount greater than ₹0"
            !isEditing && existingBudgets.any {
                it.budget.category.equals(category, ignoreCase = true) && it.budget.period == period
            } -> "A ${period.name.lowercase()} budget for $category already exists"
            else -> null
        }
    }

    @Test
    fun testValidCreateInput() {
        val error = validateBudgetInput(
            category = "Food",
            amountText = "5000",
            period = BudgetPeriod.MONTHLY,
            isEditing = false,
            existingBudgets = emptyList()
        )
        assertNull(error)
    }

    @Test
    fun testBlankCategoryRejected() {
        val error = validateBudgetInput(
            category = "",
            amountText = "5000",
            period = BudgetPeriod.MONTHLY,
            isEditing = false,
            existingBudgets = emptyList()
        )
        assertEquals("Please select a category", error)
    }

    @Test
    fun testInvalidOrZeroAmountRejected() {
        val errZero = validateBudgetInput(
            category = "Food",
            amountText = "0",
            period = BudgetPeriod.MONTHLY,
            isEditing = false,
            existingBudgets = emptyList()
        )
        assertEquals("Please enter an amount greater than ₹0", errZero)

        val errNegative = validateBudgetInput(
            category = "Food",
            amountText = "-500",
            period = BudgetPeriod.MONTHLY,
            isEditing = false,
            existingBudgets = emptyList()
        )
        assertEquals("Please enter an amount greater than ₹0", errNegative)

        val errText = validateBudgetInput(
            category = "Food",
            amountText = "abc",
            period = BudgetPeriod.MONTHLY,
            isEditing = false,
            existingBudgets = emptyList()
        )
        assertEquals("Please enter an amount greater than ₹0", errText)
    }

    @Test
    fun testDuplicateBudgetRejectedOnCreate() {
        val existing = listOf(
            BudgetStatus(
                budget = Budget(
                    id = "b1",
                    category = "Food",
                    amount = 5000.0,
                    period = BudgetPeriod.MONTHLY,
                    startDate = 1000L,
                    isActive = true,
                    createdAt = 1000L,
                    updatedAt = 1000L
                ),
                spent = 1000.0,
                remaining = 4000.0,
                percentUsed = 20f,
                state = AlertState.ON_TRACK
            )
        )

        val errorSamePeriod = validateBudgetInput(
            category = "Food",
            amountText = "8000",
            period = BudgetPeriod.MONTHLY,
            isEditing = false,
            existingBudgets = existing
        )
        assertEquals("A monthly budget for Food already exists", errorSamePeriod)

        val errorDifferentPeriod = validateBudgetInput(
            category = "Food",
            amountText = "2000",
            period = BudgetPeriod.WEEKLY,
            isEditing = false,
            existingBudgets = existing
        )
        assertNull(errorDifferentPeriod)
    }
}
