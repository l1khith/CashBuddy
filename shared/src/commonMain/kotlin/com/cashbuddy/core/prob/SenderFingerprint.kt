// NO-NETWORK
package com.cashbuddy.core.prob

/**
 * Tokenized substring check against generic financial vocabulary.
 * Used ONLY as a weak hint feeding the probabilistic classifier's senderLooksBank evidence field.
 * NEVER gates acceptance and NEVER contains specific bank sender IDs.
 */
object SenderFingerprint {
    // Standard TRAI SMS sender header pattern: e.g. VK-HDFCBK, AD-SBIINB, BZ-ICICIB
    private val TRAI_SMS_HEADER_REGEX = Regex("""^[a-z]{2}-[a-z0-9]{3,8}$""")

    fun isBankLike(sender: String?): Boolean {
        if (sender.isNullOrBlank()) return false
        val clean = sender.lowercase().trim()
        val core = if (clean.contains('-')) clean.substringAfter('-') else clean

        val isTraiStructured = TRAI_SMS_HEADER_REGEX.matches(clean)
        val hasBankIndicator = core.endsWith("bk") || core.endsWith("inb") || core.endsWith("cb") ||
                core.endsWith("crd") || core.endsWith("bank") || core.contains("bank")

        return isTraiStructured && hasBankIndicator
    }
}
