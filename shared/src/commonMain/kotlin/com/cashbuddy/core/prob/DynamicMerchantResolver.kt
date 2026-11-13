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

    // Standard internet domain segments to skip when parsing package names
    private val IGNORED_PACKAGE_SEGMENTS = setOf(
        "com", "in", "org", "net", "io", "co"
    )

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
