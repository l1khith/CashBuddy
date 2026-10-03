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
    // DedupEngine Tests
    // ==========================================

    @Test
    fun testExactDuplicate() {
        val t1 = 1700000000000L
        val t2 = 1700000060000L // 60s later
        assertTrue(isDuplicateTransaction(450.0, "Swiggy", t1, 450.0, "Swiggy", t2, 300L))
    }

    @Test
    fun testMerchantVariationDuplicate() {
        val t1 = 1700000000000L
        val t2 = 1700000120000L // 2 min later
        assertTrue(isDuplicateTransaction(120.0, "Uber India", t1, 120.0, "Uber", t2, 300L))
    }

    @Test
    fun testDifferentAmountNotDuplicate() {
        val t1 = 1700000000000L
        val t2 = 1700000010000L
        assertFalse(isDuplicateTransaction(450.0, "Swiggy", t1, 550.0, "Swiggy", t2, 300L))
    }

    @Test
    fun testOutsideWindowNotDuplicate() {
        val t1 = 1700000000000L
        val t2 = 1700000400000L // 400s later (> 300s)
        assertFalse(isDuplicateTransaction(450.0, "Swiggy", t1, 450.0, "Swiggy", t2, 300L))
    }

    // ==========================================
    // NotificationParser Tests
    // ==========================================

    @Test
    fun testParseValidNotification() {
        val parser = NotificationParser()
        val raw = RawNotification(
            packageName = "com.google.android.apps.nbu.paisa.user",
            title = "Payment to Swiggy",
            text = "Paid ₹450 to Swiggy via GPay. A/C XX1234 debited",
            timestamp = 1700000000000L
        )

        val parsed = parser.parse(raw)
        assertNotNull(parsed)
        assertEquals(450.0, parsed.amount)
        assertEquals(TransactionType.DEBIT, parsed.transactionType)
        assertEquals("Swiggy", parsed.merchant)
        assertEquals(Category.Food, parsed.category)
        assertTrue(parsed.confidence >= 0.85f)
    }

    @Test
    fun testAmountInTitle() {
        val parser = NotificationParser()
        val raw = RawNotification(
            packageName = "com.google.android.apps.nbu.paisa.user",
            title = "Paid ₹1,200",
            text = "To Zomato via UPI. Ref 9876543210. A/C XX9999 debited",
            timestamp = 1700000000000L
        )

        val parsed = parser.parse(raw)
        assertNotNull(parsed)
        assertEquals(1200.0, parsed.amount)
        assertEquals(TransactionType.DEBIT, parsed.transactionType)
        assertEquals("Zomato", parsed.merchant)
        assertEquals(Category.Food, parsed.category)
        assertEquals("XX9999", parsed.accountId)
    }

    @Test
    fun testBankSmsNotification() {
        val parser = NotificationParser()
        val raw = RawNotification(
            packageName = "com.google.android.apps.messaging",
            title = "Bank Alert",
            text = "INR 850.00 debited from a/c **4321 on 24-Sep-26 to UBER INDIA. Avl bal: INR 15,200.00",
            timestamp = 1700000000000L
        )

        val parsed = parser.parse(raw)
        assertNotNull(parsed)
        assertEquals(850.0, parsed.amount)
        assertEquals(TransactionType.DEBIT, parsed.transactionType)
        assertEquals(Category.Transport, parsed.category)
        assertEquals("XX4321", parsed.accountId)
    }

    @Test
    fun testPersonalSmsDiscarded() {
        val parser = NotificationParser()
        val raw = RawNotification(
            packageName = "com.google.android.apps.messaging",
            title = "John Doe",
            text = "Hey are we still meeting for lunch today at 1 PM?",
            timestamp = 1700000000000L
        )

        val parsed = parser.parse(raw)
        assertNull(parsed)
    }

    @Test
    fun testDiscardOtp() {
        val parser = NotificationParser()
        val raw = RawNotification(
            packageName = "com.bank.app",
            title = "Security Alert",
            text = "Your OTP for NetBanking is 492019. Do not share with anyone.",
            timestamp = 1700000000000L
        )
        assertNull(parser.parse(raw))
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
