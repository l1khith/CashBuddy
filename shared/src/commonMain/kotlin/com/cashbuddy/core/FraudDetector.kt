// NO-NETWORK
package com.cashbuddy.core

import com.cashbuddy.domain.repository.RecurringPatternRepository
import com.cashbuddy.domain.repository.TransactionRepository
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Robust anomaly and outlier detector for transaction amounts using:
 * 1. Histogram Percentile Buckets (P50, P90, P95, P99)
 * 2. Modified Z-Score via Median Absolute Deviation (MAD) (Iglewicz and Hoaglin, 1993)
 * 3. Silent Anomaly Suppression for verified recurring patterns.
 *
 * Evaluates DEBIT transactions at merchant M over a 30-day window preceding the transaction.
 * Flags transactions where amount > P95 or M_i > 3.5 with sample size n >= 10.
 */
class FraudDetector(
    private val transactionRepository: TransactionRepository,
    private val recurringPatternRepository: RecurringPatternRepository? = null,
    private val windowDays: Int = 30
) {

    /**
     * Checks if a transaction amount is anomalous compared to the merchant's 30-day debit history.
     *
     * @param merchant Normalized merchant name.
     * @param amount The transaction amount to evaluate.
     * @param timestamp The transaction timestamp in epoch milliseconds.
     * @return [AnomalyResult] containing anomaly decision, percentile buckets, M_i score, median, and warning message.
     */
    suspend fun checkAnomaly(
        merchant: String,
        amount: Double,
        timestamp: Long
    ): AnomalyResult {
        // Step 1: Silent Anomaly Suppression for Recurring Patterns.
        // If this transaction matches a known recurring pattern (same merchant, amount within 10%),
        // suppress false anomalies immediately so routine habits are never flagged.
        if (recurringPatternRepository != null) {
            val patterns = recurringPatternRepository.findByMerchant(merchant)
            val amountPaise = kotlin.math.round(amount * 100.0).toLong()
            val matchesRecurring = patterns.any { pattern ->
                if (pattern.amountPaise > 0L) {
                    val diffPaise = abs(amountPaise - pattern.amountPaise)
                    (diffPaise * 10L) <= pattern.amountPaise
                } else {
                    false
                }
            }
            if (matchesRecurring) {
                return AnomalyResult(
                    isAnomalous = false,
                    score = 0.0,
                    median = amount,
                    mad = 0.0,
                    sampleSize = 0,
                    p50 = amount,
                    p90 = amount,
                    p95 = amount,
                    p99 = amount,
                    warningMessage = null
                )
            }
        }

        val windowMs = windowDays.toLong() * 24L * 60L * 60L * 1000L
        val fromTimestamp = timestamp - windowMs
        val historicalAmounts = transactionRepository.getAmountsAtMerchant(
            merchant = merchant,
            fromTimestamp = fromTimestamp,
            toTimestamp = timestamp
        )

        val n = historicalAmounts.size
        if (n < 10) {
            val median = if (n > 0) computeMedian(historicalAmounts) else 0.0
            return AnomalyResult(
                isAnomalous = false,
                score = 0.0,
                median = median,
                mad = 0.0,
                sampleSize = n,
                p50 = median,
                p90 = median,
                p95 = median,
                p99 = median,
                warningMessage = null
            )
        }

        val sorted = historicalAmounts.sorted()
        val p50 = computePercentile(sorted, 50.0)
        val p90 = computePercentile(sorted, 90.0)
        val p95 = computePercentile(sorted, 95.0)
        val p99 = computePercentile(sorted, 99.0)

        val median = p50
        val deviations = historicalAmounts.map { abs(it - median) }
        val mad = computeMedian(deviations)

        if (mad < 1e-4) {
            val isAnomalous = if (median > 0.0 && amount != median) {
                val ratio = abs(amount - median) / median
                ratio >= 0.5
            } else if (median == 0.0 && amount > 0.0) {
                true
            } else {
                false
            }
            val score = if (isAnomalous) 99.0 else 0.0
            val warningMessage = if (isAnomalous) {
                buildWarningMessage(merchant, amount, p50)
            } else {
                null
            }
            return AnomalyResult(
                isAnomalous = isAnomalous,
                score = score,
                median = median,
                mad = 0.0,
                sampleSize = n,
                p50 = p50,
                p90 = p90,
                p95 = p95,
                p99 = p99,
                warningMessage = warningMessage
            )
        }

        val score = 0.6745 * (amount - median) / mad
        val isScoreAnomalous = score > 3.5
        // Flagged if score > 3.5, or if amount exceeds the 95th percentile and is significantly above median
        val isPercentileAnomalous = amount > p95 && (amount >= 1.5 * p50 || isScoreAnomalous)
        val isAnomalous = isScoreAnomalous || isPercentileAnomalous

        val warningMessage = if (isAnomalous) {
            buildWarningMessage(merchant, amount, p50)
        } else {
            null
        }

        return AnomalyResult(
            isAnomalous = isAnomalous,
            score = score,
            median = median,
            mad = mad,
            sampleSize = n,
            p50 = p50,
            p90 = p90,
            p95 = p95,
            p99 = p99,
            warningMessage = warningMessage
        )
    }

    private fun buildWarningMessage(merchant: String, amount: Double, p50: Double): String {
        val ratio = if (p50 > 0.0) (amount / p50).roundToInt() else 0
        return if (ratio >= 2) {
            "Unusual spend at $merchant: ₹${amount.toInt()} is ${ratio}x your normal order."
        } else {
            "Unusual spend at $merchant: ₹${amount.toInt()} exceeds normal range."
        }
    }

    fun computePercentile(sortedValues: List<Double>, p: Double): Double {
        if (sortedValues.isEmpty()) return 0.0
        if (sortedValues.size == 1) return sortedValues[0]
        val clampedP = p.coerceIn(0.0, 100.0)
        val rank = (clampedP / 100.0) * (sortedValues.size - 1)
        val lower = rank.toInt()
        val upper = minOf(lower + 1, sortedValues.size - 1)
        val fraction = rank - lower
        return sortedValues[lower] + fraction * (sortedValues[upper] - sortedValues[lower])
    }

    private fun computeMedian(values: List<Double>): Double {
        if (values.isEmpty()) return 0.0
        val sorted = values.sorted()
        val size = sorted.size
        return if (size % 2 == 1) {
            sorted[size / 2]
        } else {
            (sorted[(size / 2) - 1] + sorted[size / 2]) / 2.0
        }
    }
}

/**
 * Result data class for transaction amount anomaly evaluation.
 *
 * @property isAnomalous True if the transaction amount is a statistical outlier (amount > P95 or M_i > 3.5, n >= 10).
 * @property score Modified Z-Score (M_i).
 * @property median Historical median transaction amount.
 * @property mad Median Absolute Deviation of historical amounts.
 * @property sampleSize Number of historical samples evaluated in the window.
 * @property p50 50th percentile (median).
 * @property p90 90th percentile.
 * @property p95 95th percentile.
 * @property p99 99th percentile.
 * @property warningMessage Human-readable anomaly explanation if flagged.
 */
data class AnomalyResult(
    val isAnomalous: Boolean,
    val score: Double,
    val median: Double,
    val mad: Double,
    val sampleSize: Int,
    val p50: Double = median,
    val p90: Double = 0.0,
    val p95: Double = 0.0,
    val p99: Double = 0.0,
    val warningMessage: String? = null
)
