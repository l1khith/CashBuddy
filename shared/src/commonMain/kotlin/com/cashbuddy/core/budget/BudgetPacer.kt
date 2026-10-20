// NO-NETWORK
package com.cashbuddy.core.budget

import com.cashbuddy.domain.model.BudgetPeriod
import kotlinx.datetime.Instant
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.toLocalDateTime

data class PacingResult(
    val daysElapsed: Int,
    val totalDays: Int,
    val expectedPercent: Double,
    val actualPercent: Double,
    val projectedSpend: Double,
    val projectedOverage: Double
) {
    val daysRemaining: Int get() = maxOf(0, totalDays - daysElapsed)
}

object BudgetPacer {

    fun calculatePacing(
        spent: Double,
        budgetAmount: Double,
        period: BudgetPeriod,
        now: Instant,
        timeZone: TimeZone = TimeZone.currentSystemDefault()
    ): PacingResult {
        val localDate = now.toLocalDateTime(timeZone).date

        val (daysElapsed, totalDays) = when (period) {
            BudgetPeriod.MONTHLY -> {
                val year = localDate.year
                val isLeapYear = (year % 4 == 0 && (year % 100 != 0 || year % 400 == 0))
                val monthLength = when (localDate.month) {
                    Month.FEBRUARY -> if (isLeapYear) 29 else 28
                    Month.APRIL, Month.JUNE, Month.SEPTEMBER, Month.NOVEMBER -> 30
                    else -> 31
                }
                Pair(localDate.dayOfMonth, monthLength)
            }
            BudgetPeriod.WEEKLY -> {
                // ISO day of week: Monday = 1, Sunday = 7
                Pair(localDate.dayOfWeek.isoDayNumber, 7)
            }
            BudgetPeriod.YEARLY -> {
                val year = localDate.year
                val isLeapYear = (year % 4 == 0 && (year % 100 != 0 || year % 400 == 0))
                val yearLength = if (isLeapYear) 366 else 365
                Pair(localDate.dayOfYear, yearLength)
            }
        }

        val expectedPercent = if (totalDays > 0) (daysElapsed.toDouble() / totalDays.toDouble()) * 100.0 else 0.0
        val actualPercent = if (budgetAmount > 0.0) (spent / budgetAmount) * 100.0 else if (spent > 0.0) 100.0 else 0.0
        val dailyAverage = if (daysElapsed > 0) spent / daysElapsed.toDouble() else 0.0
        val projectedSpend = dailyAverage * totalDays.toDouble()
        val projectedOverage = maxOf(0.0, projectedSpend - budgetAmount)

        return PacingResult(
            daysElapsed = daysElapsed,
            totalDays = totalDays,
            expectedPercent = expectedPercent,
            actualPercent = actualPercent,
            projectedSpend = projectedSpend,
            projectedOverage = projectedOverage
        )
    }

    fun calculatePacing(
        spent: Double,
        budget: Double,
        period: String,
        now: Instant,
        timeZone: TimeZone = TimeZone.currentSystemDefault()
    ): PacingResult {
        val budgetPeriod = when (period.uppercase()) {
            "WEEKLY" -> BudgetPeriod.WEEKLY
            "YEARLY" -> BudgetPeriod.YEARLY
            else -> BudgetPeriod.MONTHLY
        }
        return calculatePacing(
            spent = spent,
            budgetAmount = budget,
            period = budgetPeriod,
            now = now,
            timeZone = timeZone
        )
    }
}
