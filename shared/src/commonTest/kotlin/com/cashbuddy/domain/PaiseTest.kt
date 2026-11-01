package com.cashbuddy.domain

import com.cashbuddy.domain.model.Paise
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PaiseTest {

    @Test
    fun testFromRupeesRoundsCorrectly() {
        assertEquals(1050L, Paise.fromRupees(10.50).value)
        assertEquals(1055L, Paise.fromRupees(10.55).value)
        assertEquals(100L, Paise.fromRupees(1.0).value)
        assertEquals(0L, Paise.ZERO.value)
    }

    @Test
    fun testArithmeticOperations() {
        val a = Paise.fromRupees(150.25)
        val b = Paise.fromRupees(50.75)

        assertEquals(Paise.fromRupees(201.00), a + b)
        assertEquals(Paise.fromRupees(99.50), a - b)
        assertEquals(Paise.fromRupees(300.50), a * 2L)
        assertEquals(Paise.fromRupees(75.12), a / 2L)
    }

    @Test
    fun testComparison() {
        val small = Paise.fromRupees(10.0)
        val large = Paise.fromRupees(20.0)

        assertTrue(small < large)
        assertEquals(small, Paise.fromRupees(10.0))
    }

    @Test
    fun testToRupeesAndString() {
        val p = Paise.fromRupees(1234.56)
        assertEquals(1234.56, p.toRupees())
        assertEquals("1234.56", p.toString())
    }
}
