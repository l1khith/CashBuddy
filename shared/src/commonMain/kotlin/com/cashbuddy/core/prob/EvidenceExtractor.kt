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
                (raw.packageName != null && isMerchantPackageHint(raw.packageName)) ||
                isMerchantTextHint(tLower)

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
        val p = pkg.lowercase()
        return p.contains("swiggy") || p.contains("zomato") || p.contains("uber") ||
                p.contains("ola") || p.contains("blinkit") || p.contains("zepto") ||
                p.contains("bigbasket") || p.contains("amazon") || p.contains("flipkart") ||
                p.contains("smartq")
    }

    private fun isMerchantTextHint(text: String): Boolean {
        return text.startsWith("smartq") || text.contains("smartq ·") ||
                text.startsWith("zomato") || text.contains("zomato ·") ||
                text.startsWith("swiggy") || text.contains("swiggy ·") ||
                text.startsWith("uber") || text.contains("uber ·")
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

    fun extractMerchant(text: String): String? {
        // 1. UPI handle / VPA
        val vpa = Regexes.UPI_HANDLE.find(text)?.value
        if (vpa != null) {
            val handle = vpa.substringBefore("@").replace(".", " ").replace("-", " ").trim()
            if (handle.length >= 3) {
                return handle.split(" ").joinToString(" ") { word ->
                    word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                }
            }
        }
        // 2. Prefix "to ...", "paid to ...", "towards ..."
        val prefixRegex = Regex("""(?i)(?:paid\s+to|sent\s+to|transferred\s+to|towards|at|to)\s+([A-Za-z0-9\s&'-]{3,30}?)(?:\s+on|\s+ref|\s+via|\s+using|\s+bal|\s+upi|\.|\z)""")
        val m = prefixRegex.find(text)
        if (m != null) {
            val raw = m.groups[1]?.value?.trim()
            if (!raw.isNullOrBlank() && raw.length >= 2) {
                return raw.split(" ").joinToString(" ") { word ->
                    word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                }
            }
        }
        return null
    }
}
