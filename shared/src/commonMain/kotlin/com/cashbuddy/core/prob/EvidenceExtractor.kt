// NO-NETWORK
package com.cashbuddy.core.prob

import com.cashbuddy.domain.model.TransactionType

class EvidenceExtractor {
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
        val hasDebit = Regexes.DEBIT.containsMatchIn(tLower)
        val hasCredit = Regexes.CREDIT.containsMatchIn(tLower)
        val hasOtp = Regexes.OTP.containsMatchIn(tLower)
        val hasPromo = Regexes.PROMO.containsMatchIn(tLower)
        val hasOffer = Regexes.OFFER.containsMatchIn(tLower)
        val hasUpiHandle = Regexes.UPI_HANDLE.containsMatchIn(fullText)
        val hasBalance = Regexes.BALANCE.containsMatchIn(tLower)
        val hasVerb = Regexes.TX_VERB.containsMatchIn(tLower)
        val hasSuccess = Regexes.SUCCESS.containsMatchIn(tLower)
        val senderLooksBank = SenderFingerprint.isBankLike(raw.senderId ?: raw.title)
        val fromMerchantPackage = source == NotificationSource.MERCHANT_APP ||
                (raw.packageName != null && isMerchantPackageHint(raw.packageName))

        return Evidence(
            hasAmount = hasAmount,
            hasAccount = hasAccount,
            hasUtr = hasUtr,
            hasDebit = hasDebit,
            hasCredit = hasCredit,
            hasOtp = hasOtp,
            hasPromo = hasPromo,
            hasOffer = hasOffer,
            hasUpiHandle = hasUpiHandle,
            hasBalanceMention = hasBalance,
            hasTransactionVerb = hasVerb,
            hasSuccessWord = hasSuccess,
            senderLooksBank = senderLooksBank,
            fromMerchantPackage = fromMerchantPackage,
            recentSameAmount = recentSameAmount,
            recentSameMerchant = recentSameMerchant,
            velocityHigh = velocityHigh
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
