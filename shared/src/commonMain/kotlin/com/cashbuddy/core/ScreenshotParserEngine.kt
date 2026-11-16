// NO-NETWORK
package com.cashbuddy.core


/**
 * Screenshot Parser for CashBuddy.
 * Parses OCR text extracted from Indian UPI payment screens.
 */
class ScreenshotParserEngine(
    private val categoryEngine: CategoryEngine = CategoryEngine()
) {
    companion object {
        /** Base confidence for a successfully parsed screenshot receipt. */
        private const val BASE_CONFIDENCE = 0.85f

        /** Per-bonus confidence bump (UTR found, app identified). */
        private const val BONUS_CONFIDENCE = 0.05f

        /** Known-category confidence floor. */
        private const val KNOWN_CATEGORY_CONFIDENCE = 0.90f

        /** Hard ceiling on screenshot-parse confidence (never fully auto-trusts OCR). */
        private const val MAX_CONFIDENCE = 0.99f

        /** Sanity clamp on OCR-extracted amounts (₹1 .. ₹5,00,000). */
        private const val MIN_AMOUNT = 1.0
        private const val MAX_AMOUNT = 500_000.0
    }


    private val amountRegex = Regex(
        """(?i)(?:₹|Rs\.?|INR)\s*([0-9]{1,3}(?:,[0-9]{2,3})*(?:\.[0-9]{1,2})?|[0-9]+(?:\.[0-9]{1,2})?)"""
    )

    private val paymentOfAmountRegex = Regex(
        """(?i)(?:payment\s+of|paid|sent)\s+([0-9]{1,3}(?:,[0-9]{2,3})*(?:\.[0-9]{1,2})?|[0-9]+(?:\.[0-9]{1,2})?)\s+(?:completed|successful|done)"""
    )

    private val utrRegex = Regex(
        """(?i)\b(?:UPI\s+(?:transaction\s+id|ref(?:\s+no|\s+id|\s+number)?)|Transaction\s+ID|UTR)[:\s]+([A-Za-z0-9]{8,35})"""
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
        var confidence = BASE_CONFIDENCE
        if (utrOrRef != null) confidence += BONUS_CONFIDENCE
        if (appName != null) confidence += BONUS_CONFIDENCE
        if (category != "Unknown") confidence = maxOf(confidence, KNOWN_CATEGORY_CONFIDENCE)
        confidence = minOf(confidence, MAX_CONFIDENCE)

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
        // e.g. "PhonePe Transaction ID" -> extracts "PhonePe"
        val txIdHeaderRegex = Regex("""(?i)^(.+?)\s+transaction\s+id$""")
        for (line in lines) {
            val m = txIdHeaderRegex.matchEntire(line.trim())
            if (m != null) {
                val candidate = m.groupValues[1].trim()
                if (candidate.isNotBlank() && candidate.length in 2..30 && !candidate.equals("upi", ignoreCase = true)) {
                    return candidate
                }
            }
        }

        // Strategy 2: Look for "Powered by <Brand>" footer pattern
        val poweredByRegex = Regex("""(?i)powered\s+by\s+(.+)""")
        for (line in lines) {
            val m = poweredByRegex.find(line.trim())
            if (m != null) {
                val candidate = m.groupValues[1].trim()
                if (candidate.isNotBlank() && candidate.length in 2..30 && !candidate.equals("upi", ignoreCase = true)) {
                    return candidate
                }
            }
        }

        // Strategy 3: Check if first line is a short title brand (e.g. "Google Pay", "Navi", "BHIM")
        // but not a transaction status, amount, or date
        val firstLine = lines.firstOrNull()?.trim() ?: ""
        if (firstLine.length in 3..25 &&
            !firstLine.contains(Regex("""\d""")) &&
            !isSystemLabel(firstLine) &&
            !firstLine.equals("paid to", ignoreCase = true) &&
            !firstLine.equals("received from", ignoreCase = true)
        ) {
            return firstLine
        }

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

        // Strategy 4: Line immediately following an isolated currency glyph
        val currencyGlyphRegex = Regex("""^(?:₹|Rs[.:]?|INR|[$€£¥])$""", RegexOption.IGNORE_CASE)
        for (i in lines.indices) {
            val l = lines[i]
            if (currencyGlyphRegex.matches(l)) {
                val next = lines.getOrNull(i + 1)?.replace(",", "")?.toDoubleOrNull()
                if (next != null && next in MIN_AMOUNT..MAX_AMOUNT) {
                    return next
                }
            }
        }

        // Strategy 5: Standalone line with a numeric amount (with or without comma separators e.g. "1,149" or "1,149.00")
        val lower = text.lowercase()
        val hasPaymentContext = com.cashbuddy.core.prob.Regexes.SUCCESS.containsMatchIn(lower) ||
                com.cashbuddy.core.prob.Regexes.DEBIT.containsMatchIn(lower) ||
                com.cashbuddy.core.prob.Regexes.CREDIT.containsMatchIn(lower) ||
                com.cashbuddy.core.prob.Regexes.UTR.containsMatchIn(lower) ||
                lower.contains("upi")
        if (hasPaymentContext) {
            for (line in lines) {
                val clean = line.removePrefix("?").removePrefix("=").removePrefix("*")
                    .removePrefix("M").removePrefix("m").removePrefix("₹").trim()
                if (clean.isNotEmpty() && clean.all { it.isDigit() || it == ',' || it == '.' }) {
                    val num = clean.replace(",", "").toDoubleOrNull()
                    if (num != null && num in MIN_AMOUNT..MAX_AMOUNT) {
                        return num
                    }
                }
            }
        }

        return null
    }

    private fun detectTxType(text: String): TransactionType {
        return if (com.cashbuddy.core.prob.Regexes.CREDIT.containsMatchIn(text)) {
            TransactionType.CREDIT
        } else {
            TransactionType.DEBIT
        }
    }

    private val merchantHeaderLineRegex = Regex("""(?i)^(?:received(?:\s+from)?|from:?|paid\s+to|payment\s+(?:to|of)|sent\s+to|to:?|.*successfully\s+to)$""")
    private val merchantInlineRegex = Regex("""(?i)^(?:received\s+from|paid\s+to|payment\s+to|sent\s+to|to:?)\s+(.+)""")

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

            if (merchantHeaderLineRegex.matches(line)) {
                val nextLine = lines.getOrNull(i + 1)
                if (nextLine != null) {
                    val candidate = cleanMerchantLine(nextLine)
                    if (candidate.isNotEmpty() && !isSystemLabel(candidate)) {
                        return candidate
                    }
                }
            }

            val inlineMatch = merchantInlineRegex.matchEntire(line)
            if (inlineMatch != null) {
                val candidate = cleanMerchantLine(inlineMatch.groupValues[1])
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

        return com.cashbuddy.core.MerchantLabels.UPI_MERCHANT
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

    private fun isSystemLabel(s: String): Boolean {
        val lower = s.lowercase().trim()
        return com.cashbuddy.core.prob.Regexes.SUCCESS.containsMatchIn(lower) ||
            lower == "failed" ||
            lower == "pending" ||
            lower.contains("details") ||
            lower.contains("history") ||
            lower.contains("receipt") ||
            lower.startsWith("powered by") ||
            com.cashbuddy.core.prob.Regexes.UTR.containsMatchIn(lower) ||
            com.cashbuddy.core.prob.Regexes.ACCOUNT.containsMatchIn(lower) ||
            com.cashbuddy.core.prob.Regexes.DEBIT.containsMatchIn(lower) ||
            com.cashbuddy.core.prob.Regexes.CREDIT.containsMatchIn(lower) ||
            com.cashbuddy.core.prob.Regexes.AMOUNT.containsMatchIn(lower) ||
            com.cashbuddy.core.prob.Regexes.AMOUNT_SUFFIX.containsMatchIn(lower)
    }
}

// Top-level convenience function
fun parseScreenshotText(rawText: String): ScreenshotTransaction? {
    return ScreenshotParserEngine().parse(rawText)
}
