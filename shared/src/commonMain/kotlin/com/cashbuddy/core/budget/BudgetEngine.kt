// NO-NETWORK
package com.cashbuddy.core.budget

import com.cashbuddy.domain.model.AlertState
import com.cashbuddy.domain.model.Budget
import com.cashbuddy.domain.model.BudgetCategories
import com.cashbuddy.domain.model.BudgetPeriod
import com.cashbuddy.domain.model.BudgetStatus
import com.cashbuddy.domain.model.TransactionType
import com.cashbuddy.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.firstOrNull
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
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
    private val recurringPatternRepository: com.cashbuddy.domain.repository.RecurringPatternRepository? = null
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
        val periodTxs = transactionRepository.getByDateRange(range.first, range.last).firstOrNull() ?: emptyList()
        val patterns = recurringPatternRepository?.getAll()?.firstOrNull()
        return computeStatus(budget, now, patterns, periodTxs)
    }

    /**
     * Batched variant of [statusFor] for N budgets (audit fix P2, N+1 hoist).
     * Previously each budget re-issued its own range query + sum + pattern fetch
     * (≈4N queries per reload); this fetches the period's transactions ONCE per
     * DISTINCT period and derives every budget's status from the shared list,
     * replicating exactly the filters of `sumAll` / `sumByCategory` / the
     * committed-pattern matching.
     */
    suspend fun statusesFor(budgets: List<Budget>, now: Long): List<BudgetStatus> {
        if (budgets.isEmpty()) return emptyList()
        val patterns = recurringPatternRepository?.getAll()?.firstOrNull()

        val byPeriod = budgets.groupBy { it.period }
        val txsByPeriod = byPeriod.keys.associateWith { period ->
            val range = periodRange(period, now)
            transactionRepository.getByDateRange(range.first, range.last).firstOrNull() ?: emptyList()
        }

        return budgets.map { budget ->
            if (!budget.isActive || now < budget.startDate) {
                BudgetStatus(
                    budget = budget,
                    spent = 0.0,
                    remaining = budget.amount,
                    percentUsed = 0.0f,
                    state = AlertState.ON_TRACK
                )
            } else {
                computeStatus(budget, now, patterns, txsByPeriod.getValue(budget.period))
            }
        }
    }

    /**
     * Pure computation of one budget's status from a pre-fetched period transaction
     * list (already filtered by the query's `is_merged = 0` / timestamp bounds).
     * The category/sum filters mirror the SQL of `sumAll` (DEBIT, CONFIRMED/MODIFIED)
     * and `sumByCategory` (category name OR id-string match).
     */
    private fun computeStatus(
        budget: Budget,
        now: Long,
        patterns: List<com.cashbuddy.domain.model.RecurringPattern>?,
        periodTxs: List<com.cashbuddy.domain.model.Transaction>
    ): BudgetStatus {
        val range = periodRange(budget.period, now)
        val eligibleDebits = periodTxs.filter {
            it.type == TransactionType.DEBIT &&
                (it.status == com.cashbuddy.domain.model.TransactionStatus.CONFIRMED ||
                    it.status == com.cashbuddy.domain.model.TransactionStatus.MODIFIED)
        }
        val spent = if (budget.category == BudgetCategories.GLOBAL) {
            eligibleDebits.sumOf { it.amount }
        } else {
            eligibleDebits.filter { it.matchesBudgetCategory(budget.category) }.sumOf { it.amount }
        }
        val remaining = budget.amount - spent
        val ratio = if (budget.amount > 0.0) spent / budget.amount else if (spent > 0.0) 1.0 else 0.0
        val percentUsed = if (budget.amount > 0.0) ((spent / budget.amount) * 100.0).toFloat() else 0.0f

        val state = when {
            ratio >= 1.0 -> AlertState.EXCEEDED
            ratio >= 0.80 -> AlertState.WARNING
            else -> AlertState.ON_TRACK
        }

        var committed = 0.0
        var patternCount = 0
        if (patterns != null) {
            val categoryTxs = if (budget.category == BudgetCategories.GLOBAL) {
                periodTxs
            } else {
                periodTxs.filter { it.matchesBudgetCategory(budget.category) }
            }

            for (pattern in patterns) {
                val occurrences = categoryTxs.count { tx ->
                    tx.type == TransactionType.DEBIT &&
                    tx.merchant.trim().equals(pattern.merchantNormalized, ignoreCase = true) &&
                    kotlin.math.round(tx.amount * 100.0).toLong() == pattern.amountPaise
                }
                if (occurrences > 0) {
                    committed += (pattern.amountPaise / 100.0) * occurrences
                    patternCount++
                }
            }
        }
        val discretionary = maxOf(0.0, spent - committed)

        // Feature 5: Budget Pacing Projection
        // projected = (spent / daysElapsed) * totalDaysInPeriod
        val daysElapsed = maxOf(1L, (now - range.first) / 86400_000L + 1L)
        val totalDays = maxOf(1L, (range.last - range.first) / 86400_000L + 1L)
        val projected = (spent / daysElapsed.toDouble()) * totalDays.toDouble()

        return BudgetStatus(
            budget = budget,
            spent = spent,
            remaining = remaining,
            percentUsed = percentUsed,
            state = state,
            committed = committed,
            discretionary = discretionary,
            recurringPatternCount = patternCount,
            projected = projected
        )
    }

    /**
     * Mirrors `sumByCategory`'s `(c.name = :category OR CAST(t.category_id AS TEXT) = :category)`
     * match using the mapped row's categoryName/categoryId fields.
     */
    private fun com.cashbuddy.domain.model.Transaction.matchesBudgetCategory(category: String): Boolean =
        categoryName == category || categoryId.toString() == category

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
