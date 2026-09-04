package com.cashbuddy.domain.parser

import com.cashbuddy.domain.model.TransactionType

data class RawNotificationData(
    val packageName: String,
    val title: String,
    val text: String,
    val timestamp: Long
)

data class ParsedNotificationResult(
    val amount: Double,
    val type: TransactionType,
    val categoryName: String,
    val merchant: String,
    val accountId: String?,
    val sourceApp: String,
    val rawText: String,
    val confidence: Float,
    val timestamp: Long
)

class KotlinNotificationParser {

    companion object {
        val ALLOWED_PACKAGES = hashSetOf(
            // Curated UPI & Payment Apps
            "com.google.android.apps.nbu.paisa.user", // Google Pay
            "com.phonepe.app",                       // PhonePe
            "net.one97.paytm",                       // Paytm
            "in.org.npci.upiapp",                    // BHIM UPI
            "com.dreamplug.androidapp",              // CRED
            "in.amazon.mShop.android.shopping",      // Amazon Pay
            "com.whatsapp",                          // WhatsApp Pay
            "com.naviapp",                           // Navi
            "com.mobikwik_new",                      // MobiKwik
            "com.freecharge.android",                // FreeCharge
            "com.samsung.android.spay",              // Samsung Pay
            "com.cred.android",                      // CRED alternative

            // Private & Public Banks
            "com.snapwork.hdfc",                     // HDFC Bank
            "com.csam.icici.bank.imobile",           // ICICI iMobile
            "com.axis.mobile",                       // Axis Mobile
            "com.msf.kbank.mobile",                  // Kotak 811
            "com.indusind.mpassbook",                // IndusInd Bank
            "com.idfcfirstbank.optimus",             // IDFC FIRST Bank
            "com.fedmobile",                         // Federal Bank
            "com.yesbank",                           // YES Bank
            "com.rblbank.mobank",                    // RBL MoBank
            "com.bandhan.mpassbook",                 // Bandhan Bank
            "com.sbi.lotusintouch",                  // SBI YONO
            "com.sbicard.customerapp",               // SBI Card
            "com.bankofbaroda.mconnect",             // BoB World
            "com.pnb.one",                           // PNB ONE
            "com.canarabank.mobility",               // Canara ai1
            "com.unionbank.ecommerce.mobile.android",// Union Vyom
            "com.infrasoft.indianbank",              // IndOASIS
            "com.boi.omnineo",                       // BOI Mobile
            "com.cbi.mobile",                        // Central Bank of India
            "com.uco.ucobank",                       // UCO Bank
            "com.psb.mobile",                        // Punjab & Sind Bank
            "com.iob.mconnect",                      // IOB Mobile

            // Neobanks & Modern Cards
            "money.jupiter",                         // Jupiter Neobank
            "money.fi.banking",                      // Fi Money
            "com.onecard.app",                       // OneCard
            "org.slicepay",                          // Slice
            "in.uni.cards",                          // Uni Cards
            "com.scapia.cards",                      // Scapia Federal Card
            "com.niyo.equitas",                      // NiyoX
            "com.fampay.in"                          // FamPay
        )

        private val AMOUNT_REGEX = Regex(
            """(?:(?:₹|Rs\.?|INR)\s*([0-9]{1,3}(?:,[0-9]{3})*(?:\.[0-9]{1,2})?|[0-9]+(?:\.[0-9]{1,2})?))|(?:([0-9]{1,3}(?:,[0-9]{3})*(?:\.[0-9]{1,2})?|[0-9]+(?:\.[0-9]{1,2})?)\s*(?:₹|Rs\.?|INR))""",
            RegexOption.IGNORE_CASE
        )

        private val DEBIT_REGEX = Regex(
            """\b(debited|paid|spent|sent|transferred\s+to|purchase\s+at|withdrawn)\b""",
            RegexOption.IGNORE_CASE
        )

        private val CREDIT_REGEX = Regex(
            """\b(credited|received|added|refunded|deposited|cashback)\b""",
            RegexOption.IGNORE_CASE
        )

        private val DISCARD_REGEX = Regex(
            """\b(otp|one time password|verification code|secret code|do not share|win|cashback up to|congratulations|pre-approved|deal|discount|apply now)\b""",
            RegexOption.IGNORE_CASE
        )

        private val ACCOUNT_REGEX = Regex(
            """\b(?:a/c|account|card|acct)\s*(?:no\.?)?\s*(?:ending\s+)?(?:with\s+)?(?:x{2,}|[*]{2,})?([0-9]{3,4})\b""",
            RegexOption.IGNORE_CASE
        )

        private val VPA_REGEX = Regex("""[a-zA-Z0-9._-]+@[a-zA-Z0-9]+""")

        private val MERCHANT_PREFIX_REGEX = Regex(
            """(?:to|at|info:|vpa|towards|paid to)\s+([A-Za-z0-9\s&'-]{3,25}?)(?:\s+on|\s+ref|\s+via|\s+using|\s+bal|\s+upi|\.|\z)""",
            RegexOption.IGNORE_CASE
        )
    }

