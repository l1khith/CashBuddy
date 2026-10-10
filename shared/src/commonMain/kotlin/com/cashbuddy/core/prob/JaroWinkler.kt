// NO-NETWORK
package com.cashbuddy.core.prob

import kotlin.math.max
import kotlin.math.min

/**
 * Computes Jaro-Winkler string similarity between two strings.
 *
 * Implements the standard Jaro string comparator augmented with Winkler's prefix
 * bonus adjustment (p = 0.1, max prefix length = 4).
 *
 * Output range is normalized in [0.0, 1.0], where 1.0 represents an exact match
 * and 0.0 indicates zero character overlap within the allowable search window.
 */
object JaroWinkler {

    /**
     * Calculates the Jaro-Winkler similarity score between strings [a] and [b].
     *
     * @param a First string to compare.
     * @param b Second string to compare.
     * @return Similarity score between 0.0 (no similarity) and 1.0 (identical).
     */
    fun similarity(a: String, b: String): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.0
        if (a == b) return 1.0

        val maxLen = max(a.length, b.length)
        val matchWindow = max(0, (maxLen / 2) - 1)

        val aMatched = BooleanArray(a.length)
        val bMatched = BooleanArray(b.length)

        var matches = 0

        for (i in a.indices) {
            val start = max(0, i - matchWindow)
            val end = min(b.length - 1, i + matchWindow)
            for (j in start..end) {
                if (!bMatched[j] && a[i] == b[j]) {
                    aMatched[i] = true
                    bMatched[j] = true
                    matches++
                    break
                }
            }
        }

        if (matches == 0) return 0.0

        var transpositions = 0
        var bIdx = 0
        for (i in a.indices) {
            if (aMatched[i]) {
                while (bIdx < b.length && !bMatched[bIdx]) {
                    bIdx++
                }
                if (bIdx < b.length && a[i] != b[bIdx]) {
                    transpositions++
                }
                bIdx++
            }
        }

        val m = matches.toDouble()
        val t = transpositions.toDouble() / 2.0
        val jaro = (1.0 / 3.0) * ((m / a.length) + (m / b.length) + ((m - t) / m))

        var prefixLength = 0
        val maxPrefix = min(4, min(a.length, b.length))
        while (prefixLength < maxPrefix && a[prefixLength] == b[prefixLength]) {
            prefixLength++
        }

        return jaro + 0.1 * prefixLength * (1.0 - jaro)
    }
}
