// NO-NETWORK
package com.cashbuddy.core.prob

/**
 * Stage 2: Universal Gateway Token Stripping & Pure Algorithmic Merchant Resolution
 *
 * ZERO HARDCODED PACKAGE LISTS. ZERO HARDCODED MERCHANT DICTIONARIES.
 * 100% CONTENT-FIRST AND ALGORITHMIC.
 *
 * Mathematical formulation:
 * S_clean = g(S_raw) = S_raw \ ( G_prefix ∪ G_suffix ∪ V_handles )
 *
 * Where:
 * - G_prefix strips payment gateway and bank noise (INF*, UPI/, RAZORPAY*, etc.)
 * - G_suffix strips location codes, transaction IDs, corporate entity suffixes (Pvt Ltd, etc.)
 * - V_handles strips UPI domain handles (@ybl, @icici, etc.)
 *
 * For unknown apps and packages:
 * - Derives brand identity purely by parsing package segments (e.g. "com.thesmartq.smartq" -> "Smartq")
 * - Formats cleaned raw merchant strings into title-case canonical names dynamically.
 */
object DynamicMerchantResolver {

    const val FUZZY_THRESHOLD: Double = 0.70

    // Prefix patterns injected by payment aggregators, switches, and bank gateways
    private val GATEWAY_PREFIXES = listOf(
        Regex("""(?i)^(?:PAYTM[-*_\s]|INF\*|UPI[/_\s]|RAZORPAY\*|BILLDESK\*|CCAVENUE\*|AMZN\*|AMAZON\*|GPAY\*|PHONEPE\*|CRED\*|POS\s+|ECOM\s+|VPA\s+)"""),
        Regex("""(?i)^(?:to\s+|paid\s+to\s+|sent\s+to\s+|at\s+)""")
    )

    // Suffix patterns (locations, corporate abbreviations, random transaction hashes)
    private val GATEWAY_SUFFIXES = listOf(
        Regex("""(?i)(?:\*BLR|\*MUM|\*DEL|\*HYD|\*CHE|\*KOL|\*PUN|\*BANGALORE|\*MUMBAI|\*DELHI)$"""),
        Regex("""(?i)(?:\s+(?:pvt\s+ltd|private\s+limited|limited|ltd|india|store|online|pay|media\s+pvt\s+ltd|marketplace|designs|systems|commerce|services|retail|solutions|enterprises|ventures|infra))\b.*$"""),
        Regex("""(?i)[-_*#]\d{4,16}$""")
    )

    // UPI handles to strip: @icici, @ybl, @paytm, @okaxis, etc.
    private val UPI_HANDLE_REGEX = Regex("""(?i)@[a-zA-Z0-9.\-_]{2,32}\b""")

    // Common namespace domain prefixes to skip when parsing package names
    private val IGNORED_PACKAGE_SEGMENTS = setOf(
        "com", "in", "org", "net", "io", "app", "apps", "co", "android", "mobile", "client", "customer", "user"
    )

    data class Resolution(
        val canonicalName: String,
        val similarity: Double,
        val matchedVia: MatchSource
    )

    enum class MatchSource { PACKAGE_EXTRACTED, CONTENT_CLEANED, FALLBACK }

    /**
     * Cleaning function g(S_raw) = S_raw \ ( G_prefix ∪ G_suffix ∪ V_handles )
     */
    fun cleanRawMerchant(raw: String): String {
        if (raw.isBlank()) return ""
        var s = raw.trim()

        // 1. Strip UPI handles: zomato@ybl -> zomato
        s = UPI_HANDLE_REGEX.replace(s, "")

        // 2. Strip gateway prefixes
        for (prefix in GATEWAY_PREFIXES) {
            s = prefix.replace(s, "").trim()
        }

        // 3. Strip gateway suffixes and corporate noise
        for (suffix in GATEWAY_SUFFIXES) {
            s = suffix.replace(s, "").trim()
        }

        // 4. Clean extra punctuation / non-alphanumeric trailing
        s = s.trim('-', ':', '*', '/', '_', '#', '.', ' ', '•', '!')
        return s
    }

    /**
     * Algorithmically extracts a human-readable brand name from ANY Android package name.
     * Example:
     * - "com.thesmartq.smartq" -> "Smartq"
     * - "in.swiggy.android" -> "Swiggy"
     * - "com.application.zomato" -> "Zomato"
     * - "com.mycompany.coolapp" -> "Coolapp"
     */
    fun extractBrandFromPackage(packageName: String): String? {
        if (packageName.isBlank()) return null
        val segments = packageName.lowercase()
            .split('.', '_', '-')
            .filter { it.length >= 3 && !IGNORED_PACKAGE_SEGMENTS.contains(it) }

        if (segments.isEmpty()) return null
        // Pick the most distinctive segment (usually the last or second last)
        val candidate = segments.lastOrNull { !it.contains("app") } ?: segments.last()
        return formatTitleCase(candidate)
    }

    /**
     * Resolves ANY merchant string and optional package ID purely algorithmically.
     * No hardcoded apps or dictionaries.
     */
    fun resolve(rawMerchant: String?, packageName: String?): Resolution {
        // 1. If raw merchant text is present, clean it through g(S_raw)
        if (!rawMerchant.isNullOrBlank()) {
            val cleaned = cleanRawMerchant(rawMerchant)
            if (cleaned.isNotBlank()) {
                val formatted = formatTitleCase(cleaned)
                return Resolution(formatted, 1.0, MatchSource.CONTENT_CLEANED)
            }
        }

        // 2. If merchant text was absent, extract brand dynamically from package name
        if (!packageName.isNullOrBlank()) {
            val brand = extractBrandFromPackage(packageName)
            if (brand != null) {
                return Resolution(brand, 0.90, MatchSource.PACKAGE_EXTRACTED)
            }
        }

        // 3. Fallback
        val fallback = if (!rawMerchant.isNullOrBlank()) formatTitleCase(rawMerchant.trim()) else "Unknown Merchant"
        return Resolution(fallback, 0.0, MatchSource.FALLBACK)
    }

    fun formatTitleCase(input: String): String {
        return input.split(" ")
            .filter { it.isNotBlank() }
            .joinToString(" ") { word ->
                if (word.all { it.isLetter() && it.isUpperCase() }) {
                    word.lowercase().replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                } else {
                    word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                }
            }
    }
}
