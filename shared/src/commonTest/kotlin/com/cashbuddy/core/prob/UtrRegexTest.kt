// NO-NETWORK
package com.cashbuddy.core.prob

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Regression tests for the UTR word-boundary fix (audit finding 1.3).
 *
 * The old pattern matched keywords mid-word (`ref` inside "reference"), producing false
 * UTRs that fed the 24h dedup window and Tier-1 UTR merging. The fixed pattern requires
 * a word boundary before the keyword AND a separator (space / : / #) before the ID.
 */
class UtrRegexTest {

    private fun utrIn(text: String): String? =
        Regexes.UTR.find(text)?.groupValues?.getOrNull(1)?.trim()

    // --- Real bank formats must still be extracted ---

    @Test
    fun extractsUtrWithColon() {
        assertEquals("427182938472", utrIn("Paid ₹130. UTR: 427182938472"))
    }

    @Test
    fun extractsUtrWithSpaceSeparator() {
        assertEquals("427182938472", utrIn("UPI Ref no 427182938472"))
    }

    @Test
    fun extractsUtrWithHashSeparator() {
        assertEquals("427182938472", utrIn("Ref#427182938472"))
    }

    @Test
    fun extractsTransactionId() {
        assertEquals("427182938472", utrIn("Transaction ID: 427182938472"))
    }

    @Test
    fun extractsUpiPrefixedRef() {
        assertEquals("427182938472", utrIn("UPI transaction id: 427182938472"))
    }

    @Test
    fun extractsRrnAndTxnKeywords() {
        assertEquals("427182938472", utrIn("RRN: 427182938472"))
        assertEquals("427182938472", utrIn("txn 427182938472"))
    }

    // --- Mid-word keyword matches must no longer produce false UTRs ---

    @Test
    fun rejectsMidWordRefInsideWord() {
        assertNull(utrIn("myreference12345678"), "'ref' inside 'myreference' must not match")
    }

    @Test
    fun rejectsMidWordUtrInsideWord() {
        assertNull(utrIn("nutrient42718293"), "'utr' inside 'nutrient' must not match")
    }

    @Test
    fun rejectsRefundPrefixWithoutSeparator() {
        assertNull(utrIn("refunded 427182938472"))
    }

    @Test
    fun rejectsKeywordWithoutSeparatorBeforeId() {
        // Separator (space/:/#) is now mandatory before the captured ID.
        assertNull(utrIn("UTR427182938472"))
    }

    @Test
    fun rejectsNarrationTextWithoutKeyword() {
        assertNull(utrIn("Paid to merchant 427182938472 thanks"))
    }

    // --- Tokenizer interplay: UTR blanking must not eat valid words anymore ---

    @Test
    fun tokenizerStillKeepsWordsAroundUtrShapedText() {
        // Old behavior: UTR.replace() blanked "reference12345678" mid-word, leaving
        // only "my" (dropped, < 3 chars). New behavior: the word survives.
        val tokens = Tokenizer.tokenize("paid myreference12345678 today")
        assertEquals(true, tokens.contains("myreference"))
        assertEquals(true, tokens.contains("paid"))
    }
}
