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
    val isWarning: Boolean get() = projectedOverage > 0.0
    val warningMessage: String?
        get() = if (isWarning) "At this rate, you will exceed your budget by ₹${projectedOverage.toInt()}." else null

    fun warningMessageForCategory(category: String): String? =
        if (isWarning) "At this rate, you will exceed your $category budget by ₹${projectedOverage.toInt()}." else null
}

data class BudgetWarning(
    val dailyAverage: Double,
    val projectedTotal: Double,
    val budgetLimit: Double,
    val overage: Double,
    val isExceeded: Boolean,
    val warningMessage: String?
)

object BudgetPacer {

    /**
     * Calculates the daily average spend so far.
     */
    fun calculateDailyAverage(spent: Double, daysElapsed: Int): Double {
        return if (daysElapsed > 0) spent / daysElapsed.toDouble() else 0.0
    }

    /**
     * Projects month-end total spend using linear regression / pacing: dailyAverage * totalDaysInMonth.
     */
    fun projectMonthEndTotal(dailyAverage: Double, totalDaysInMonth: Int = 30): Double {
        return dailyAverage * totalDaysInMonth.toDouble()
    }

    /**
     * Evaluates whether projected month-end spend exceeds the budget limit.
     */
    fun checkBudgetWarning(
        spentSoFar: Double,
        budgetLimit: Double,
        daysElapsed: Int,
        totalDaysInMonth: Int = 30,
        category: String = "budget"
    ): BudgetWarning {
        val dailyAvg = calculateDailyAverage(spentSoFar, daysElapsed)
        val projectedTotal = projectMonthEndTotal(dailyAvg, totalDaysInMonth)
        val isExceeded = projectedTotal > budgetLimit
        val overage = maxOf(0.0, projectedTotal - budgetLimit)
        val warningMessage = if (isExceeded) {
            "At this rate, you will exceed your $category budget by ₹${overage.toInt()}."
        } else {
            null
        }
        return BudgetWarning(
            dailyAverage = dailyAvg,
            projectedTotal = projectedTotal,
            budgetLimit = budgetLimit,
            overage = overage,
            isExceeded = isExceeded,
            warningMessage = warningMessage
        )
    }

    fun calculatePacing(
        spent: Double,
        budgetAmount: Double,
        period: BudgetPeriod,
        nowEpochMs: Long,
        timeZone: TimeZone = TimeZone.currentSystemDefault()
    ): PacingResult = calculatePacing(
        spent = spent,
        budgetAmount = budgetAmount,
        period = period,
        now = Instant.fromEpochMilliseconds(nowEpochMs),
        timeZone = timeZone
    )

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
}
