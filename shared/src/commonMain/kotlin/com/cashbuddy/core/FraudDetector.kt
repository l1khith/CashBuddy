// NO-NETWORK
package com.cashbuddy.core

import kotlin.math.roundToLong

/**
 * Velocity & fraud detector for CashBuddy.
 * Rate-limits incoming notifications to prevent spam or duplicate burst parsing.
 */
class FraudDetector(
    private val maxTransactionsPerMinute: Int = 5
) {
    private val history = mutableMapOf<String, MutableList<Long>>()

    /**
     * Returns true if transaction velocity is normal, false if anomalous (too many in 1 min).
     */
    fun checkVelocity(packageName: String, timestamp: Long): Boolean {
        val timestamps = history.getOrPut(packageName) { mutableListOf() }
        val cutoff = timestamp - 60_000L
        timestamps.removeAll { it <= cutoff }

        return if (timestamps.size >= maxTransactionsPerMinute) {
            false
        } else {
            timestamps.add(timestamp)
            true
        }
    }

    companion object {
        /**
         * Computes deterministic signature for duplicate suppression (5-minute window bucket).
         */
        fun computeDedupHash(amount: Double, merchant: String, timestamp: Long): String {
            val windowBucket = timestamp / 300_000L
            val cents = (amount * 100.0).roundToLong()
            return "$cents:${merchant.trim().lowercase()}:$windowBucket"
        }
    }
}
