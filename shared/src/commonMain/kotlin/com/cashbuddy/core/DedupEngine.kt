// NO-NETWORK
package com.cashbuddy.core

import kotlin.math.abs

/**
 * Deduplication engine for CashBuddy.
 * Detects duplicate transactions captured across multiple channels (SMS, Screenshot, Notification).
 * Standard rule: Same amount + same merchant + within a 5-minute time window.
 */
fun isDuplicateTransaction(
    amount1: Double,
    merchant1: String,
    time1: Long,
    amount2: Double,
    merchant2: String,
    time2: Long,
    windowSecs: Long = 300L
): Boolean {
    // 1. Amount match within 1 paisa (0.01) tolerance
    if (abs(amount1 - amount2) > 0.01) {
        return false
    }

    // 2. Time difference within window (handle both ms and seconds)
    val timeDiff = abs(time1 - time2)
    val windowMs = if (windowSecs > 1000L) windowSecs else windowSecs * 1000L
    if (timeDiff > windowMs) {
        return false
    }

    // 3. Merchant similarity check
    val m1 = normalizeMerchant(merchant1)
    val m2 = normalizeMerchant(merchant2)

    if (m1.isEmpty() || m2.isEmpty()) {
        return false
    }

    if (m1 == m2) {
        return true
    }

    // Containment check for merchants with length >= 3
    if (m1.length >= 3 && m2.length >= 3 && (m1.contains(m2) || m2.contains(m1))) {
        return true
    }

    return false
}

fun normalizeMerchant(s: String): String {
    return s.trim()
        .lowercase()
        .replace("pvt ltd", "")
        .replace("private limited", "")
        .replace("ltd", "")
        .replace("india", "")
        .trim()
}
