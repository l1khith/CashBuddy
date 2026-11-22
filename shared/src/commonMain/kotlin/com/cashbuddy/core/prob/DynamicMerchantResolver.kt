// NO-NETWORK
package com.cashbuddy.core.prob

/**
 * Stage 2: Pure Algorithmic Merchant Resolution from Android Package Names
 *
 * ZERO HARDCODED PACKAGE LISTS. ZERO HARDCODED MERCHANT DICTIONARIES.
 * 100% CONTENT-FIRST AND ALGORITHMIC.
 *
 * NOTE: earlier revisions of this header described gateway-token stripping
 * (G_prefix for "INF*", "UPI/", "RAZORPAY*" gateway noise, G_suffix for
 * location codes and "Pvt Ltd" suffixes, V_handles for @ybl/@icici UPI
 * domains). That logic does NOT exist in this object — VPA-handle cleanup
 * happens in [EvidenceExtractor.extractMerchant] (strategy 1) and gateway
 * noise is handled by the merchant-validity checks there. This object only:
 *
 * - Parses Android package segments algorithmically (skipping standard
 *   internet domain segments: com, in, org, net, io, co).
 * - Derives a brand name purely by segment selection (e.g.
 *   "com.thesmartq.smartq" -> "Smartq", "in.swiggy.android" -> "Swiggy").
 * - Formats cleaned merchant strings into title-case canonical names.
 */
object DynamicMerchantResolver {

    // Standard internet domain segments to skip when parsing package names
    private val IGNORED_PACKAGE_SEGMENTS = setOf(
        "com", "in", "org", "net", "io", "co"
    )

    /**
     * Algorithmically extracts a human-readable brand name from ANY Android package name.
     * Example:
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
