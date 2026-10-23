// NO-NETWORK
package com.cashbuddy.core.prob

/**
 * Classifies source into BANK | UPI_APP | MERCHANT_APP | UNKNOWN
 * based on content signals first, package name second.
 * Package name is a soft hint, NOT a gate.
 */
class SourceDetector {
    fun detect(packageNameOrSender: String, text: String): NotificationSource {
        val tLower = text.lowercase()
        val pLower = packageNameOrSender.lowercase()

        // 1. Content-first signals (Highest priority)
        val hasUpiContent = Regexes.UPI_HANDLE.containsMatchIn(text) ||
                tLower.contains("upi ref") ||
                tLower.contains("upi id") ||
                tLower.contains("vpa") ||
                tLower.contains("unified payments interface")

        val hasBankContent = tLower.contains("a/c") ||
                tLower.contains("acct") ||
                tLower.contains("account no") ||
                tLower.contains("avl bal") ||
                tLower.contains("available balance") ||
                tLower.contains("neft") ||
                tLower.contains("rtgs") ||
                tLower.contains("imps")

        val hasMerchantContent = (tLower.contains("order #") || tLower.contains("order id") ||
                tLower.contains("delivered") || tLower.contains("delivery")) &&
                !hasBankContent && !hasUpiContent

        if (hasUpiContent) return NotificationSource.UPI_APP
        if (hasBankContent) return NotificationSource.BANK
        if (hasMerchantContent) return NotificationSource.MERCHANT_APP

        // 2. Package / Sender structural heuristics (Soft hints)
        if (pLower.contains("phonepe") || pLower.contains(".upi") || pLower.contains("npci") ||
            pLower.contains("pay") || pLower.contains("paisa") || pLower.contains("wallet")) {
            return NotificationSource.UPI_APP
        }

        if (pLower.contains("bank") || SenderFingerprint.isBankLike(packageNameOrSender)) {
            return NotificationSource.BANK
        }

        return NotificationSource.UNKNOWN
    }
}
