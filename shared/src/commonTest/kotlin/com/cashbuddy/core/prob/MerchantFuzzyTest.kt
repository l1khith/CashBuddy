// NO-NETWORK
package com.cashbuddy.core.prob

import com.cashbuddy.core.CategoryEngine
import com.cashbuddy.core.MerchantRuleEntry
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MerchantFuzzyTest {

    private lateinit var categoryEngine: CategoryEngine

    @BeforeTest
    fun setup() {
        categoryEngine = CategoryEngine()
        categoryEngine.loadUserRules(
            listOf(
                MerchantRuleEntry("swiggy", "Food & Dining"),
                MerchantRuleEntry("zomato", "Food & Dining"),
                MerchantRuleEntry("shanti sagar", "Food & Dining")
            )
        )
    }

    @Test
    fun testSwiggyLocationCompoundMerchant() {
        val match = categoryEngine.getCategory("SWIGGY_BLR_KORAMANGALA")
        assertEquals("Food & Dining", match.category)
        assertEquals("UserRule", match.source)
        assertEquals(1.0f, match.confidence)
    }

    @Test
    fun testPaytmAggregatorPrefixedMerchant() {
        val match = categoryEngine.getCategory("PAYTM*SWIGGY")
        assertEquals("Food & Dining", match.category)
        assertEquals("UserRule", match.source)
        assertEquals(1.0f, match.confidence)
    }

    @Test
    fun testVpaPrefixedMerchant() {
        val match = categoryEngine.getCategory("swiggy@ybl")
        assertEquals("Food & Dining", match.category)
        assertEquals(1.0f, match.confidence)
    }

    @Test
    fun testZomatoExactTier() {
        val match = categoryEngine.getCategory("ZOMATO")
        assertEquals("Food & Dining", match.category)
        assertEquals(1.0f, match.confidence)
        assertEquals("UserRule", match.source)
    }

    @Test
    fun testLearnedMerchantFuzzyMatch() {
        // "shanti sagar hotel" should fuzzy/token match learned rule "shanti sagar"
        val match = categoryEngine.getCategory("SHANTI SAGAR HOTEL")
        assertEquals("Food & Dining", match.category)
        assertEquals(1.0f, match.confidence)
    }

    @Test
    fun testRandomNonsenseFallsBackToUnknown() {
        val match = categoryEngine.getCategory("asdfghjkl")
        assertEquals("Unknown", match.category)
        assertEquals(0.0f, match.confidence)
        assertEquals("Fallback", match.source)
    }
}
