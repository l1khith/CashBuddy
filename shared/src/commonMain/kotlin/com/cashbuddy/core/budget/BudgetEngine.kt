// NO-NETWORK
package com.cashbuddy.core.budget

import com.cashbuddy.domain.model.AlertState
import com.cashbuddy.domain.model.Budget
import com.cashbuddy.domain.model.BudgetCategories
import com.cashbuddy.domain.model.BudgetPeriod
import com.cashbuddy.domain.model.BudgetStatus
import com.cashbuddy.domain.model.TransactionType
import com.cashbuddy.domain.repository.TransactionRepository
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

class BudgetEngine(
    private val transactionRepository: TransactionRepository,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault()
) {

    suspend fun statusFor(budget: Budget, now: Long): BudgetStatus {
        if (!budget.isActive || now < budget.startDate) {
            return BudgetStatus(
                budget = budget,
                spent = 0.0,
                remaining = budget.amount,
                percentUsed = 0.0f,
                state = AlertState.ON_TRACK
            )
        }

        val range = periodRange(budget.period, now)
        val spent = if (budget.category == BudgetCategories.GLOBAL) {
            transactionRepository.sumAll(range.first, range.last, TransactionType.DEBIT)
        } else {
            transactionRepository.sumByCategory(budget.category, range.first, range.last)
        }
        val remaining = budget.amount - spent
        val ratio = if (budget.amount > 0.0) spent / budget.amount else if (spent > 0.0) 1.0 else 0.0
        val percentUsed = if (budget.amount > 0.0) ((spent / budget.amount) * 100.0).toFloat() else 0.0f

        val state = when {
            ratio >= 1.0 -> AlertState.EXCEEDED
            ratio >= 0.80 -> AlertState.WARNING
            else -> AlertState.ON_TRACK
        }

        return BudgetStatus(
            budget = budget,
            spent = spent,
            remaining = remaining,
            percentUsed = percentUsed,
            state = state
        )
    }

    fun periodRange(period: BudgetPeriod, now: Long): LongRange {
        val instant = Instant.fromEpochMilliseconds(now)
        val localDateTime = instant.toLocalDateTime(timeZone)
        val today = localDateTime.date

        return when (period) {
            BudgetPeriod.WEEKLY -> {
                val daysSinceMonday = today.dayOfWeek.ordinal
                val startOfWeekDate = today.minus(DatePeriod(days = daysSinceMonday))
                val endOfWeekDate = startOfWeekDate.plus(DatePeriod(days = 6))
                val start = LocalDateTime(startOfWeekDate, LocalTime(0, 0, 0, 0))
                    .toInstant(timeZone)
                    .toEpochMilliseconds()
                val end = LocalDateTime(endOfWeekDate, LocalTime(23, 59, 59, 999_000_000))
                    .toInstant(timeZone)
                    .toEpochMilliseconds()
                start..end
            }
            BudgetPeriod.MONTHLY -> {
                val startOfMonthDate = LocalDate(today.year, today.monthNumber, 1)
                val startOfNextMonthDate = if (today.monthNumber == 12) {
                    LocalDate(today.year + 1, 1, 1)
                } else {
                    LocalDate(today.year, today.monthNumber + 1, 1)
                }
                val start = LocalDateTime(startOfMonthDate, LocalTime(0, 0, 0, 0))
                    .toInstant(timeZone)
                    .toEpochMilliseconds()
                val end = LocalDateTime(startOfNextMonthDate, LocalTime(0, 0, 0, 0))
                    .toInstant(timeZone)
                    .toEpochMilliseconds() - 1L
                start..end
            }
            BudgetPeriod.YEARLY -> {
                val startOfYearDate = LocalDate(today.year, 1, 1)
                val startOfNextYearDate = LocalDate(today.year + 1, 1, 1)
                val start = LocalDateTime(startOfYearDate, LocalTime(0, 0, 0, 0))
                    .toInstant(timeZone)
                    .toEpochMilliseconds()
                val end = LocalDateTime(startOfNextYearDate, LocalTime(0, 0, 0, 0))
                    .toInstant(timeZone)
                    .toEpochMilliseconds() - 1L
                start..end
            }
        }
    }
}
