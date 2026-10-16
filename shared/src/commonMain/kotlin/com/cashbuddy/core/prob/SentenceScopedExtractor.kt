// NO-NETWORK
package com.cashbuddy.core.prob

import kotlin.math.roundToLong

/**
 * Stage 1: Sentence Isolation & Filter Operator V(s_i)
 *
 * Mathematical formulation:
 * V(s_i) = 𝟙( P_amount(s_i) ≠ ∅ ∧ P_verb(s_i) ≠ ∅ ∧ ∑_{t ∈ N} 𝟙(t ∈ s_i) = 0 )
 *
 * Discards non-transactional sentences, promotional alerts, job/salary ads,
 * OTPs, and status cascades while extracting valid transactional amounts and clauses.
 */
object SentenceScopedExtractor {

    private const val CURR_PREFIX = """(?:₹|Rs[.:]?|INR|[$€£¥]|USD|EUR|GBP|CAD|AUD|AED|SGD|JPY|C\$|A\$|S\$)"""
    private const val CURR_SUFFIX = """(?:₹|Rs[.:]?|INR|[$€£¥]|USD|EUR|GBP|CAD|AUD|AED|SGD|JPY|paise|paisa)"""

    // P_amount(s_i): Pattern matcher for continuous currency sequences
    val AMOUNT_PREFIX_REGEX = Regex("""(?i)$CURR_PREFIX\s*(\d+(?:,\d{2,3})*(?:\.\d{1,2})?|\d+(?:\.\d{1,2})?)""")
    val AMOUNT_SUFFIX_REGEX = Regex("""(?i)(\d+(?:,\d{2,3})*(?:\.\d{1,2})?|\d+(?:\.\d{1,2})?)\s*$CURR_SUFFIX""")
    val PAISE_ONLY_REGEX = Regex("""(?i)\b(\d+)\s*(?:paise|paisa)\b""")

    // P_verb(s_i): Transactional state verbs and noun-phrase completions
    val TRANSACTION_STATE_VERBS = Regex(
        """(?i)\b(debited|withdrawn|spent|paid(?!\s+(?:to\s+)?you\b)|payment(?:\s+of|\s+for|\s+successful|\s+done|\s+completed)?|sent(?!\s+you\b)|transferred(?!\s+to\s+you\b)|deducted|charged|purchase\s+at|credited|received|deposited|added|refunded|cashback|fare(?:\s+of)?|bill(?:\s+of)?)\b"""
    )

    val DEBIT_STATE_VERBS = Regex(
        """(?i)\b(debited|withdrawn|spent|paid(?!\s+(?:to\s+)?you\b)|payment(?:\s+of|\s+for|\s+successful|\s+done|\s+completed)?|sent(?!\s+you\b)|transferred(?!\s+to\s+you\b)|deducted|charged|purchase\s+at|order\s+paid|fare(?:\s+of)?|bill(?:\s+of)?)\b"""
    )

    val CREDIT_STATE_VERBS = Regex(
        """(?i)\b(credited|received|deposited|added|refunded|cashback|paid\s+(?:to\s+)?you|sent\s+you|transferred\s+to\s+you)\b"""
    )

    // N: Negative forbidden tokens (Promotions, salary, OTP, discounts, reminders)
    private val FORBIDDEN_TOKENS = listOf(
        Regex("""(?i)\b(?:lpa|ctc|stipend)\b"""),
        Regex("""(?i)\b(?:per\s+(?:annum|month|year)|a\s+year|annually)\b"""),
        Regex("""(?i)\b(?:salary\s+(?:up\s*to\s+)?$CURR_PREFIX)"""),
        Regex("""(?i)$CURR_PREFIX\s*\d[\d,.]*[kKlL]?\s*(?:off|discount|voucher)\b"""),
        Regex("""(?i)\b(?:up\s*to|upto)\s*$CURR_PREFIX\s*\d"""),
        Regex("""(?i)$CURR_PREFIX\s*\d[\d,.]*[kKlL]?\s*%"""),
        Regex("""(?i)\b(?:otp|one time password|verification code|secret code|do not share)\b"""),
        Regex("""(?i)\b(?:statement\s+for|statement\s+ready|your\s+monthly\s+statement)\b"""),
        Regex("""(?i)\b(?:bill\s+is\s+due|emi\s+due|upcoming\s+bill)\b"""),
        Regex("""(?i)\b(?:pre-approved|win\s+cash|apply\s+now|apply\s+for)\b""")
    )

