// NO-NETWORK
package com.cashbuddy.presentation.navigation

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class BudgetNavigationTest {

    @Test
    fun testBudgetDrilldownRouteSerialization() {
        val route = ScreenRoute.BudgetDrilldown(category = "Food & Dining", period = "MONTHLY")
        val json = Json.encodeToString(route)
        val decoded = Json.decodeFromString<ScreenRoute.BudgetDrilldown>(json)

        assertEquals("Food & Dining", decoded.category)
        assertEquals("MONTHLY", decoded.period)
    }

    @Test
    fun testBudgetsRouteIdentity() {
        val route: ScreenRoute = ScreenRoute.Budgets
        assertIs<ScreenRoute.Budgets>(route)
    }

    @Test
    fun testBudgetNavigationBackStackBehavior() {
        val backStack = ArrayDeque<ScreenRoute>()

        // 1. App starts at Home
        backStack.addLast(ScreenRoute.Home)
        assertEquals(1, backStack.size)
        assertEquals(ScreenRoute.Home, backStack.last())

        // 2. User taps Budgets from Home entry card
        backStack.addLast(ScreenRoute.Budgets)
        assertEquals(2, backStack.size)
        assertEquals(ScreenRoute.Budgets, backStack.last())

        // 3. User taps a budget card to open Drilldown
        val drilldownRoute = ScreenRoute.BudgetDrilldown(category = "Transport", period = "WEEKLY")
        backStack.addLast(drilldownRoute)
        assertEquals(3, backStack.size)
        assertEquals(drilldownRoute, backStack.last())

        // 4. User presses back from Drilldown
        val poppedFromDrilldown = backStack.removeLast()
        assertEquals(drilldownRoute, poppedFromDrilldown)
        assertEquals(ScreenRoute.Budgets, backStack.last())

        // 5. User presses back from Budgets list
        val poppedFromBudgets = backStack.removeLast()
        assertEquals(ScreenRoute.Budgets, poppedFromBudgets)
        assertEquals(ScreenRoute.Home, backStack.last())
        assertEquals(1, backStack.size)
    }
}
