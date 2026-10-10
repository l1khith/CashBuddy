// NO-NETWORK
package com.cashbuddy.core.prob

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class MerchantFuzzyTest {

    @Test
    fun testSwiggyLocationCompoundMerchant() {
        val match = MerchantMap.lookup("SWIGGY_BLR_KORAMANGALA")
        assertEquals("Food & Dining", match.category)
        assertEquals(MerchantMap.CategorySource.MERCHANT_MAP, match.source)
    }

    @Test
    fun testPaytmAggregatorPrefixedMerchant() {
        val match = MerchantMap.lookup("PAYTM*SWIGGY")
        assertEquals("Food & Dining", match.category)
        assertEquals(MerchantMap.CategorySource.MERCHANT_MAP, match.source)
    }

    @Test
    fun testVpaPrefixedMerchant() {
        val match = MerchantMap.lookup("swiggy@ybl")
        assertEquals("Food & Dining", match.category)
        assertEquals(MerchantMap.CategorySource.MERCHANT_MAP, match.source)
    }

    @Test
    fun testZomatoExactTierNotFuzzy() {
        val match = MerchantMap.lookup("ZOMATO")
        assertEquals("Food & Dining", match.category)
        assertEquals(0.95f, match.confidence)
        assertEquals(MerchantMap.CategorySource.MERCHANT_MAP, match.source)
    }

    @Test
    fun testZomatoResolvesDifferentlyFromSwiggy() {
        val zomatoMatch = MerchantMap.lookup("ZOMATO")
        val swiggyMatch = MerchantMap.lookup("SWIGGY")

        assertEquals("Food & Dining", zomatoMatch.category)
        assertEquals("Food & Dining", swiggyMatch.category)

        // Ensure ZOMATO does not fuzzy match Swiggy
        val similarity = JaroWinkler.similarity("zomato", "swiggy")
        assertTrue(similarity < 0.5, "ZOMATO and SWIGGY should not be similar: $similarity")
    }

    @Test
    fun testRandomNonsenseFallsBackToUnknown() {
        val match = MerchantMap.lookup("asdfghjkl")
        assertEquals("Unknown", match.category)
        assertEquals(0.0f, match.confidence)
        assertEquals(MerchantMap.CategorySource.FALLBACK, match.source)
    }
}