    fun parse(notification: RawNotificationData): ParsedNotificationResult? {
        // Layer 1: Source Validation
        val isAllowed = ALLOWED_PACKAGES.contains(notification.packageName)
        if (!isAllowed) {
            return null
        }

        // Layer 2: Content Classification & Discard
        val fullText = "${notification.title} ${notification.text}".trim()
        if (DISCARD_REGEX.containsMatchIn(fullText)) {
            return null
        }

        // Layer 3: Entity Extraction
        val amount = extractAmount(fullText) ?: return null
        val type = detectType(fullText) ?: return null
        val merchant = extractMerchant(fullText, notification.packageName)
        val accountId = extractAccountId(fullText)

        // Layer 4: Categorization & Confidence Scoring
        val category = guessCategory(merchant, fullText)
        val confidence = calculateConfidence(
            hasAmount = true,
            hasType = true,
            hasMerchant = merchant != "Unknown Merchant",
            hasAccount = accountId != null,
            isKnownSource = true,
            isRuleMatched = category != "Unknown"
        )

        return ParsedNotificationResult(
            amount = amount,
            type = type,
            categoryName = category,
            merchant = merchant,
            accountId = accountId,
            sourceApp = notification.packageName,
            rawText = fullText,
            confidence = confidence,
            timestamp = notification.timestamp
        )
    }

    private fun extractAmount(text: String): Double? {
        val match = AMOUNT_REGEX.find(text) ?: return null
        val group1 = match.groups[1]?.value
        val group2 = match.groups[2]?.value
        val rawNum = (group1 ?: group2)?.replace(",", "")?.trim() ?: return null
        return rawNum.toDoubleOrNull()
    }

    private fun detectType(text: String): TransactionType? {
        val hasDebit = DEBIT_REGEX.containsMatchIn(text)
        val hasCredit = CREDIT_REGEX.containsMatchIn(text)

        return when {
            hasDebit && !hasCredit -> TransactionType.DEBIT
            hasCredit && !hasDebit -> TransactionType.CREDIT
            hasDebit && hasCredit -> {
                // Determine which match appears earlier or has stronger intent
                val debitIdx = DEBIT_REGEX.find(text)?.range?.first ?: Int.MAX_VALUE
                val creditIdx = CREDIT_REGEX.find(text)?.range?.first ?: Int.MAX_VALUE
                if (debitIdx < creditIdx) TransactionType.DEBIT else TransactionType.CREDIT
            }
            else -> null
        }
    }

