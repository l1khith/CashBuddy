// NO-NETWORK
package com.cashbuddy.domain.model

/**
 * Domain entity representing an identified recurring payment pattern.
 *
 * @property id Unique identifier (0 for unpersisted cache records).
 * @property merchantNormalized Normalized merchant key.
 * @property amountPaise Exact transaction amount in integer paise.
 * @property occurrenceCount Number of occurrences observed within the detection window.
 * @property medianIntervalHours Median inter-arrival time in hours.
 * @property cv Coefficient of variation of inter-arrival times (must be <= 0.25).
 * @property periodHours Detected primary periodicity in hours (one of 12, 24, 48, 168).
 * @property autocorrelationPeak Peak autocorrelation value R(L) at the detected period (>= 0.4).
 * @property firstSeen Epoch timestamp of the earliest transaction matching this pattern.
 * @property lastSeen Epoch timestamp of the most recent transaction matching this pattern.
 * @property lastUpdated Epoch timestamp when this pattern was calculated or updated.
 */
data class RecurringPattern(
    val id: Long = 0L,
    val merchantNormalized: String,
    val amountPaise: Long,
    val occurrenceCount: Int,
    val medianIntervalHours: Double,
    val cv: Double,
    val periodHours: Int,
    val autocorrelationPeak: Double,
    val firstSeen: Long,
    val lastSeen: Long,
    val lastUpdated: Long
) {
    /**
     * Human-readable amount formatted in Rupees.
     */
    val amountInRupees: Double get() = amountPaise / 100.0

    /**
     * Descriptive label for the period (e.g. "twice-daily", "daily", "every-other-day", "weekly").
     */
    val periodLabel: String
        get() = when (periodHours) {
            12 -> "twice-daily"
            24 -> "daily"
            48 -> "every-other-day"
            168 -> "weekly"
            else -> "${periodHours}h"
        }

    /**
     * Estimated next occurrence timestamp based on last_seen + periodHours.
     */
    val nextExpectedTimestamp: Long
        get() = lastSeen + (periodHours * 3600_000L)
}
