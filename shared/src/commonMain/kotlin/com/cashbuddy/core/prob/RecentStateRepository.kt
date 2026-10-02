// NO-NETWORK
package com.cashbuddy.core.prob

import com.cashbuddy.platform.currentTimeMillis
import kotlin.math.abs

data class RecentTx(
    val amount: Double,
    val merchant: String?,
    val sourcePackage: String?,
    val timestamp: Long
)

class RecentStateRepository(private val maxEntries: Int = 20) {
    private val entries = ConcurrentHashMap<Long, RecentTx>()
    private val rawTimestamps = ConcurrentHashMap<Long, String>()
    private var sequence = 0L

    fun record(tx: RecentTx) {
        val seq = ++sequence
        entries[seq] = tx
        // Prune older entries if size exceeds maxEntries
        if (entries.size > maxEntries) {
            val oldestKey = entries.keys.minOrNull()
            if (oldestKey != null) {
                entries.remove(oldestKey)
            }
        }
    }

    fun record(amount: Double, merchant: String?, sourcePackage: String?, timestamp: Long = currentTimeMillis()) {
        record(RecentTx(amount, merchant, sourcePackage, timestamp))
    }

    fun recordRaw(timestamp: Long, packageName: String?) {
        rawTimestamps[timestamp] = packageName ?: ""
        // Prune raw timestamps older than 2 minutes
        val cutoff = timestamp - 120_000L
        val oldKeys = rawTimestamps.keys.filter { it < cutoff }
        for (k in oldKeys) {
            rawTimestamps.remove(k)
        }
    }

    fun all(): List<RecentTx> = entries.values.toList().sortedByDescending { it.timestamp }

    fun clear() {
        entries.clear()
        rawTimestamps.clear()
    }

    /**
     * Checks if a similar amount (within ₹1.0) was logged recently (default 5 min).
     * Conditionally requires EITHER the merchant to match OR the source package to match,
     * so two independent transactions (e.g. Rapido ₹50 and Zomato ₹50) are NOT penalized.
     */
    fun recentSimilarAmount(
        newAmount: Double,
        newMerchant: String?,
        newPackage: String?,
        now: Long = currentTimeMillis(),
        windowMs: Long = 5 * 60_000L
    ): Boolean {
        return all().any { tx ->
            abs(tx.amount - newAmount) < 1.0 &&
            abs(now - tx.timestamp) < windowMs &&
            (
                (!newMerchant.isNullOrBlank() && tx.merchant?.equals(newMerchant, ignoreCase = true) == true) ||
                (!newPackage.isNullOrBlank() && tx.sourcePackage == newPackage)
            )
        }
    }

    /**
     * Checks if the same merchant was logged in the last 30 minutes.
     */
    fun recentSameMerchant(
        newMerchant: String?,
        now: Long = currentTimeMillis(),
        windowMs: Long = 30 * 60_000L
    ): Boolean {
        if (newMerchant.isNullOrBlank()) return false
        return all().any { tx ->
            abs(now - tx.timestamp) < windowMs &&
            tx.merchant?.equals(newMerchant, ignoreCase = true) == true
        }
    }

    /**
     * Flags burst if more than threshold (default 3) distinct notifications arrived
     * in the last windowMs (default 60s).
     */
    fun burstDetected(
        now: Long = currentTimeMillis(),
        windowMs: Long = 60_000L,
        threshold: Int = 3
    ): Boolean {
        val windowStart = now - windowMs
        val txsInWindow = all().count { it.timestamp in windowStart..now }
        val rawInWindow = rawTimestamps.keys.count { it in windowStart..now }
        return (txsInWindow + rawInWindow) > threshold
    }
}
