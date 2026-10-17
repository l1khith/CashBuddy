// NO-NETWORK
package com.cashbuddy.core.analytics

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RecurrenceDetectorTest {

    // 2026-10-05 09:00:00 UTC is Monday
    private val baseMonday: Long = LocalDateTime(2026, 10, 5, 9, 0, 0, 0)
        .toInstant(TimeZone.UTC)
        .toEpochMilliseconds()

    private val oneDayMs: Long = 24L * 3600_000L

    @Test
    fun testBMTCWeekdayPatternDetected() {
        // TEST 1 — BMTC 5 days/week
        // Insert 20 transactions, Mon-Fri, over 4 weeks
        // Expected: recurring, active days = 5, CV_active = 0
        val points = mutableListOf<RecurrenceDetector.Point>()
        for (week in 0 until 4) {
            for (day in 0..4) { // Monday to Friday
                val t = baseMonday + (week * 7L + day) * oneDayMs
                points.add(
                    RecurrenceDetector.Point(
                        timestamp = t,
                        merchant = "BMTC",
                        amountPaise = 2000L,
                        isDebit = true
                    )
                )
            }
        }

        val now = points.last().timestamp + 3600_000L
        val result = RecurrenceDetector.analyzePoints(points, now, TimeZone.UTC)
        val patterns = RecurrenceDetector.detectFromPoints(points, now, TimeZone.UTC)

        assertTrue(result.isRecurring, "Weekday BMTC pattern should be recognized as recurring")
        assertEquals(5, result.activeDaysCount)
        assertEquals(0.0, result.cvActive, 0.001)
        assertEquals("weekdays", result.patternDescription)
        assertEquals(1, patterns.size)
        assertEquals("bmtc", patterns.first().merchantNormalized)
    }

    @Test
    fun testRandomCoffeeShopNotDetected() {
        // TEST 2 — Random coffee shop
        // Insert 11 transactions spread randomly across all days
        // Expected: not recurring, active days < 3
        val points = mutableListOf<RecurrenceDetector.Point>()
        // Distribution of 11 transactions across days (Mon..Sun): counts = [2, 1, 2, 1, 2, 2, 1]
        val dayOffsets = listOf(0, 0, 1, 2, 2, 3, 4, 4, 5, 5, 6)
        for (offset in dayOffsets) {
            val t = baseMonday + (offset * oneDayMs)
            points.add(
                RecurrenceDetector.Point(
                    timestamp = t,
                    merchant = "Coffee Day",
                    amountPaise = 15000L,
                    isDebit = true
                )
            )
        }

        val now = points.last().timestamp + 3600_000L
        val result = RecurrenceDetector.analyzePoints(points, now, TimeZone.UTC)
        val patterns = RecurrenceDetector.detectFromPoints(points, now, TimeZone.UTC)

        assertFalse(result.isRecurring, "Random coffee shop transactions should not be recurring")
        assertTrue(result.activeDaysCount < 3, "Expected active days < 3, got ${result.activeDaysCount}")
        assertTrue(patterns.isEmpty(), "Patterns should be empty for random coffee shop")
    }

    @Test
    fun testNetflixMonthlyNotDetected() {
        // TEST 3 — Netflix monthly
        // Insert 1 transaction
        // Expected: not recurring, count < 4
        val points = listOf(
            RecurrenceDetector.Point(
                timestamp = baseMonday,
                merchant = "Netflix",
                amountPaise = 64900L,
                isDebit = true
            )
        )

        val now = baseMonday + 3600_000L
        val result = RecurrenceDetector.analyzePoints(points, now, TimeZone.UTC)
        val patterns = RecurrenceDetector.detectFromPoints(points, now, TimeZone.UTC)

        assertFalse(result.isRecurring, "Single monthly transaction must not be marked recurring")
        assertTrue(result.count < 4, "Count must be < 4")
        assertTrue(patterns.isEmpty(), "Patterns should be empty when count < 4")
    }

    @Test
    fun testDailyMorningCoffeeDetected() {
        // TEST 4 — Daily morning coffee
        // Insert 28 transactions, every day, over 4 weeks
        // Expected: recurring, active days = 7
        val points = mutableListOf<RecurrenceDetector.Point>()
        for (day in 0 until 28) {
            val t = baseMonday + (day * oneDayMs)
            points.add(
                RecurrenceDetector.Point(
                    timestamp = t,
                    merchant = "Blue Tokai",
                    amountPaise = 22000L,
                    isDebit = true
                )
            )
        }

        val now = points.last().timestamp + 3600_000L
        val result = RecurrenceDetector.analyzePoints(points, now, TimeZone.UTC)
        val patterns = RecurrenceDetector.detectFromPoints(points, now, TimeZone.UTC)

        assertTrue(result.isRecurring, "Daily morning coffee should be recurring")
        assertEquals(7, result.activeDaysCount)
        assertEquals("daily", result.patternDescription)
        assertEquals(1, patterns.size)
    }

    @Test
    fun testWeekendOnlyGymDetected() {
        // TEST 5 — Weekend-only gym
        // Insert 8 transactions, Sat and Sun, over 4 weeks
        // Expected: recurring, active days = 2
        val points = mutableListOf<RecurrenceDetector.Point>()
        for (week in 0 until 4) {
            for (day in 5..6) { // Saturday and Sunday
                val t = baseMonday + (week * 7L + day) * oneDayMs
                points.add(
                    RecurrenceDetector.Point(
                        timestamp = t,
                        merchant = "Cult Gym",
                        amountPaise = 50000L,
                        isDebit = true
                    )
                )
            }
        }

        val now = points.last().timestamp + 3600_000L
        val result = RecurrenceDetector.analyzePoints(points, now, TimeZone.UTC)
        val patterns = RecurrenceDetector.detectFromPoints(points, now, TimeZone.UTC)

        assertTrue(result.isRecurring, "Weekend gym pattern should be recognized as recurring")
        assertEquals(2, result.activeDaysCount)
        assertEquals("weekends", result.patternDescription)
        assertEquals(1, patterns.size)
    }
}
