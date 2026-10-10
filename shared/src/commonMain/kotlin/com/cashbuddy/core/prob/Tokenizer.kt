// NO-NETWORK
package com.cashbuddy.core.prob

/**
 * Pure structural tokenizer that converts raw notification or OCR text into
 * a deduplicated set of candidate tokens for on-device Naive Bayes learning.
 *
 * Contains zero domain vocabulary, merchant names, or verb lists. Only strips
 * structural elements (currency symbols, numbers, punctuation, account/UTR masks)
 * and closed-set English function words ([GRAMMAR]).
 */
object Tokenizer {

    /**
     * Closed-set English grammar words (articles, prepositions, pronouns,
     * conjunctions, and auxiliary verbs).
     */
    internal val GRAMMAR = setOf(
        "the", "a", "an", "and", "or", "but", "if", "then",
        "to", "from", "at", "for", "by", "on", "in", "with",
        "via", "towards", "into", "of", "as", "is", "are",
        "was", "were", "be", "been", "being", "has", "have",
        "had", "do", "does", "did", "will", "would", "can",
        "could", "should", "may", "might", "must", "not",
        "no", "yes", "you", "me", "us", "we", "they", "their",
        "this", "that", "these", "those", "your", "my", "our"
    )

    private val CURRENCY_MARKER = Regex("""(?:₹|\brs\.?\b|\brs\.|\binr\b|[$€£¥])""")
    private val MASKED_DIGITS = Regex("""\b[x*]{2,}\d*\b""")
    private val DIGITS = Regex("""\d+""")
    private val NON_ALPHA = Regex("""[^a-z\s]""")
    private val WHITESPACE = Regex("""\s+""")
    private val HEX_ONLY = Regex("""^[0-9a-f]{6,}$""")

    /**
     * Extracts candidate learning tokens from [rawText].
     */
    fun tokenize(rawText: String): Set<String> {
        if (rawText.isBlank()) return emptySet()

        // 1. Lowercase the input
        var text = rawText.lowercase()

        // 9. Remove known structural tokens (account numbers, reference IDs, masked digits) before digit stripping
        text = Regexes.ACCOUNT.replace(text, " ")
        text = Regexes.UTR.replace(text, " ")
        text = MASKED_DIGITS.replace(text, " ")

        // 2. Replace currency symbols and abbreviations with a single space
        text = CURRENCY_MARKER.replace(text, " ")

        // 3. Remove all digits and digit runs
        text = DIGITS.replace(text, " ")

        // 4. Remove punctuation except space
        text = NON_ALPHA.replace(text, " ")

        // 5–10. Split on whitespace, filter length/hex/grammar, and return deduplicated set
        return text.split(WHITESPACE)
            .asSequence()
            .map { it.trim() }
            .filter { it.length >= 3 }
            .filterNot { it.all { ch -> ch == 'x' } }
            .filterNot { HEX_ONLY.matches(it) }
            .filterNot { GRAMMAR.contains(it) }
            .toSet()
    }
}
