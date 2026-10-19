// NO-NETWORK
package com.cashbuddy.core

import com.cashbuddy.core.budget.BudgetPacer
import com.cashbuddy.domain.model.BudgetPeriod
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BudgetPacerTest {

    private val utc = TimeZone.UTC

    @Test
    fun testFebruaryLeapYear() {
        // Date: Feb 28, 2024 (2024 is a leap year -> 29 days in Feb)
        val feb28_2024 = Instant.parse("2024-02-28T12:00:00Z")
        val pacing = BudgetPacer.calculatePacing(
            spent = 1000.0,
            budgetAmount = 2000.0,
            period = BudgetPeriod.MONTHLY,
            now = feb28_2024,
            timeZone = utc
        )

        assertEquals(29, pacing.totalDays)
        assertEquals(28, pacing.daysElapsed)
        // expectedPercent: (28 / 29) * 100 = 96.5517...
        assertTrue(pacing.expectedPercent > 96.5 && pacing.expectedPercent < 96.6)
    }

    @Test
    fun testAprilThirtyDays() {
        // Date: April 15, 2026 (April has 30 days)
        val apr15_2026 = Instant.parse("2026-04-15T12:00:00Z")
        val pacing = BudgetPacer.calculatePacing(
            spent = 2500.0,
            budgetAmount = 5000.0,
            period = BudgetPeriod.MONTHLY,
            now = apr15_2026,
            timeZone = utc
        )

        assertEquals(30, pacing.totalDays)
        assertEquals(15, pacing.daysElapsed)
        assertEquals(50.0, pacing.expectedPercent)
    }

    @Test
    fun testOctoberThirtyOneDays() {
        // Date: Oct 15, 2026 (October has 31 days)
        val oct15_2026 = Instant.parse("2026-10-15T12:00:00Z")
        val pacing = BudgetPacer.calculatePacing(
            spent = 4000.0,
            budgetAmount = 10000.0,
            period = BudgetPeriod.MONTHLY,
            now = oct15_2026,
            timeZone = utc
        )

        assertEquals(31, pacing.totalDays)
        assertEquals(15, pacing.daysElapsed)
        // expectedPercent: (15 / 31) * 100 = 48.387...
        assertTrue(pacing.expectedPercent > 48.3 && pacing.expectedPercent < 48.4)
    }

    @Test
    fun testProjectionAndOverage() {
        // Budget: ₹10,000. Date: Day 10 of a 30-day month (e.g., April 10, 2026). Spent: ₹4,000.
        val apr10_2026 = Instant.parse("2026-04-10T12:00:00Z")
        val pacing = BudgetPacer.calculatePacing(
            spent = 4000.0,
            budgetAmount = 10000.0,
            period = BudgetPeriod.MONTHLY,
            now = apr10_2026,
            timeZone = utc
        )

        assertEquals(30, pacing.totalDays)
        assertEquals(10, pacing.daysElapsed)
        // dailyAverage = 4000 / 10 = 400
        // projectedSpend = 400 * 30 = 12,000
        assertEquals(12000.0, pacing.projectedSpend)
        // projectedOverage = 12000 - 10000 = 2,000
        assertEquals(2000.0, pacing.projectedOverage)
    }

    @Test
    fun testWeeklyPacing() {
        // Wednesday: April 15, 2026 is a Wednesday (ISO day number = 3)
        val apr15_2026 = Instant.parse("2026-04-15T12:00:00Z")
        val pacing = BudgetPacer.calculatePacing(
            spent = 1500.0,
            budgetAmount = 3500.0,
            period = BudgetPeriod.WEEKLY,
            now = apr15_2026,
            timeZone = utc
        )

        assertEquals(7, pacing.totalDays)
        assertEquals(3, pacing.daysElapsed)
        // expectedPercent = (3 / 7) * 100 = 42.857...
        assertTrue(pacing.expectedPercent > 42.8 && pacing.expectedPercent < 42.9)
    }
}