    private fun extractMerchant(text: String, packageName: String): String {
        // Check VPA first
        val vpaMatch = VPA_REGEX.find(text)
        if (vpaMatch != null) {
            val vpa = vpaMatch.value
            val handle = vpa.substringBefore("@")
            if (handle.length >= 3) {
                return handle.replace(".", " ").replace("-", " ").capitalizeWords()
            }
        }

        // Check merchant prefix
        val prefixMatch = MERCHANT_PREFIX_REGEX.find(text)
        if (prefixMatch != null) {
            val name = prefixMatch.groups[1]?.value?.trim()
            if (!name.isNullOrBlank() && name.length >= 3) {
                return name.capitalizeWords()
            }
        }

        // Fallback to app name
        return when {
            packageName.contains("swiggy") -> "Swiggy"
            packageName.contains("zomato") -> "Zomato"
            packageName.contains("uber") -> "Uber"
            packageName.contains("ola") -> "Ola"
            packageName.contains("amazon") -> "Amazon"
            packageName.contains("flipkart") -> "Flipkart"
            packageName.contains("blinkit") -> "Blinkit"
            packageName.contains("zepto") -> "Zepto"
            packageName.contains("bigbasket") -> "BigBasket"
            else -> "Unknown Merchant"
        }
    }

    private fun extractAccountId(text: String): String? {
        val match = ACCOUNT_REGEX.find(text) ?: return null
        val digits = match.groups[1]?.value ?: return null
        return "XX$digits"
    }

    private fun guessCategory(merchant: String, text: String): String {
        val m = merchant.lowercase()
        val t = text.lowercase()

        return when {
            m.contains("swiggy") || m.contains("zomato") || m.contains("mcdonald") ||
            m.contains("starbucks") || m.contains("kfc") || m.contains("domino") ||
            t.contains("restaurant") || t.contains("cafe") || t.contains("food") -> "Food"

            m.contains("uber") || m.contains("ola") || m.contains("rapido") ||
            m.contains("metro") || m.contains("irctc") || m.contains("fuel") ||
            m.contains("petrol") || m.contains("hpcl") || m.contains("bpcl") ||
            m.contains("indianoil") -> "Transport"

            m.contains("amazon") || m.contains("flipkart") || m.contains("myntra") ||
            m.contains("ajio") || m.contains("zara") || m.contains("h&m") ||
            m.contains("blinkit") || m.contains("zepto") || m.contains("bigbasket") -> "Shopping"

            m.contains("electricity") || m.contains("bescom") || m.contains("airtel") ||
            m.contains("jio") || m.contains("vi") || m.contains("broadband") ||
            m.contains("water") || m.contains("gas") || t.contains("bill payment") ||
            t.contains("utility") -> "Bills"

            m.contains("netflix") || m.contains("spotify") || m.contains("hotstar") ||
            m.contains("prime video") || m.contains("bookmyshow") || m.contains("pvr") ||
            m.contains("inox") -> "Entertainment"

            m.contains("apollo") || m.contains("1mg") || m.contains("pharmeasy") ||
            m.contains("hospital") || m.contains("pharmacy") || m.contains("clinic") ||
            t.contains("medical") -> "Health"

            m.contains("school") || m.contains("college") || m.contains("university") ||
            m.contains("udemy") || m.contains("coursera") || t.contains("tuition") ||
            t.contains("fees") -> "Education"

            m.contains("zerodha") || m.contains("groww") || m.contains("upstox") ||
            m.contains("kuvera") || m.contains("smallcase") || t.contains("mutual fund") ||
            t.contains("sip") || t.contains("equity") -> "Investments"

            t.contains("salary") || t.contains("payroll") || t.contains("stipend") -> "Salary"
            t.contains("refund") || t.contains("reversal") -> "Refund"
            t.contains("cashback") || t.contains("reward") -> "Gift"

            else -> "Unknown"
        }
    }

    private fun calculateConfidence(
        hasAmount: Boolean,
        hasType: Boolean,
        hasMerchant: Boolean,
        hasAccount: Boolean,
        isKnownSource: Boolean,
        isRuleMatched: Boolean
    ): Float {
        var score = 0.0f
        if (hasAmount) score += 0.30f
        if (hasType) score += 0.30f
        if (isKnownSource) score += 0.10f
        if (hasMerchant) score += 0.10f
        if (hasAccount) score += 0.05f
        if (isRuleMatched) score += 0.15f
        return score.coerceIn(0.0f, 1.0f)
    }

    private fun String.capitalizeWords(): String {
        return split(" ").joinToString(" ") { word ->
            word.lowercase().replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        }
    }
}