    private val JOB_PAID_MARKER = Regex("""(?i)\b(?:intern(?:ship)?\s*\(\s*paid\s*\)|\(\s*paid\s*(?:intern(?:ship)?)?\s*\)|paid\s+intern(?:ship)?|(?:engineer|developer|role|position)\s*\(\s*paid\s*\))\b""")

    /**
     * Splits raw text into independent clauses/sentences,
     * protecting numbers with decimals (e.g., 140.0, 100.00), currency abbreviations (Rs.),
     * and bank terms (a/c, no., ref.).
     */
    fun splitSentences(text: String): List<String> {
        if (text.isBlank()) return emptyList()

        // 1. Protect decimal numbers between digits: e.g. "140.0" -> "140\u00000"
        var s = Regex("""(\d)\.(\d)""").replace(text, "$1\u0000$2")
        // 2. Protect currency abbreviations: "Rs.", "Re.", "INR."
        s = Regex("""(?i)\b(rs|re|inr)\.""").replace(s, "$1\u0001")
        // 3. Protect common banking abbreviations
        s = Regex("""(?i)\b(a/c|ac|no|ref|vpa|co|ltd|dr|mr|mrs)\.""").replace(s, "$1\u0001")

        // 4. Split on sentence delimiters: period, !, ?, newline, bullets, pipe, em-dash
        return s.split(Regex("""[.!?\n\r•·|—]+"""))
            .map { it.replace('\u0000', '.').replace('\u0001', '.').trim() }
            .filter { it.isNotBlank() }
    }

    /**
     * Computes the sentence validity indicator V(s_i) ∈ {0, 1}
     */
    fun isValidTransactionSentence(sentence: String): Boolean {
        if (sentence.isBlank()) return false
        val sanitized = JOB_PAID_MARKER.replace(sentence, "")

        // 1. P_amount(s_i) ≠ ∅
        val hasAmount = AMOUNT_PREFIX_REGEX.containsMatchIn(sanitized) ||
                AMOUNT_SUFFIX_REGEX.containsMatchIn(sanitized) ||
                PAISE_ONLY_REGEX.containsMatchIn(sanitized)
        if (!hasAmount) return false

        // 2. P_verb(s_i) ≠ ∅
        val hasVerb = TRANSACTION_STATE_VERBS.containsMatchIn(sanitized)
        if (!hasVerb) return false

        // 3. ∑_{t ∈ N} 𝟙(t ∈ s_i) = 0
        val hasForbidden = FORBIDDEN_TOKENS.any { it.containsMatchIn(sanitized) }
        if (hasForbidden) return false

        return true
    }

    /**
     * Isolates all valid transaction sentences from raw alert text.
     */
    fun extractValidSentences(fullText: String): List<String> {
        return splitSentences(fullText).filter { isValidTransactionSentence(it) }
    }

    /**
     * Extracts normalized amount from valid sentences or returns null.
     */
    fun extractNormalizedAmount(sentence: String): Double? {
        // Normalize common OCR misreads in amount: 'O'/'o' -> '0', 'l'/'I' -> '1'
        var normalized = sentence
        // Check paise only (e.g. "50 paise")
        val paiseMatch = PAISE_ONLY_REGEX.find(normalized)
        if (paiseMatch != null) {
            val pVal = paiseMatch.groups[1]?.value?.toDoubleOrNull()
            if (pVal != null) return pVal / 100.0
        }

        // Amount with prefix
        val m1 = AMOUNT_PREFIX_REGEX.find(normalized)
        if (m1 != null) {
            val numStr = cleanNumberString(m1.groups[1]?.value)
            numStr?.toDoubleOrNull()?.let { return it }
        }

        // Amount with suffix
        val m2 = AMOUNT_SUFFIX_REGEX.find(normalized)
        if (m2 != null) {
            val numStr = cleanNumberString(m2.groups[1]?.value)
            numStr?.toDoubleOrNull()?.let { return it }
        }

        return null
    }

    private fun cleanNumberString(raw: String?): String? {
        if (raw == null) return null
        var s = raw.replace(",", "").trim()
        // If trailing dot: "50." -> "50"
        if (s.endsWith(".")) s = s.dropLast(1)
        // Clean OCR noise: 'O' -> '0', 'o' -> '0', 'l' -> '1'
        s = s.replace('O', '0').replace('o', '0').replace('l', '1').replace('I', '1')
        return s
    }
}
