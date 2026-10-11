package com.cashbuddy.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CoreEnginesTest {

    // ==========================================
    // CategoryEngine Tests
    // ==========================================

    @Test
    fun testCategoryEnginePriority() {
        val engine = CategoryEngine()

        // Initially Zomato matches KeywordMap -> Food & Dining
        val res = engine.getCategory("Zomato India")
        assertEquals("Food & Dining", res.category)
        assertEquals(0.90f, res.confidence)
        assertEquals("KeywordMap", res.source)

        // User overrides Zomato to Entertainment & Recreation
        engine.learnCorrection("Zomato", "Entertainment & Recreation")
        val res2 = engine.getCategory("Zomato India")
        assertEquals("Entertainment & Recreation", res2.category)
        assertEquals(1.0f, res2.confidence)
        assertEquals("UserRule", res2.source)
    }

    @Test
    fun testCategoryEngineUnknownFallback() {
        val engine = CategoryEngine()
        val res = engine.getCategory("XYZ Random Merchant 999")
        assertEquals("Unknown", res.category)
        assertEquals(0.50f, res.confidence)
        assertEquals("Fallback", res.source)
    }

    @Test
    fun testCategoryEngineBatchLoadRules() {
        val engine = CategoryEngine()
        val entries = listOf(
            MerchantRuleEntry(merchant = "Chaiwala Uncle", category = "Food & Dining"),
            MerchantRuleEntry(merchant = "Landlord Ramesh", category = "Housing & Rent")
        )
        engine.loadUserRules(entries)

        val res = engine.getCategory("Payment to Chaiwala Uncle")
        assertEquals("Food & Dining", res.category)
        assertEquals(1.0f, res.confidence)
        assertEquals("UserRule", res.source)

        val resRent = engine.getCategory("Landlord Ramesh")
        assertEquals("Housing & Rent", resRent.category)
        assertEquals(1.0f, resRent.confidence)
        assertEquals("UserRule", resRent.source)
    }

    // ==========================================
    // ScreenshotParserEngine Tests
    // ==========================================

    @Test
    fun testScreenshotGooglePay() {
        val engine = ScreenshotParserEngine()
        val text = """
            Google Pay
            ₹450
            Paid to Chai Point
            chai@icici
            Completed
            May 14, 2024 12:45 PM
            UPI transaction ID: 413418291039
            To: Chai Point
            From: State Bank of India
        """.trimIndent()

        val tx = engine.parse(text)
        assertNotNull(tx)
        assertEquals(450.0, tx.amount)
        assertEquals(TransactionType.DEBIT, tx.transactionType)
        assertEquals("Chai Point", tx.merchant)
        assertEquals("Food & Dining", tx.category)
        assertEquals("Google Pay", tx.appName)
        assertEquals("413418291039", tx.utrOrRef)
        assertTrue(tx.confidence >= 0.85f)
    }

    @Test
    fun testScreenshotBmtcBusTicket() {
        val engine = ScreenshotParserEngine()
        // Exact OCR text from user's live device logcat!
        val text = """
            S
            626778121226
            To: BMTC
            Payment for 26124n2812426Ac1
            UPI transaction ID: 626778121226
            B
            To BMTC
            18
            Union Bank of India 0383
            n Payment of 18 completed
            24 Sept 2026, 6:58 pm
            Receiver's bank has confirmed deposit of m
        """.trimIndent()

        val tx = engine.parse(text)
        assertNotNull(tx)
        assertEquals(18.0, tx.amount)
        assertEquals(TransactionType.DEBIT, tx.transactionType)
        assertEquals("BMTC", tx.merchant)
        assertEquals("Transportation", tx.category)
        assertTrue(tx.confidence >= 0.85f)
    }

    @Test
    fun testNegativeCurrencyFormatting() {
        val positive = com.cashbuddy.domain.model.CurrencyRegistry.format(5000.0)
        assertEquals("₹5,000", positive)

        val negative = com.cashbuddy.domain.model.CurrencyRegistry.format(-2000.0)
        assertEquals("-₹2,000", negative)

        val negativeNoSymbol = com.cashbuddy.domain.model.CurrencyRegistry.format(-2000.0, includeSymbol = false)
        assertEquals("-2,000", negativeNoSymbol)
    }

    @Test
    fun testReviewReducerUpdateTransaction() {
        val reducer = com.cashbuddy.presentation.review.ReviewReducer()
        val initialTx = com.cashbuddy.domain.model.Transaction(
            id = 101L,
            amount = 5000.0,
            type = com.cashbuddy.domain.model.TransactionType.DEBIT,
            merchant = "Unknown",
            categoryId = 1L,
            sourceApp = "com.google.android.apps.nbu.paisa.user",
            rawText = "Paid 5000",
            confidence = 0.9f,
            status = com.cashbuddy.domain.model.TransactionStatus.PENDING,
            timestamp = 1000L
        )
        val state = com.cashbuddy.presentation.review.ReviewState(
            transactions = listOf(initialTx),
            isLoading = false
        )

        // User edits transaction to CREDIT, 5000.0, Merchant "Likhith"
        val updatedTx = initialTx.copy(
            type = com.cashbuddy.domain.model.TransactionType.CREDIT,
            merchant = "Likhith",
            categoryId = 2L
        )
        val nextState = reducer.reduce(state, com.cashbuddy.presentation.review.ReviewResult.TransactionUpdated(updatedTx))

        assertEquals(1, nextState.transactions.size)
        assertEquals(com.cashbuddy.domain.model.TransactionType.CREDIT, nextState.transactions[0].type)
        assertEquals("Likhith", nextState.transactions[0].merchant)
        assertEquals(2L, nextState.transactions[0].categoryId)
    }
}
