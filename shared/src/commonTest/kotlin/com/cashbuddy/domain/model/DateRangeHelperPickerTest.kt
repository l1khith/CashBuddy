// NO-NETWORK
package com.cashbuddy.domain.model

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Regression tests for the custom date-range timezone fix (audit finding 1.2).
 *
 * The old UI used Material3's UTC-midnight millis raw as query bounds, skewing the
 * window by the local UTC offset (for IST: excluded 00:00–05:30 of the start day,
 * included 00:00–05:30 of the day after the end day). These tests pin the corrected
 * semantics using fixed zones (IST +05:30 and UTC).
 */
class DateRangeHelperPickerTest {

    private val ist = TimeZone.of("Asia/Kolkata")
    private val utc = TimeZone.UTC

    /** 2026-11-01T00:00:00Z — exactly what Material3's DatePicker returns for Nov 1. */
    private fun pickerMillis(date: LocalDate): Long =
        date.atStartOfDayIn(utc).toEpochMilliseconds()

    private fun localMidnight(date: LocalDate, tz: TimeZone): Long =
        LocalDateTime(date, LocalTime(0, 0, 0, 0)).toInstant(tz).toEpochMilliseconds()

    @Test
    fun utcPickerMillisConvertToSameLocalDayStart() {
        val nov1 = LocalDate(2026, 11, 1)
        // IST local midnight of Nov 1 = 2026-10-31T18:30Z
        assertEquals(
            localMidnight(nov1, ist),
            DateRangeHelper.utcPickerMillisToLocalStartOfDay(pickerMillis(nov1), ist)
        )
    }

    @Test
    fun localDayBoundsSpanFullLocalDay() {
        val nov1 = LocalDate(2026, 11, 1)
        val bounds = DateRangeHelper.localDayBounds(pickerMillis(nov1), ist)
        assertEquals(localMidnight(nov1, ist), bounds.startTimestamp)
        // End must be the last representable millisecond of the LOCAL day,
        // not start + 24h - 1 (which leaks into the next day on 23h DST days).
        assertEquals(
            localMidnight(nov1, ist) + (24L * 60 * 60 * 1000) - 1,
            bounds.endTimestamp
        )
    }

    @Test
    fun transactionAtEarlyMorningOfStartDayIsInsidePickedRange() {
        // THE regression: a 00:30 IST transaction on the picked start day used to be
        // excluded (UTC-midnight start = 05:30 IST). It must now be inside the range.
        val startDay = LocalDate(2026, 11, 1)
        val endDay = LocalDate(2026, 11, 7)
        val rangeStart = DateRangeHelper.localDayBounds(pickerMillis(startDay), ist).startTimestamp
        val rangeEnd = DateRangeHelper.localDayBounds(pickerMillis(endDay), ist).endTimestamp

        val earlyMorningTx = LocalDateTime(
            startDay, LocalTime(0, 30, 0, 0)
        ).toInstant(ist).toEpochMilliseconds()
        assertTrue(earlyMorningTx >= rangeStart, "00:30 IST on start day must be >= range start")

        val lateEveningEndDay = LocalDateTime(
            endDay, LocalTime(23, 45, 0, 0)
        ).toInstant(ist).toEpochMilliseconds()
        assertTrue(lateEveningEndDay <= rangeEnd, "23:45 IST on end day must be <= range end")
    }

    @Test
    fun transactionJustAfterEndDayIsOutsidePickedRange() {
        val endDay = LocalDate(2026, 11, 7)
        val rangeEnd = DateRangeHelper.localDayBounds(pickerMillis(endDay), ist).endTimestamp
        // 00:15 IST on Nov 8 — used to be INCLUDED by the old UTC-based end bound.
        val nextMorning = LocalDateTime(
            LocalDate(2026, 11, 8), LocalTime(0, 15, 0, 0)
        ).toInstant(ist).toEpochMilliseconds()
        assertTrue(nextMorning > rangeEnd, "Next-day morning must be excluded")
    }

    @Test
    fun utcZoneBoundsMatchPickerMillisExactly() {
        val nov1 = LocalDate(2026, 11, 1)
        val bounds = DateRangeHelper.localDayBounds(pickerMillis(nov1), utc)
        assertEquals(pickerMillis(nov1), bounds.startTimestamp)
        assertEquals(pickerMillis(nov1) + (24L * 60 * 60 * 1000) - 1, bounds.endTimestamp)
    }

    @Test
    fun pickerMillisRoundTripThroughLocalStorage() {
        val nov15 = LocalDate(2026, 11, 15)
        val stored = DateRangeHelper.utcPickerMillisToLocalStartOfDay(pickerMillis(nov15), ist)
        assertEquals(
            pickerMillis(nov15),
            DateRangeHelper.localMillisToUtcPickerMillis(stored, ist),
            "Reopening the picker must highlight the same calendar day"
        )
    }

    @Test
    fun rollingDefaultMillisMapToCorrectCalendarDay() {
        // Defaults are "now - N days" (rolling), not midnight — the day derivation
        // must still resolve to the same local calendar day.
        val noonIstNov20 = LocalDateTime(
            LocalDate(2026, 11, 20), LocalTime(12, 0, 0, 0)
        ).toInstant(ist).toEpochMilliseconds()
        assertEquals(
            localMidnight(LocalDate(2026, 11, 20), ist),
            DateRangeHelper.localDayBounds(noonIstNov20, ist).startTimestamp
        )
    }
}
