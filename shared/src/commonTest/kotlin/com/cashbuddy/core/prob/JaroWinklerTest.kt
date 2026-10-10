// NO-NETWORK
package com.cashbuddy.core.prob

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JaroWinklerTest {

    @Test
    fun testWinklerReferenceOracles() {
        val marthaSim = JaroWinkler.similarity("MARTHA", "MARHTA")
        assertTrue(
            abs(marthaSim - 0.9611) <= 0.001,
            "Expected MARTHA vs MARHTA ≈ 0.9611, but got $marthaSim"
        )

        val dwayneSim = JaroWinkler.similarity("DWAYNE", "DUANE")
        assertTrue(
            abs(dwayneSim - 0.8400) <= 0.001,
            "Expected DWAYNE vs DUANE ≈ 0.8400, but got $dwayneSim"
        )

        val dixonSim = JaroWinkler.similarity("DIXON", "DICKSONX")
        assertTrue(
            abs(dixonSim - 0.8133) <= 0.001,
            "Expected DIXON vs DICKSONX ≈ 0.8133, but got $dixonSim"
        )
    }

    @Test
    fun testIdenticalStringsReturnOne() {
        assertEquals(1.0, JaroWinkler.similarity("SWIGGY", "SWIGGY"))
    }

    @Test
    fun testEmptyStringsReturnZero() {
        assertEquals(0.0, JaroWinkler.similarity("", "SWIGGY"))
        assertEquals(0.0, JaroWinkler.similarity("SWIGGY", ""))
        assertEquals(0.0, JaroWinkler.similarity("", ""))
    }

    @Test
    fun testMerchantVariantPrefixSimilarity() {
        val sim = JaroWinkler.similarity("SWIGGY", "SWIGGY BLR")
        assertTrue(sim > 0.88, "Expected SWIGGY vs SWIGGY BLR > 0.88, but got $sim")
    }
}
