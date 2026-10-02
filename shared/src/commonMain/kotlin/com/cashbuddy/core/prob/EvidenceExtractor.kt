// NO-NETWORK
package com.cashbuddy.core.prob

import com.cashbuddy.domain.model.TransactionType

class EvidenceExtractor(
    private val recentStateRepository: RecentStateRepository? = null
) {
    companion object {
        private val DEBIT_VERBS = Regex("""(?i)\b(debited|withdrawn|spent|paid|sent|deducted|transferred|charged)\b""")
        private val CREDIT_VERBS = Regex("""(?i)\b(credited|deposited|received|refunded)\b""")
        private val TRANSACTION_VERBS = Regex("""(?i)\b(debited|withdrawn|spent|paid|sent|deducted|transferred|charged|credited|deposited|received|refunded|payment|txn|transaction)\b""")

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
            Regex("""$CURR\s*\d[\d,.]*[kKlL]?\s*(?:per\s+(?:annum|month|year)|a\s+year|annually|salary|stipend)\b""", RegexOption.IGNORE_CASE),
            Regex("""\b(?:stipend|salary)\s+(?:up\s*to\s+)?$CURR\s*\d""", RegexOption.IGNORE_CASE)
        )

        private val JOB_PAID_MARKER = Regex("""(?i)\b(?:intern(?:ship)?\s*\(\s*paid\s*\)|\(\s*paid\s*(?:intern(?:ship)?)?\s*\)|paid\s+intern(?:ship)?|(?:engineer|developer|role|position)\s*\(\s*paid\s*\))\b""")
    }

    fun sentences(text: String): List<String> {
        if (text.isBlank()) return emptyList()
        // 1. Protect decimal numbers between digits: e.g. "100.00" -> "100\u000000"
        var s = Regex("""(\d)\.(\d)""").replace(text, "$1\u0000$2")
        // 2. Protect currency abbreviations: "Rs.", "Re.", "INR." -> "Rs\u0001"
        s = Regex("""(?i)\b(rs|re|inr)\.""").replace(s, "$1\u0001")
        // 3. Protect common bank / text abbreviations: "a/c.", "ac.", "no.", "ref.", "vpa.", "co.", "ltd."
        s = Regex("""(?i)\b(a/c|ac|no|ref|vpa|co|ltd|dr|mr|mrs)\.""").replace(s, "$1\u0001")
        // 4. Split on real sentence / clause delimiters: period, !, ?, newline, bullets (•, ·), pipes (|), em-dash (—)
        return s.split(Regex("""[.!?\n\r•·|—]+"""))
            .map { it.replace('\u0000', '.').replace('\u0001', '.').trim() }
            .filter { it.isNotEmpty() }
    }

    fun amountAndDebitSameSentence(text: String): Boolean {
        val sanitized = JOB_PAID_MARKER.replace(text, "")
        return sentences(sanitized).any { s ->
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
        val sanitized = JOB_PAID_MARKER.replace(text, "")
        return sentences(sanitized).any { s ->
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
            burstDetected = calcBurst
        )
    }

    private fun isMerchantPackageHint(pkg: String): Boolean {
        if (pkg.isBlank()) return false
        val segments = pkg.lowercase().split('.', '_', '-')
        return segments.any { segment ->
            MerchantMap.lookup(segment).source != MerchantMap.CategorySource.FALLBACK
        }
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

    fun extractMerchant(text: String, packageName: String? = null): String? {
        // 1. UPI handle / VPA
        val vpa = Regexes.UPI_HANDLE.find(text)?.value
        if (vpa != null) {
            val handle = vpa.substringBefore("@").replace(".", " ").replace("-", " ").trim()
            if (handle.length >= 3) {
                return formatMerchant(handle)
            }
        }

        // 2. Fvg / In favour of / Banking name (as seen in Union Bank, Indian Bank SMS, and GPay receipts)
        val fvgRegex = Regex("""(?i)(?:fvg:?|favoring|in\s+favou?r\s+of|banking\s+name:?)\s*([A-Za-z0-9\s&'.-]{2,30}?)(?:\s+avl|\s+bal|\s+ref|\s+on|\n|\.|\z)""")
        val mFvg = fvgRegex.find(text)
        if (mFvg != null) {
            val raw = mFvg.groups[1]?.value?.trim()
            if (!raw.isNullOrBlank() && raw.length >= 2) {
                return formatMerchant(raw)
            }
        }

        // 3. Line-by-line structure: "Paid to\n<Merchant>"
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        for (i in lines.indices) {
            val l = lines[i].lowercase()
            if (l == "paid to" || l == "to:" || l == "to" || l == "payment to") {
                val next = lines.getOrNull(i + 1)?.trim()
                if (!next.isNullOrBlank() && next.length in 2..40 &&
                    !next.startsWith("₹") && !next.startsWith("Rs", ignoreCase = true) &&
                    !next.lowercase().startsWith("banking name") && !next.lowercase().startsWith("receiver")
                ) {
                    return formatMerchant(next)
                }
            }
        }

        // 4. Prefix "paid to ...", "sent to ...", "towards ..."
        val prefixRegex = Regex("""(?i)(?:paid\s+to|sent\s+to|transferred\s+to|towards|at|to)\s+([A-Za-z0-9\s&'-]{2,30}?)(?:\s+on|\s+ref|\s+via|\s+using|\s+bal|\s+avl|\s+upi|\n|\.|\z)""")
        val m = prefixRegex.find(text)
        if (m != null) {
            val raw = m.groups[1]?.value?.trim()
            if (!raw.isNullOrBlank() && raw.length >= 2) {
                return formatMerchant(raw)
            }
        }

        // 5. MerchantMap brand token scan in text (e.g. "BMTC", "SmartQ", "Swiggy", "Zomato")
        val tokens = text.lowercase().split(Regex("[^a-z0-9]")).filter { it.length >= 3 }
        for (token in tokens) {
            val match = MerchantMap.lookup(token)
            if (match.source != MerchantMap.CategorySource.FALLBACK) {
                return token.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            }
        }

        // 6. MerchantMap brand token scan in packageName (e.g. "com.smartq" -> "Smartq")
        if (!packageName.isNullOrBlank()) {
            val pkgTokens = packageName.lowercase().split('.', '_', '-').filter { it.length >= 3 }
            for (token in pkgTokens) {
                val match = MerchantMap.lookup(token)
                if (match.source != MerchantMap.CategorySource.FALLBACK) {
                    return token.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                }
            }
        }

        return null
    }

    private fun formatMerchant(name: String): String {
        return name.split(" ").filter { it.isNotBlank() }.joinToString(" ") { word ->
            if (word.all { it.isLetter() && it.isUpperCase() } && word.length > 1) {
                word.lowercase().replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            } else {
                word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            }
        }
    }
}
