package com.cashbuddy.clipboard

import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import com.cashbuddy.core.prob.Regexes

object ClipboardMonitor {

    private val standalone12DigitRegex = Regex("""^\d{12}$""")
    private var lastProcessedUtr: String? = null

    /**
     * Inspects primary clip in ClipboardManager.
     * Returns extracted UTR if present and not previously processed; otherwise null.
     */
    fun checkClipboard(context: Context): String? {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            ?: return null

        if (!clipboard.hasPrimaryClip()) return null

        val description = clipboard.primaryClipDescription
        if (description != null &&
            !description.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN) &&
            !description.hasMimeType(ClipDescription.MIMETYPE_TEXT_HTML)
        ) {
            return null
        }

        val clip = clipboard.primaryClip ?: return null
        if (clip.itemCount == 0) return null

        val text = clip.getItemAt(0)?.text?.toString() ?: return null
        val utr = extractUtrFromText(text) ?: return null

        // Prevent repeat triggers for the same clipboard content
        if (utr.equals(lastProcessedUtr, ignoreCase = true)) {
            return null
        }

        lastProcessedUtr = utr
        return utr
    }

    /**
     * Pure extraction of UTR or 12-digit NPCI RRN from raw text.
     */
    fun extractUtrFromText(text: String): String? {
        val trimmed = text.trim()
        if (trimmed.isBlank() || trimmed.length > 500) return null

        val utr = when {
            // Standalone 12-digit NPCI RRN (copied by Google Pay / PhonePe / standard bank apps)
            standalone12DigitRegex.matches(trimmed) -> trimmed
            else -> {
                // UTR prefixed with standard indicators
                val match = Regexes.UTR.find(trimmed)
                match?.groupValues?.getOrNull(1)?.trim()
            }
        }

        if (utr.isNullOrBlank() || utr.length < 8) return null
        return utr
    }

    /**
     * Resets or updates the last processed UTR (e.g. for testing).
     */
    fun setLastProcessedUtr(utr: String?) {
        lastProcessedUtr = utr
    }

    fun getLastProcessedUtr(): String? = lastProcessedUtr
}
