// NO-NETWORK
package com.cashbuddy.core.analytics

import com.cashbuddy.domain.model.RecurringPattern
import com.cashbuddy.domain.model.Transaction
import com.cashbuddy.domain.model.TransactionType
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.math.roundToLong
import kotlin.math.sqrt

/**
 * Recurrence Detector for financial transaction streams based on day-of-week histogram analysis.
 *
 * An event is recurring if:
 * 1. Count in last 30 days >= 4
 * 2. Active days (days with count >= 3) >= 3 (or >= 2 for weekend patterns: Sat & Sun)
 * 3. CV_active <= 0.30
 *
 * Does not check inter-arrival times or autocorrelation.
 */
object RecurrenceDetector {

    const val WINDOW_DAYS: Int = 30
    const val WINDOW_MS: Long = WINDOW_DAYS * 24L * 3600_000L

    const val MIN_TOTAL_COUNT: Int = 4
    const val MIN_ACTIVE_DAY_COUNT: Int = 3
    const val MAX_CV: Double = 0.30

    data class Point(
        val timestamp: Long,
        val merchant: String,
        val amountPaise: Long,
        val isDebit: Boolean
    )

    data class DetectionResult(
        val isRecurring: Boolean,
        val count: Int,
        val activeDaysCount: Int,
        val inactiveDaysCount: Int,
        val activeDays: List<Int>, // 0=Mon, 1=Tue, ..., 6=Sun
        val inactiveDays: List<Int>,
        val cvActive: Double,
        val patternDescription: String
    )

    /**
     * Analyzes discrete transaction points for a single merchant/amount group.
     */
    fun analyzePoints(
        points: List<Point>,
        now: Long,
        timeZone: TimeZone = TimeZone.UTC
    ): DetectionResult {
        val windowStart = now - WINDOW_MS
        val relevant = points.filter { it.isDebit && it.timestamp in windowStart..now }
        val count = relevant.size

        // Step 1 — Build 7-element histogram over last 30 days counting occurrences per day of week (Mon through Sun)
        val histogram = IntArray(7)
        for (pt in relevant) {
            val local = Instant.fromEpochMilliseconds(pt.timestamp).toLocalDateTime(timeZone)
            val dayIdx = local.dayOfWeek.ordinal // 0=Mon..6=Sun
            histogram[dayIdx]++
        }

        // Step 2 — Count active days: d_i >= 3. Count inactive days: d_i <= 1.
        val activeDays = (0..6).filter { histogram[it] >= 3 }
        val inactiveDays = (0..6).filter { histogram[it] <= 1 }
        val activeDaysCount = activeDays.size
        val inactiveDaysCount = inactiveDays.size

        // Step 3 — Compute CV_active over only the active days
        val cvActive = if (activeDays.isEmpty()) {
            0.0
        } else {
            val activeCounts = activeDays.map { histogram[it].toDouble() }
            val mean = activeCounts.average()
            if (mean <= 0.0) {
                0.0
            } else {
                val variance = activeCounts.sumOf { (it - mean) * (it - mean) } / activeCounts.size.toDouble()
                val stdDev = sqrt(variance)
                stdDev / mean
            }
        }

        // Pattern description:
        // - "weekdays" if active days are Mon-Fri
        // - "weekends" if active days are Sat-Sun
        // - "daily" if active days are all 7
        // - "specific days" otherwise (list the active days)
        val activeSet = activeDays.toSet()
        val dayNames = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
        val patternDescription = when {
            activeDaysCount == 7 -> "daily"
            activeSet == setOf(0, 1, 2, 3, 4) -> "weekdays"
            activeSet == setOf(5, 6) -> "weekends"
            activeDaysCount > 0 -> "specific days: " + activeDays.joinToString(", ") { dayNames[it] }
            else -> "none"
        }

        // Step 4 — Recurring if:
        // (count in 30 days >= 4) && (active days >= 3 || weekend pattern) && (CV_active <= 0.30)
        val isWeekend = activeSet == setOf(5, 6)
        val hasEnoughActiveDays = activeDaysCount >= MIN_ACTIVE_DAY_COUNT || isWeekend
        val isRecurring = count >= MIN_TOTAL_COUNT && hasEnoughActiveDays && cvActive <= MAX_CV

        return DetectionResult(
            isRecurring = isRecurring,
            count = count,
            activeDaysCount = activeDaysCount,
            inactiveDaysCount = inactiveDaysCount,
            activeDays = activeDays,
            inactiveDays = inactiveDays,
            cvActive = cvActive,
            patternDescription = patternDescription
        )
    }

    /**
     * Detects recurring patterns from a list of domain transactions.
     * Only DEBIT transactions are considered (expenses only).
     *
     * @param transactions Historical transactions.
     * @param now Reference timestamp (defaults to latest transaction or system time).
     * @param timeZone TimeZone used for day-of-week evaluation (defaults to system default).
     * @return List of identified [RecurringPattern] records.
     */
    fun detect(
        transactions: List<Transaction>,
        now: Long = transactions.maxOfOrNull { it.timestamp } ?: 0L,
        timeZone: TimeZone = TimeZone.currentSystemDefault()
    ): List<RecurringPattern> {
        val points = transactions.map {
            Point(
                timestamp = it.timestamp,
                merchant = it.merchant.trim(),
                amountPaise = (it.amount * 100.0).roundToLong(),
                isDebit = it.type == TransactionType.DEBIT
            )
        }
        return detectFromPoints(points, now, timeZone)
    }

    /**
     * Core detection function operating on discrete points.
     */
    fun detectFromPoints(
        points: List<Point>,
        now: Long,
        timeZone: TimeZone = TimeZone.UTC
    ): List<RecurringPattern> {
        val windowStart = now - WINDOW_MS

        val relevant = points.filter {
            it.isDebit && it.timestamp in windowStart..now
        }

        val groups = relevant.groupBy {
            normalizeMerchantKey(it.merchant) to it.amountPaise
        }

        val patterns = mutableListOf<RecurringPattern>()

        for ((key, groupPoints) in groups) {
            val (normMerchant, amountPaise) = key
            val analysis = analyzePoints(groupPoints, now, timeZone)

            if (analysis.isRecurring) {
                val sorted = groupPoints.sortedBy { it.timestamp }
                val periodHours = if (analysis.patternDescription == "daily") 24 else 168
                patterns.add(
                    RecurringPattern(
                        id = 0L,
                        merchantNormalized = normMerchant,
                        amountPaise = amountPaise,
                        occurrenceCount = analysis.count,
                        medianIntervalHours = periodHours.toDouble(),
                        cv = analysis.cvActive,
                        periodHours = periodHours,
                        autocorrelationPeak = 1.0,
                        firstSeen = sorted.first().timestamp,
                        lastSeen = sorted.last().timestamp,
                        lastUpdated = now
                    )
                )
            }
        }

        return patterns
    }

    private fun normalizeMerchantKey(merchant: String): String {
        return merchant.trim().lowercase()
    }
}
