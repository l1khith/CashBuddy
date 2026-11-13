// NO-NETWORK
package com.cashbuddy.core.analytics

import com.cashbuddy.domain.model.RecurringPattern
import com.cashbuddy.domain.model.Transaction
import com.cashbuddy.domain.model.TransactionType
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.math.abs
import kotlin.math.roundToLong
import kotlin.math.sqrt

/**
 * Unified Recurrence Detector for financial transaction streams combining:
 * 1. Point Process Analysis (Inter-Arrival Time & Fano Factor)
 * 2. Day-of-Week Histogram Analysis
 *
 * An event is recurring if:
 * - Point Process: Fano Factor (Var/Mean) < 0.30 and Mean IAT ~ 24h, 168h, or 720h (or 12h)
 * - OR Histogram: Count in 30 days >= 4, Active days >= 3 (or weekend), and CV_active <= 0.30
 */
object RecurrenceDetector {

    const val WINDOW_DAYS: Int = 90
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

    data class PointProcessResult(
        val meanIATHours: Double,
        val varianceIATHours: Double,
        val fanoFactor: Double,
        val cv: Double = 0.0,
        val detectedPeriodHours: Int?, // 12, 24, 168, 720
        val isRecurring: Boolean
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
     * Point Process Analysis using Inter-Arrival Times (IAT) and Fano Factor.
     *
     * 1. Sort the transactions by timestamp.
     * 2. Calculate the Inter-Arrival Time (IAT) in hours between each.
     * 3. Calculate the Mean and Variance of the IAT.
     * 4. Compute the Fano Factor: Variance / Mean.
     * 5. If FanoFactor < 0.3 (consistent timing) AND Mean is roughly 24h, 168h (weekly), or 720h (monthly):
     *    mark as recurring.
     */
    fun analyzeIAT(timestamps: List<Long>): PointProcessResult {
        if (timestamps.size < 3) {
            return PointProcessResult(
                meanIATHours = 0.0,
                varianceIATHours = 0.0,
                fanoFactor = 0.0,
                detectedPeriodHours = null,
                isRecurring = false
            )
        }

        val sorted = timestamps.sorted()
        val intervals = sorted.zipWithNext { a, b -> (b - a).toDouble() / 3600_000.0 }
        val mean = intervals.average()
        if (mean <= 0.0) {
            return PointProcessResult(
                meanIATHours = 0.0,
                varianceIATHours = 0.0,
                fanoFactor = 0.0,
                detectedPeriodHours = null,
                isRecurring = false
            )
        }

        val variance = intervals.sumOf { (it - mean) * (it - mean) } / intervals.size.toDouble()
        val fanoFactor = variance / mean
        val cv = sqrt(variance) / mean

        val detectedPeriod = when {
            abs(mean - 12.0) <= 2.5 -> 12
            abs(mean - 24.0) <= 5.0 -> 24
            abs(mean - 168.0) <= 30.0 -> 168
            abs(mean - 720.0) <= 72.0 -> 720
            else -> null
        }

        val isRecurring = cv <= MAX_CV && detectedPeriod != null

        return PointProcessResult(
            meanIATHours = mean,
            varianceIATHours = variance,
            fanoFactor = fanoFactor,
            cv = cv,
            detectedPeriodHours = detectedPeriod,
            isRecurring = isRecurring
        )
    }

    /**
     * Analyzes discrete transaction points for a single merchant/amount group using day-of-week histograms.
     */
    fun analyzePoints(
        points: List<Point>,
        now: Long,
        timeZone: TimeZone = TimeZone.UTC
    ): DetectionResult {
        val windowStart = now - WINDOW_MS
        val relevant = points.filter { it.isDebit && it.timestamp in windowStart..now }
        val count = relevant.size

        // Build 7-element histogram over last 30 days counting occurrences per day of week (Mon through Sun)
        val histogram = IntArray(7)
        for (pt in relevant) {
            val local = Instant.fromEpochMilliseconds(pt.timestamp).toLocalDateTime(timeZone)
            val dayIdx = local.dayOfWeek.ordinal // 0=Mon..6=Sun
            histogram[dayIdx]++
        }

        // Count active days: d_i >= 3. Count inactive days: d_i <= 1.
        val activeDays = (0..6).filter { histogram[it] >= 3 }
        val inactiveDays = (0..6).filter { histogram[it] <= 1 }
        val activeDaysCount = activeDays.size
        val inactiveDaysCount = inactiveDays.size

        // Compute CV_active over only the active days
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

        val activeSet = activeDays.toSet()
        val dayNames = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
        val patternDescription = when {
            activeDaysCount == 7 -> "daily"
            activeSet == setOf(0, 1, 2, 3, 4) -> "weekdays"
            activeSet == setOf(5, 6) -> "weekends"
            activeDaysCount > 0 -> "specific days: " + activeDays.joinToString(", ") { dayNames[it] }
            else -> "none"
        }

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
            val sorted = groupPoints.sortedBy { it.timestamp }
            val timestamps = sorted.map { it.timestamp }

            // 1. Point Process IAT Analysis
            val iatResult = analyzeIAT(timestamps)

            // 2. Histogram Day-of-Week Analysis
            val histogramAnalysis = analyzePoints(groupPoints, now, timeZone)

            val isRecurring = iatResult.isRecurring || histogramAnalysis.isRecurring

            if (isRecurring) {
                val periodHours = iatResult.detectedPeriodHours
                    ?: (if (histogramAnalysis.patternDescription == "daily") 24 else 168)

                val intervals = sorted.zipWithNext { a, b -> (b.timestamp - a.timestamp) / 3600000.0 }
                val actualMedian = if (intervals.isNotEmpty()) {
                    val sortedIntervals = intervals.sorted()
                    if (sortedIntervals.size % 2 == 1) {
                        sortedIntervals[sortedIntervals.size / 2]
                    } else {
                        (sortedIntervals[sortedIntervals.size / 2 - 1] + sortedIntervals[sortedIntervals.size / 2]) / 2.0
                    }
                } else {
                    periodHours.toDouble()
                }

                val cv = if (iatResult.isRecurring) iatResult.cv else histogramAnalysis.cvActive

                patterns.add(
                    RecurringPattern(
                        id = 0L,
                        merchantNormalized = normMerchant,
                        amountPaise = amountPaise,
                        occurrenceCount = sorted.size,
                        medianIntervalHours = actualMedian,
                        cv = cv,
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
