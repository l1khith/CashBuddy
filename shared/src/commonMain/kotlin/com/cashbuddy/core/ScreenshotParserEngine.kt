// NO-NETWORK
package com.cashbuddy.core

import com.cashbuddy.core.prob.AppLabelMap
import com.cashbuddy.core.prob.MerchantMap

/**
 * Screenshot Parser for CashBuddy.
 * Parses OCR text extracted from Indian UPI payment screens.
 */
class ScreenshotParserEngine(
    private val categoryEngine: CategoryEngine = CategoryEngine()
) {

    private val amountRegex = Regex(
        """(?i)(?:₹|Rs\.?|INR)\s*([0-9]{1,3}(?:,[0-9]{2,3})*(?:\.[0-9]{1,2})?|[0-9]+(?:\.[0-9]{1,2})?)"""
    )

    private val paymentOfAmountRegex = Regex(
        """(?i)(?:payment\s+of|paid|sent)\s+([0-9]{1,3}(?:,[0-9]{2,3})*(?:\.[0-9]{1,2})?|[0-9]+(?:\.[0-9]{1,2})?)\s+(?:completed|successful|done)"""
    )

    private val utrRegex = Regex(
        """(?i)(?:UPI\s+(?:transaction\s+id|ref(?:\s+no|\s+id|\s+number)?)|Transaction\s+ID|UTR)[:\s]+([A-Za-z0-9]{8,35})"""
    )

    private val vpaRegex = Regex(
        """(?i)\b([a-zA-Z0-9.\-_]{2,64}@[a-zA-Z]{2,32})\b"""
    )

    fun parse(rawText: String): ScreenshotTransaction? {
        val trimmed = rawText.trim()
        if (trimmed.isEmpty()) return null

        // 1. Detect App
        val appName = detectAppName(trimmed)

        // 2. Extract Amount
        val amount = extractAmount(trimmed) ?: return null
        if (amount <= 0.0) return null

        // 3. Detect Transaction Type (Debit vs Credit)
        val txType = detectTxType(trimmed)

        // 4. Extract Merchant
        val merchant = extractMerchant(trimmed)
        if (merchant.isEmpty()) return null

        // 5. Extract UTR / Reference ID
        val utrOrRef = extractUtr(trimmed)

        // 6. Category mapping via CategoryEngine
        val categoryMatch = categoryEngine.getCategory(merchant)
        val category = categoryMatch.category

        // 7. Calculate Confidence
        var confidence = 0.85f
        if (utrOrRef != null) confidence += 0.05f
        if (appName != null) confidence += 0.05f
        if (category != "Unknown") confidence = maxOf(confidence, 0.90f)
        confidence = minOf(confidence, 0.99f)

        return ScreenshotTransaction(
            amount = amount,
            transactionType = txType,
            merchant = merchant,
            category = category,
            utrOrRef = utrOrRef,
            appName = appName,
            confidence = confidence,
            rawText = trimmed
        )
    }

    private fun detectAppName(text: String): String? = AppLabelMap.label(text)

    private fun extractAmount(text: String): Double? {
        // Strategy 1: Standard ₹ / Rs / INR symbol prefix
        val match = amountRegex.find(text)
        if (match != null) {
            val numStr = match.groupValues[1].replace(",", "")
            numStr.toDoubleOrNull()?.let { return it }
        }

        // Strategy 2: "Payment of XX completed" (as seen in BMTC, PhonePe, Paytm receipts)
        val paymentMatch = paymentOfAmountRegex.find(text)
        if (paymentMatch != null) {
            val numStr = paymentMatch.groupValues[1].replace(",", "")
            numStr.toDoubleOrNull()?.let { return it }
        }

        // Strategy 3: Line starting with ₹ or Rs
        for (line in text.lines()) {
            val trimmed = line.trim()
            if (trimmed.startsWith('₹') || trimmed.startsWith("Rs", ignoreCase = true)) {
                val digits = trimmed.filter { it.isDigit() || it == '.' || it == ',' }.replace(",", "")
                digits.toDoubleOrNull()?.let { if (it > 0.0) return it }
            }
        }

        // Strategy 4: Standalone line with just a number between 1 and 100,000 surrounded by transit/payment context
        for (line in text.lines()) {
            val trimmed = line.trim()
            val num = trimmed.toDoubleOrNull()
            if (num != null && num in 1.0..100000.0) {
                // If the entire text contains confirmation words, accept standalone number
                val lower = text.lowercase()
                if (lower.contains("completed") || lower.contains("successful") || lower.contains("confirmed")) {
                    return num
                }
            }
        }

        return null
    }

    private fun detectTxType(text: String): TransactionType {
        val lower = text.lowercase()
        return if (lower.contains("received from") ||
            lower.contains("payment received") ||
            lower.contains("money received") ||
            lower.contains("credited to")
        ) {
            TransactionType.CREDIT
        } else {
            TransactionType.DEBIT
        }
    }

    private fun extractMerchant(text: String): String {
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }

        // Strategy 1: Line right after or on "Paid to", "To:", "Payment to", etc.
        for (i in lines.indices) {
            val line = lines[i]
            val lower = line.lowercase()

            if (lower == "paid to" || lower == "payment to" || lower == "payment of" ||
                lower == "sent to" || lower == "to" || lower == "to:" ||
                lower == "received from" || lower == "from" || lower == "from:" ||
                lower.endsWith("successfully to")
            ) {
                val nextLine = lines.getOrNull(i + 1)
                if (nextLine != null) {
                    val candidate = cleanMerchantLine(nextLine)
                    if (candidate.isNotEmpty() && !isSystemLabel(candidate)) {
                        return candidate
                    }
                }
            }

            // Inline checks: "Paid to Chai Point", "To: BMTC", etc.
            if (lower.startsWith("paid to ")) {
                val candidate = cleanMerchantLine(line.substring(8))
                if (candidate.isNotEmpty() && !isSystemLabel(candidate)) return candidate
            }
            if (lower.startsWith("to: ")) {
                val candidate = cleanMerchantLine(line.substring(4))
                if (candidate.isNotEmpty() && !isSystemLabel(candidate)) return candidate
            }
            if (lower.startsWith("to ")) {
                val candidate = cleanMerchantLine(line.substring(3))
                if (candidate.isNotEmpty() && !isSystemLabel(candidate)) return candidate
            }
            if (lower.startsWith("received from ")) {
                val candidate = cleanMerchantLine(line.substring(14))
                if (candidate.isNotEmpty() && !isSystemLabel(candidate)) return candidate
            }
            if (lower.startsWith("payment to ")) {
                val candidate = cleanMerchantLine(line.substring(11))
                if (candidate.isNotEmpty() && !isSystemLabel(candidate)) return candidate
            }
        }

        // Strategy 2: VPA identifier (e.g. swiggy@icici -> "Swiggy")
        val vpaMatch = vpaRegex.find(text)
        if (vpaMatch != null) {
            val vpa = vpaMatch.groupValues[1]
            val username = vpa.substringBefore('@')
            val cleaned = cleanMerchantLine(username)
            if (cleaned.isNotEmpty()) {
                return cleaned.replaceFirstChar { it.uppercase() }
            }
        }

        // Strategy 3: Check for known merchants in MerchantMap mentioned anywhere in text
        val tokens = text.lowercase().split(Regex("[^a-z0-9]")).filter { it.length >= 3 }
        for (token in tokens) {
            val match = MerchantMap.lookup(token)
            if (match.source != MerchantMap.CategorySource.FALLBACK) {
                return token.replaceFirstChar { it.uppercase() }
            }
        }

        return "UPI Merchant"
    }

    private fun extractUtr(text: String): String? {
        val match = utrRegex.find(text) ?: return null
        return match.groups[1]?.value?.trim()
    }

    private fun cleanMerchantLine(line: String): String {
        var cleaned = line
            .replace("✓", "")
            .replace("✔", "")
            .replace("•", "")
            .trim()

        while (cleaned.endsWith('.') || cleaned.endsWith(',') || cleaned.endsWith(':')) {
            cleaned = cleaned.dropLast(1).trim()
        }

        return cleaned
    }

    private fun isSystemLabel(s: String): bool {
        val lower = s.lowercase()
        return lower == "completed" ||
            lower == "successful" ||
            lower == "failed" ||
            lower == "pending" ||
            lower == "transfer details" ||
            lower == "transaction details" ||
            lower.startsWith("upi transaction id") ||
            lower.startsWith("transaction id") ||
            lower.startsWith("utr") ||
            lower.startsWith("₹") ||
            lower.startsWith("rs")
    }
}

private typealias bool = Boolean

// Top-level convenience function
fun parseScreenshotText(rawText: String): ScreenshotTransaction? {
    return ScreenshotParserEngine().parse(rawText)
}
