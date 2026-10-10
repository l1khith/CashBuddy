// NO-NETWORK
package com.cashbuddy.core

import com.cashbuddy.domain.repository.TransactionRepository
import kotlin.math.abs

/**
 * Robust anomaly and outlier detector for transaction amounts using Modified Z-Score
 * via Median Absolute Deviation (MAD).
 *
 * Implements Iglewicz and Hoaglin (1993) robust outlier detection formula:
 * M_i = 0.6745 * (x_new - median(X)) / MAD
 *
 * Evaluates DEBIT transactions at merchant M over a 90-day window preceding the transaction.
 * Flags transactions where M_i > 3.5 and sample size n >= 10.
 */
class FraudDetector(
    private val transactionRepository: TransactionRepository
) {

    /**
     * Checks if a transaction amount is anomalous compared to the merchant's 90-day debit history.
     *
     * @param merchant Normalized merchant name.
     * @param amount The transaction amount to evaluate.
     * @param timestamp The transaction timestamp in epoch milliseconds.
     * @return [AnomalyResult] containing anomaly decision, M_i score, median, MAD, and sample size.
     */
    suspend fun checkAnomaly(
        merchant: String,
        amount: Double,
        timestamp: Long
    ): AnomalyResult {
        val windowMs = 90L * 24 * 60 * 60 * 1000L
        val fromTimestamp = timestamp - windowMs
        val historicalAmounts = transactionRepository.getAmountsAtMerchant(
            merchant = merchant,
            fromTimestamp = fromTimestamp,
            toTimestamp = timestamp
        )

        val n = historicalAmounts.size
        if (n < 10) {
            return AnomalyResult(
                isAnomalous = false,
                score = 0.0,
                median = if (n > 0) computeMedian(historicalAmounts) else 0.0,
                mad = 0.0,
                sampleSize = n
            )
        }

        val median = computeMedian(historicalAmounts)
        val deviations = historicalAmounts.map { abs(it - median) }
        val mad = computeMedian(deviations)

        if (mad == 0.0) {
            return AnomalyResult(
                isAnomalous = false,
                score = 0.0,
                median = median,
                mad = 0.0,
                sampleSize = n
            )
        }

        val score = 0.6745 * (amount - median) / mad
        val isAnomalous = score > 3.5

        return AnomalyResult(
            isAnomalous = isAnomalous,
            score = score,
            median = median,
            mad = mad,
            sampleSize = n
        )
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
 * @property isAnomalous True if the transaction amount is a statistical outlier (M_i > 3.5, n >= 10).
 * @property score Modified Z-Score (M_i).
 * @property median Historical median transaction amount.
 * @property mad Median Absolute Deviation of historical amounts.
 * @property sampleSize Number of historical samples evaluated in the 90-day window.
 */
data class AnomalyResult(
    val isAnomalous: Boolean,
    val score: Double,
    val median: Double,
    val mad: Double,
    val sampleSize: Int
)
