// NO-NETWORK
package com.cashbuddy.core.prob

/**
 * Maps OCR-extracted app names to UI labels.
 * This is a presentation concern, NOT a classifier or trust gate.
 * Do not use this map for acceptance decisions.
 */
object AppLabelMap {
    private val LABELS: Map<String, String> = mapOf(
        "google pay" to "Google Pay",
        "gpay" to "Google Pay",
        "phonepe" to "PhonePe",
        "paytm" to "Paytm",
        "bhim" to "BHIM",
        "cred" to "Cred",
        "amazon pay" to "Amazon Pay"
    )

    fun label(text: String): String? {
        val lower = text.lowercase()
        return LABELS.entries.firstOrNull { lower.contains(it.key) }?.value
    }
}
