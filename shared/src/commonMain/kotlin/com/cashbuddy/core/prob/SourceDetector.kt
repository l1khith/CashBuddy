// NO-NETWORK
package com.cashbuddy.core.prob

/**
 * Classifies source into BANK | UPI_APP | UNKNOWN
 * based on structural content signals first, sender fingerprint second.
 * Package name is a soft hint, NOT a gate.
 */
class SourceDetector {
    fun detect(packageNameOrSender: String, text: String): NotificationSource {
        // 1. Content-first signals via structural patterns
        val hasUpiContent = Regexes.UPI_HANDLE.containsMatchIn(text) ||
                Regexes.UTR.containsMatchIn(text)

        val hasBankContent = Regexes.ACCOUNT.containsMatchIn(text) ||
                Regexes.BALANCE.containsMatchIn(text)

        if (hasUpiContent) return NotificationSource.UPI_APP
        if (hasBankContent) return NotificationSource.BANK

        // 2. Structural sender heuristics (Soft hints)
        if (SenderFingerprint.isBankLike(packageNameOrSender)) {
            return NotificationSource.BANK
        }

        return NotificationSource.UNKNOWN
    }
}
