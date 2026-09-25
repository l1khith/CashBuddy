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

        // 1. Content-first signals
        val hasBankContent = tLower.contains("a/c") ||
                tLower.contains("acct") ||
                tLower.contains("account") ||
                tLower.contains("avl bal") ||
                tLower.contains("balance") ||
                tLower.contains("neft") ||
                tLower.contains("rtgs") ||
                tLower.contains("imps")

        val hasUpiContent = Regexes.UPI_HANDLE.containsMatchIn(text) ||
                tLower.contains("upi ref") ||
                tLower.contains("upi id") ||
                tLower.contains("vpa")

        val hasMerchantContent = tLower.contains("order") ||
                tLower.contains("delivery") ||
                tLower.contains("ride") ||
                tLower.contains("food") ||
                tLower.contains("dining") ||
                tLower.contains("trip") ||
                tLower.contains("ticket")

        if (hasBankContent) return NotificationSource.BANK
        if (hasUpiContent) return NotificationSource.UPI_APP
        if (hasMerchantContent) return NotificationSource.MERCHANT_APP

        // 2. Package name hints (soft)
        if (pLower.contains("bank")) return NotificationSource.BANK
        if (pLower.contains("paisa") || pLower.contains("pay") || pLower.contains("wallet")) {
            return NotificationSource.UPI_APP
        }
        if (pLower.contains("food") || pLower.contains("order") || pLower.contains("shop") ||
            pLower.contains("cab") || pLower.contains("ride") || pLower.contains("mart")) {
            return NotificationSource.MERCHANT_APP
        }

        return NotificationSource.UNKNOWN
    }
}
