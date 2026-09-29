// NO-NETWORK
package com.cashbuddy.domain.model

import com.cashbuddy.platform.currentTimeMillis
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

enum class TimePeriod(val displayName: String) {
    TODAY("Today"),
    THIS_WEEK("This Week"),
    THIS_MONTH("This Month"),
    THIS_YEAR("This Year"),
    ALL_TIME("All Time"),
    CUSTOM("Custom")
}

data class DateRangeSummary(
    val totalDebit: Double,
    val totalCredit: Double,
    val transactionCount: Long
)

object DateRangeHelper {

    fun calculateDateRange(
        period: TimePeriod,
        customStart: Long? = null,
        customEnd: Long? = null,
        nowMillis: Long = currentTimeMillis(),
        timeZone: TimeZone = TimeZone.currentSystemDefault()
    ): DateRange {
        val instant = Instant.fromEpochMilliseconds(nowMillis)
        val localDateTime = instant.toLocalDateTime(timeZone)
        val today = localDateTime.date

        return when (period) {
            TimePeriod.TODAY -> {
                val start = LocalDateTime(today, LocalTime(0, 0, 0, 0)).toInstant(timeZone).toEpochMilliseconds()
                val end = LocalDateTime(today, LocalTime(23, 59, 59, 999_000_000)).toInstant(timeZone).toEpochMilliseconds()
                DateRange(start, end)
            }
            TimePeriod.THIS_WEEK -> {
                val daysSinceMonday = today.dayOfWeek.ordinal
                val startOfWeekDate = today.minus(DatePeriod(days = daysSinceMonday))
                val endOfWeekDate = startOfWeekDate.plus(DatePeriod(days = 6))
                val start = LocalDateTime(startOfWeekDate, LocalTime(0, 0, 0, 0)).toInstant(timeZone).toEpochMilliseconds()
                val end = LocalDateTime(endOfWeekDate, LocalTime(23, 59, 59, 999_000_000)).toInstant(timeZone).toEpochMilliseconds()
                DateRange(start, end)
            }
            TimePeriod.THIS_MONTH -> {
                val startOfMonthDate = LocalDate(today.year, today.monthNumber, 1)
                val startOfNextMonthDate = if (today.monthNumber == 12) {
                    LocalDate(today.year + 1, 1, 1)
                } else {
                    LocalDate(today.year, today.monthNumber + 1, 1)
                }
                val start = LocalDateTime(startOfMonthDate, LocalTime(0, 0, 0, 0)).toInstant(timeZone).toEpochMilliseconds()
                val end = LocalDateTime(startOfNextMonthDate, LocalTime(0, 0, 0, 0)).toInstant(timeZone).toEpochMilliseconds() - 1L
                DateRange(start, end)
            }
            TimePeriod.THIS_YEAR -> {
                val startOfYearDate = LocalDate(today.year, 1, 1)
                val startOfNextYearDate = LocalDate(today.year + 1, 1, 1)
                val start = LocalDateTime(startOfYearDate, LocalTime(0, 0, 0, 0)).toInstant(timeZone).toEpochMilliseconds()
                val end = LocalDateTime(startOfNextYearDate, LocalTime(0, 0, 0, 0)).toInstant(timeZone).toEpochMilliseconds() - 1L
                DateRange(start, end)
            }
            TimePeriod.ALL_TIME -> {
                DateRange(0L, Long.MAX_VALUE)
            }
            TimePeriod.CUSTOM -> {
                DateRange(
                    startTimestamp = customStart ?: 0L,
                    endTimestamp = customEnd ?: Long.MAX_VALUE
                )
            }
        }
    }
}
