// NO-NETWORK
package com.cashbuddy.core.prob

import com.cashbuddy.domain.model.TransactionType

class EvidenceExtractor(
    private val recentStateRepository: RecentStateRepository? = null,
    private val tokenEvidenceExtractor: TokenEvidenceExtractor? = null
) {
    companion object {
        private val DEBIT_VERBS = Regex("""(?i)\b(debited|withdrawn|spent|paid(?!\s+(?:to\s+)?you\b)|sent(?!\s+you\b)|transferred(?!\s+to\s+you\b)|deducted|charged)\b""")
        private val CREDIT_VERBS = Regex("""(?i)\b(credited|deposited|received|refunded|cashback|paid\s+(?:to\s+)?you|sent\s+you|transferred\s+to\s+you)\b""")
        private val TRANSACTION_VERBS = Regex("""(?i)\b(debited|withdrawn|spent|paid|sent|deducted|transferred|charged|credited|deposited|received|refunded|payment|txn|transaction)\b""")

        private val PAYMENT_RAILS = setOf(
            "phonepe", "paytm", "gpay", "google pay", "bhim", "upi", "netbanking", "mobikwik", "cred", "bank"
        )

        private const val CURR = """(?:₹|Rs[.:]?|INR|[$€£¥]|USD|EUR|GBP|CAD|AUD|AED|SGD|JPY)"""

        private val FORBIDDEN_SHAPES = listOf(
            // "₹600 off", "$600 off", "Rs.500 OFF", "Rs 500 off"
            Regex("""$CURR\s*\d[\d,.]*[kKlL]?\s*off\b""", RegexOption.IGNORE_CASE),
            // "up to ₹600", "up to $600", "upto Rs.500"
            Regex("""\b(?:up\s*to|upto)\s*$CURR\s*\d""", RegexOption.IGNORE_CASE),
            // "₹500 off 20%" or "20% off" or "₹500 %"
            Regex("""$CURR\s*\d[\d,.]*[kKlL]?\s*%"""),
            // "₹24.60 LPA", "₹3L CTC", "₹50k pm", "₹40k/pm", "₹20 LPA", "₹25,000/month", "$5,000/month"
            Regex("""$CURR\s*\d[\d,.]*[kKlL]?\s*(?:[/]\s*)?(?:LPA|CTC|P\.?A\.?|P\.?M\.?|pm|pa|month|annum|year)\b""", RegexOption.IGNORE_CASE),
            // "₹3L - ₹7L", "$500 - $1000", "Rs 500 - Rs 1000"
            Regex("""$CURR\s*\d[\d,.]*[kKlL]?\s*[-–]\s*(?:$CURR)?\s*\d""", RegexOption.IGNORE_CASE),
            // "₹5 lakh", "₹2 crore", "Rs 5 lakhs"
            Regex("""$CURR\s*\d[\d,.]*\s*(?:lakh|lakhs|crore|crores|lac|lacs)\b""", RegexOption.IGNORE_CASE),
            // Salary / stipend phrases near amount
            Regex("""$CURR\s*\d[\d,.]*[kKlL]?\s*(?:per\s+(?:annum|month|year)|a\s+year|annually|salary|stipend)\b""", RegexOption.IGNORE_CASE)
        )

        private val CURRENCY_NEAR_PAID = Regex(
            """(?i)\bpaid\b[^.!?\n]{0,30}(?:₹|Rs\.?|INR|\$|€|£)|(?:₹|Rs\.?|INR|\$|€|£)[^.!?\n]{0,30}\bpaid\b"""
        )

        private val DECIMAL_PROTECT_REGEX = Regex("""(\d)\.(\d)""")
        private val CURRENCY_ABBREV_REGEX = Regex("""(?i)\b(rs|re|inr)\.""")
        private val BANKING_ABBREV_REGEX = Regex("""(?i)\b(a/c|ac|no|ref|vpa|co|ltd|dr|mr|mrs)\.""")
        private val SENTENCE_SPLIT_REGEX = Regex("""[.!?\n\r•·|—]+""")

        private val PAYER_REGEX = Regex("""(?i)(?:^|[\n\r•·|—:]|\b(?:from|by)\s+)\s*([A-Za-z0-9\s&'.-]{2,40}?)\s+(?:paid\s+(?:to\s+)?you|sent\s+you|transferred\s+to\s+you)\b""")
        private val FVG_REGEX = Regex("""(?i)(?:fvg:?|favoring|in\s+favou?r\s+of|banking\s+name:?)\s*([A-Za-z0-9\s&'.-]{2,30}?)(?:\s+avl|\s+bal|\s+ref|\s+on|\n|\.|\z)""")
        private val RECIPIENT_REGEX = Regex("""(?i)(?:paid|sent|transferred|payment(?:\s+of)?)\s+(?:(?:₹|Rs[.:]?|INR|[$€£¥])\s*[\d,.]+\s+)?to\s+([A-Za-z0-9\s&'-]{2,30}?)(?:\s*\(|\s+is\b|\s+was\b|\s+on\b|\s+ref\b|\s+via\b|\s+using\b|\s+bal\b|\s+avl\b|\s+upi\b|\s+successfully\b|\n|\.|\z)""")
        private val RECEIVED_REGEX = Regex("""(?i)\b(?:received\s+(?:(?:₹|Rs[.:]?|INR|[$€£¥])\s*[\d,.]+\s+)?from|received\s+from)\s+([A-Za-z0-9\s&'-]{2,30}?)(?:\s+via|\s+using|\s+on|\s+ref|\s+upi|\s+bal|\s+avl|\n|\.|\z)""")
        private val PREFIX_REGEX = Regex("""(?i)(?:towards|for|at|to)\s+([A-Za-z0-9\s&'-]{2,30}?)(?:\s*\(|\s+is\b|\s+was\b|\s+on|\s+ref|\s+via|\s+using|\s+bal|\s+avl|\s+upi|\n|\.|\z)""")
        private val ALPHANUMERIC_TOKEN_REGEX = Regex("[^a-z0-9]")
        private val APP_PREFIX_CLEAN_REGEX = Regex("""(?i)^(?:google\s+pay|gpay|phonepe|paytm|bhim|cred)\s*[:·-]?\s*""")
    }

    fun sentences(text: String): List<String> {
        if (text.isBlank()) return emptyList()
        // 1. Protect decimal numbers between digits: e.g. "100.00" -> "100\u000000"
        var s = DECIMAL_PROTECT_REGEX.replace(text, "$1\u0000$2")
        // 2. Protect currency abbreviations: "Rs.", "Re.", "INR." -> "Rs\u0001"
        s = CURRENCY_ABBREV_REGEX.replace(s, "$1\u0001")
        // 3. Protect common bank / text abbreviations: "a/c.", "ac.", "no.", "ref.", "vpa.", "co.", "ltd."
        s = BANKING_ABBREV_REGEX.replace(s, "$1\u0001")
        // 4. Split on real sentence / clause delimiters: period, !, ?, newline, bullets (•, ·), pipes (|), em-dash (—)
        return s.split(SENTENCE_SPLIT_REGEX)
            .map { it.replace('\u0000', '.').replace('\u0001', '.').trim() }
            .filter { it.isNotEmpty() }
    }

    fun amountAndDebitSameSentence(text: String): Boolean {
        return sentences(text).any { s ->
            (Regexes.AMOUNT.containsMatchIn(s) || Regexes.AMOUNT_SUFFIX.containsMatchIn(s)) &&
            DEBIT_VERBS.containsMatchIn(s)
        }
    }

    fun amountAndCreditSameSentence(text: String): Boolean {
        return sentences(text).any { s ->
            (Regexes.AMOUNT.containsMatchIn(s) || Regexes.AMOUNT_SUFFIX.containsMatchIn(s)) &&
            CREDIT_VERBS.containsMatchIn(s)
        }
    }

    fun amountInTransactionContext(text: String): Boolean {
        return sentences(text).any { s ->
            (Regexes.AMOUNT.containsMatchIn(s) || Regexes.AMOUNT_SUFFIX.containsMatchIn(s)) &&
            TRANSACTION_VERBS.containsMatchIn(s)
        }
    }

    fun amountHasForbiddenShape(text: String): Boolean =
        FORBIDDEN_SHAPES.any { it.containsMatchIn(text) }

    fun extract(
        raw: RawMessage,
        source: NotificationSource = NotificationSource.UNKNOWN,
        recentSameAmount: Boolean = false,
        recentSameMerchant: Boolean = false,
        velocityHigh: Boolean = false
    ): Evidence {
        val fullText = if (raw.title.isNotBlank()) "${raw.title} ${raw.text}" else raw.text
        val tLower = fullText.lowercase()

        val hasAmount = Regexes.AMOUNT.containsMatchIn(fullText) || Regexes.AMOUNT_SUFFIX.containsMatchIn(fullText)
        val hasAccount = Regexes.ACCOUNT.containsMatchIn(fullText)
        val hasUtr = Regexes.UTR.containsMatchIn(fullText)
        val hasOtp = Regexes.OTP.containsMatchIn(tLower)
        val hasPromo = Regexes.PROMO.containsMatchIn(tLower)
        val hasOffer = Regexes.OFFER.containsMatchIn(tLower)
        val hasUpiHandle = Regexes.UPI_HANDLE.containsMatchIn(fullText)
        val hasBalance = Regexes.BALANCE.containsMatchIn(tLower)
        val hasSuccess = Regexes.SUCCESS.containsMatchIn(tLower)
        val senderLooksBank = SenderFingerprint.isBankLike(raw.senderId ?: raw.title)
        val fromMerchantPackage = source == NotificationSource.MERCHANT_APP ||
                (raw.packageName != null && isMerchantPackageHint(raw.packageName))

        val inContext = amountInTransactionContext(fullText)
        val debitInContext = amountAndDebitSameSentence(fullText)
        val creditInContext = amountAndCreditSameSentence(fullText)
        val forbiddenShape = amountHasForbiddenShape(fullText)

        val amt = extractAmount(fullText) ?: 0.0
        val merch = extractMerchant(fullText, raw.packageName)
        val now = raw.timestamp

        val calcRecentSimilar = recentStateRepository?.recentSimilarAmount(amt, merch, raw.packageName, now) ?: recentSameAmount
        val calcRecentMerchant = recentStateRepository?.recentSameMerchant(merch, now) ?: recentSameMerchant
        val calcBurst = recentStateRepository?.burstDetected(now) ?: velocityHigh
        val tokenLogOdds = tokenEvidenceExtractor?.extractSync(fullText)?.tokenLogOdds ?: 0.0

        return Evidence(
            hasAmount = hasAmount,
            hasAccount = hasAccount,
            hasUtr = hasUtr,
            hasOtp = hasOtp,
            hasPromo = hasPromo,
            hasOffer = hasOffer,
            hasUpiHandle = hasUpiHandle,
            hasBalanceMention = hasBalance,
            hasSuccessWord = hasSuccess,
            senderLooksBank = senderLooksBank,
            fromMerchantPackage = fromMerchantPackage,
            amountInTransactionContext = inContext,
            amountAndDebitSameSentence = debitInContext,
            amountAndCreditSameSentence = creditInContext,
            amountHasForbiddenShape = forbiddenShape,
            recentSimilarAmount = calcRecentSimilar,
            recentSameMerchant = calcRecentMerchant,
            burstDetected = calcBurst,
            tokenLogOdds = tokenLogOdds
        )
    }

    // OS-framework namespaces are platform hygiene, not merchant taxonomy: brand
    // extraction from e.g. "com.android.systemui" would yield the phantom brand
    // "Systemui" and wrongly set the merchant-package feature. The denylist in
    // isMerchantPackageHint covers OS namespaces ONLY — merchant gating itself
    // stays zero-allowlist (non-negotiable #8).

    private fun isMerchantPackageHint(pkg: String): Boolean {
        if (pkg.isBlank()) return false
        val p = pkg.lowercase()
        // OS-framework namespaces are never merchants. NOTE: no "$it." string
        // templates here — "com.android." as a member already carries its dot,
        // and template concatenation would yield "com.android..".
        if (p == "android" || p.startsWith("android.")) return false
        if (p.startsWith("com.android.")) return false
        val brand = DynamicMerchantResolver.extractBrandFromPackage(pkg)
        return !brand.isNullOrBlank() && brand.length >= 3
    }

    fun extractAmount(text: String): Double? {
        val m1 = Regexes.AMOUNT.find(text)
        if (m1 != null) {
            val s = m1.groups[1]?.value?.replace(",", "")?.trim()
            s?.toDoubleOrNull()?.let { return it }
        }
        val m2 = Regexes.AMOUNT_SUFFIX.find(text)
        if (m2 != null) {
            val s = m2.groups[1]?.value?.replace(",", "")?.trim()
            s?.toDoubleOrNull()?.let { return it }
        }
        return null
    }

    fun extractType(text: String): TransactionType? {
        val debitInSent = amountAndDebitSameSentence(text)
        val creditInSent = amountAndCreditSameSentence(text)
        if (debitInSent && !creditInSent) return TransactionType.DEBIT
        if (creditInSent && !debitInSent) return TransactionType.CREDIT

        val tLower = text.lowercase()
        val hasDebit = Regexes.DEBIT.containsMatchIn(tLower)
        val hasCredit = Regexes.CREDIT.containsMatchIn(tLower)
        return when {
            hasDebit && !hasCredit -> TransactionType.DEBIT
            hasCredit && !hasDebit -> TransactionType.CREDIT
            hasDebit && hasCredit -> {
                val dIdx = Regexes.DEBIT.find(tLower)?.range?.first ?: Int.MAX_VALUE
                val cIdx = Regexes.CREDIT.find(tLower)?.range?.first ?: Int.MAX_VALUE
                if (dIdx < cIdx) TransactionType.DEBIT else TransactionType.CREDIT
            }
            else -> null
        }
    }

    fun extractAccountLast4(text: String): String? {
        val m = Regexes.ACCOUNT.find(text) ?: return null
        return m.groups[1]?.value?.takeLast(4)
    }

    fun extractUtr(text: String): String? {
        val m = Regexes.UTR.find(text) ?: return null
        return m.groups[1]?.value?.trim()
    }

    fun extractMerchant(text: String, packageName: String? = null): String? {
        // 1. UPI handle / VPA
        val vpa = Regexes.UPI_HANDLE.find(text)?.value
        if (vpa != null) {
            val handle = vpa.substringBefore("@").replace(".", " ").replace("-", " ").trim()
            if (handle.length >= 3 && !handle.all { it.isDigit() } && !PAYMENT_RAILS.contains(handle.lowercase())) {
                val explicitRecipient = RECIPIENT_REGEX.find(text)?.groups?.get(1)?.value?.trim()
                if (!explicitRecipient.isNullOrBlank() && explicitRecipient.length in 2..30 && !PAYMENT_RAILS.contains(explicitRecipient.lowercase())) {
                    return formatMerchant(explicitRecipient)
                }
                return formatMerchant(handle)
            }
        }

        // 2. Incoming P2P payer before "paid you", "sent you", "transferred to you"
        val mPayer = PAYER_REGEX.find(text)
        if (mPayer != null) {
            val raw = mPayer.groups[1]?.value?.trim()
            if (!raw.isNullOrBlank() && raw.length in 2..40 && !PAYMENT_RAILS.contains(raw.lowercase())) {
                return formatMerchant(raw)
            }
        }

        // 3. Fvg / In favour of / Banking name (as seen in Union Bank, Indian Bank SMS, and GPay receipts)
        val mFvg = FVG_REGEX.find(text)
        if (mFvg != null) {
            val raw = mFvg.groups[1]?.value?.trim()
            if (!raw.isNullOrBlank() && raw.length >= 2 && !PAYMENT_RAILS.contains(raw.lowercase())) {
                return formatMerchant(raw)
            }
        }

        // 4. Line-by-line structure: "Paid to\n<Merchant>"
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        for (i in lines.indices) {
            val l = lines[i].lowercase()
            if (l == "paid to" || l == "to:" || l == "to" || l == "payment to") {
                val next = lines.getOrNull(i + 1)?.trim()
                if (!next.isNullOrBlank() && next.length in 2..40 &&
                    !next.startsWith("₹") && !next.startsWith("Rs", ignoreCase = true) &&
                    !next.lowercase().startsWith("banking name") && !next.lowercase().startsWith("receiver") &&
                    !PAYMENT_RAILS.contains(next.lowercase())
                ) {
                    return formatMerchant(next)
                }
            }
        }

        // 5. Outgoing recipient: "Paid ₹X to ...", "Sent ₹X to ...", "Paid to ...", "Sent to ..."
        val mRecipient = RECIPIENT_REGEX.find(text)
        if (mRecipient != null) {
            val raw = mRecipient.groups[1]?.value?.trim()
            if (!raw.isNullOrBlank() && raw.length in 2..30 && !PAYMENT_RAILS.contains(raw.lowercase())) {
                return formatMerchant(raw)
            }
        }

        // 6. Incoming from: "Received ₹X from ...", "Received from ..."
        val mReceived = RECEIVED_REGEX.find(text)
        if (mReceived != null) {
            val raw = mReceived.groups[1]?.value?.trim()
            if (!raw.isNullOrBlank() && raw.length in 2..30 && !PAYMENT_RAILS.contains(raw.lowercase())) {
                return formatMerchant(raw)
            }
        }

        // 7. General Prefix "towards ...", "for ...", "at ...", "to ..."
        val m = PREFIX_REGEX.find(text)
        if (m != null) {
            val raw = m.groups[1]?.value?.trim()
            if (!raw.isNullOrBlank() && raw.length >= 2 && !PAYMENT_RAILS.contains(raw.lowercase())) {
                return formatMerchant(raw)
            }
        }

        // 8. Fallback to package brand if available (e.g. "com.smartq" -> "Smartq")
        if (!packageName.isNullOrBlank()) {
            val pkgBrand = packageName.lowercase()
                .split('.', '_', '-')
                .filter { it.length >= 3 && it != "com" && it != "app" && it != "apps" && it != "android" && it != "user" }
                .lastOrNull()
            if (!pkgBrand.isNullOrBlank() && !PAYMENT_RAILS.contains(pkgBrand)) {
                return pkgBrand.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            }
        }

        // 9. If no explicit merchant/recipient could be extracted, return null to let user label it in Review Inbox
        return null
    }

    private fun formatMerchant(name: String): String {
        val cleaned = name.replace(APP_PREFIX_CLEAN_REGEX, "").trim()
            .trim('-', ':', '·', ' ', '•')
        return cleaned.split(" ").filter { it.isNotBlank() }.joinToString(" ") { word ->
            if (word.all { it.isLetter() && it.isUpperCase() }) {
                word.lowercase().replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            } else {
                word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            }
        }
    }
}
