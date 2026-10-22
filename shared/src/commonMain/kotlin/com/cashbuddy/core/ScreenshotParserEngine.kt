// NO-NETWORK
package com.cashbuddy.core

import com.cashbuddy.core.prob.HeuristicCategorizer

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

        // 5b. Extract VPA (UPI ID)
        val vpa = vpaRegex.find(trimmed)?.groupValues?.getOrNull(1)

        // 6. Category mapping via CategoryEngine
        val category = if (txType == TransactionType.CREDIT) {
            "Income"
        } else {
            val categoryMatch = categoryEngine.getCategory(
                rawMerchant = merchant,
                vpa = vpa,
                amount = amount
            )
            categoryMatch.category
        }

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

    /**
     * Detects the source app name from OCR text using content-first signals.
     * No hardcoded app allowlist — any UPI app works automatically.
     * The app brand is extracted dynamically from receipt headers,
     * transaction ID prefixes, or UPI structural indicators.
     */
    private fun detectAppName(text: String): String? {
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }

        // Strategy 1: Look for "<AppName> Transaction ID" header pattern
        // e.g. "PhonePe Transaction ID", "Google Pay Transaction ID"
        val txIdHeaderRegex = Regex("""(?i)^(.+?)\s+transaction\s+id$""")
        for (line in lines) {
            val m = txIdHeaderRegex.matchEntire(line.trim())
            if (m != null) {
                val candidate = m.groupValues[1].trim()
                if (candidate.isNotBlank() && candidate.length in 2..30) {
                    return candidate
                }
            }
        }

        // Strategy 2: Look for "Powered by <AppName>" or "<AppName> UPI" footer
        val poweredByRegex = Regex("""(?i)powered\s+by\s+(.+)""")
        for (line in lines) {
            val m = poweredByRegex.find(line.trim())
            if (m != null) {
                val candidate = m.groupValues[1].trim()
                if (candidate.isNotBlank() && candidate.length in 2..30) {
                    return candidate
                }
            }
        }

        // Strategy 3: If UPI structural indicators are present, default to "UPI"
        val lower = text.lowercase()
        val hasUpiIndicators = lower.contains("upi ref") ||
            lower.contains("upi transaction") ||
            lower.contains("unified payments interface") ||
            Regex("""[a-z0-9._-]+@[a-z]+""").containsMatchIn(lower) ||
            lower.contains("utr:") || lower.contains("utr ")
        if (hasUpiIndicators) return "UPI"

        return null
    }

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

        // Strategy 3: Line containing ₹ or Rs or INR
        for (line in text.lines()) {
            val trimmed = line.trim()
            if (trimmed.contains('₹') || trimmed.startsWith("Rs", ignoreCase = true) || trimmed.startsWith("INR", ignoreCase = true)) {
                val digits = trimmed.filter { it.isDigit() || it == '.' || it == ',' }.replace(",", "")
                digits.toDoubleOrNull()?.let { if (it > 0.0) return it }
            }
        }

        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }

        // Strategy 4: Line immediately following an isolated currency glyph (e.g. ML Kit reading '₹' as 'M', '₹', 'F', 'T', '?')
        for (i in lines.indices) {
            val l = lines[i]
            if (l.equals("₹") || l.equals("M", ignoreCase = true) || l.equals("F", ignoreCase = true) ||
                l.equals("Rs", ignoreCase = true) || l.equals("INR", ignoreCase = true) || l == "?" || l == "•"
            ) {
                val next = lines.getOrNull(i + 1)?.replace(",", "")?.toDoubleOrNull()
                if (next != null && next in 1.0..500000.0) {
                    return next
                }
            }
        }

        // Strategy 5: Standalone line with a numeric amount (with or without comma separators e.g. "1,149" or "1,149.00")
        val lower = text.lowercase()
        val hasPaymentContext = lower.contains("completed") || lower.contains("successful") ||
                lower.contains("confirmed") || lower.contains("paid") || lower.contains("payment") ||
                lower.contains("banking name") || lower.contains("upi") || lower.contains("pay again")
        if (hasPaymentContext) {
            for (line in lines) {
                val clean = line.removePrefix("?").removePrefix("=").removePrefix("*")
                    .removePrefix("M").removePrefix("m").removePrefix("₹").trim()
                if (clean.isNotEmpty() && clean.all { it.isDigit() || it == ',' || it == '.' }) {
                    val num = clean.replace(",", "").toDoubleOrNull()
                    if (num != null && num in 1.0..500000.0) {
                        return num
                    }
                }
            }
        }

        return null
    }

    private fun detectTxType(text: String): TransactionType {
        val lower = text.lowercase()
        val isCredit = lower.contains("received from") ||
            lower.contains("payment received") ||
            lower.contains("money received") ||
            lower.contains("credited to your") ||
            lower.contains("received into") ||
            (lower.contains("credited to") && !lower.contains("debited from")) ||
            lower.contains("deposited to") ||
            lower.contains("refund received") ||
            lower.contains("cashback received")

        return if (isCredit) {
            TransactionType.CREDIT
        } else {
            TransactionType.DEBIT
        }
    }

    private fun extractMerchant(text: String): String {
        // Strategy 0: "Banking name: BMTC" / "Merchant name: ..."
        val bankingPattern = Regex("""(?i)(?:banking\s+name|merchant\s+name)[:\s]+([A-Za-z0-9 \t&'.-]{2,40})""")
        val bMatch = bankingPattern.find(text)
        if (bMatch != null) {
            val candidate = cleanMerchantLine(bMatch.groupValues[1])
            if (candidate.isNotEmpty() && !isSystemLabel(candidate)) {
                return candidate
            }
        }

        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }

        // Strategy 1: Line right after or on "Received from", "Paid to", "To:", "Payment to", etc.
        for (i in lines.indices) {
            val line = lines[i]
            val lower = line.lowercase()

            if (lower == "received from" || lower == "received" || lower == "from" || lower == "from:" ||
                lower == "paid to" || lower == "payment to" || lower == "payment of" ||
                lower == "sent to" || lower == "to" || lower == "to:" ||
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

            // Inline checks: "Received from John", "Paid to Chai Point", "To: BMTC", etc.
            if (lower.startsWith("received from ")) {
                val candidate = cleanMerchantLine(line.substring(14))
                if (candidate.isNotEmpty() && !isSystemLabel(candidate)) return candidate
            }
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
            if (lower.startsWith("payment to ")) {
                val candidate = cleanMerchantLine(line.substring(11))
                if (candidate.isNotEmpty() && !isSystemLabel(candidate)) return candidate
            }
        }

        // Strategy 2: If this is CREDIT and "received from" wasn't explicitly caught, look for payer before "credited to"
        val lowerText = text.lowercase()
        if (lowerText.contains("received from")) {
            val after = text.substringAfter("Received from", "").substringAfter("received from", "")
            val firstLine = after.lines().map { it.trim() }.firstOrNull { it.isNotEmpty() && !isSystemLabel(it) }
            if (firstLine != null) {
                return cleanMerchantLine(firstLine)
            }
        }

        // Strategy 3: VPA identifier (e.g. swiggy@icici -> "Swiggy")
        // But only use if not "Credited to <User VPA>"
        val vpaMatch = vpaRegex.find(text)
        if (vpaMatch != null) {
            val vpa = vpaMatch.groupValues[1]
            // If the VPA comes immediately after "credited to", it is the user's account receiving the funds
            val vpaIdx = text.indexOf(vpa)
            val textBeforeVpa = if (vpaIdx > 0) text.substring(0, vpaIdx).lowercase() else ""
            val isUserReceivingAccount = textBeforeVpa.takeLast(30).contains("credited to")

            if (!isUserReceivingAccount) {
                val username = vpa.substringBefore('@')
                val cleaned = cleanMerchantLine(username)
                if (cleaned.isNotEmpty()) {
                    return cleaned.replaceFirstChar { it.uppercase() }
                }
            }
        }

        // Strategy 3: Check for commercial keywords via HeuristicCategorizer
        val tokens = text.lowercase().split(Regex("[^a-z0-9]")).filter { it.length >= 3 }
        for (token in tokens) {
            val match = HeuristicCategorizer.matchVpa(token)
            if (match != null) {
                return token.replaceFirstChar { it.uppercase() }
            }
        }

        return "UPI Merchant"
    }

    private fun extractUtr(text: String): String? {
        // Priority 1: Official 12-digit banking UTR / RRN (e.g. UTR: 995780225143)
        val bankingUtrRegex = Regex("""(?i)\b(?:UTR|UPI\s+ref(?:\s+no|\s+id|\s+number)?|RRN)[:\s]+(\d{12})\b""")
        val bMatch = bankingUtrRegex.find(text)
        if (bMatch != null) {
            return bMatch.groups[1]?.value?.trim()
        }

        // Priority 2: Generic UPI / Transaction ID
        val match = utrRegex.find(text) ?: return null
        return match.groups[1]?.value?.trim()
    }

    private fun cleanMerchantLine(line: String): String {
        var cleaned = line
            .substringBefore('\n')
            .substringBefore('\r')
            .replace("✓", "")
            .replace("✔", "")
            .replace("•", "")
            .trim()

        if (cleaned.lowercase().startsWith("banking name:")) {
            cleaned = cleaned.substringAfter(":").trim()
        }

        while (cleaned.endsWith('.') || cleaned.endsWith(',') || cleaned.endsWith(':')) {
            cleaned = cleaned.dropLast(1).trim()
        }

        return cleaned
    }

    private fun isSystemLabel(s: String): bool {
        val lower = s.lowercase().trim()
        return lower == "completed" ||
            lower == "successful" ||
            lower == "failed" ||
            lower == "pending" ||
            lower == "paid to" ||
            lower == "received from" ||
            lower == "credited to" ||
            lower == "view history" ||
            lower == "share receipt" ||
            lower == "transfer details" ||
            lower == "transaction details" ||
            lower.startsWith("upi transaction id") ||
            lower.startsWith("transaction id") ||
            lower.startsWith("phonepe transaction id") ||
            lower.startsWith("google transaction id") ||
            lower.startsWith("utr") ||
            lower.startsWith("powered by") ||
            lower.startsWith("₹") ||
            lower.startsWith("rs")
    }
}

private typealias bool = Boolean

// Top-level convenience function
fun parseScreenshotText(rawText: String): ScreenshotTransaction? {
    return ScreenshotParserEngine().parse(rawText)
}
