package com.cashbuddy.domain.parser

import com.cashbuddy.domain.model.TransactionType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KotlinNotificationParserTest {

    private val parser = KotlinNotificationParser()

    @Test
    fun testGooglePayUpiDebit() {
        val raw = RawNotificationData(
            packageName = "com.google.android.apps.nbu.paisa.user",
            title = "Paid to Swiggy",
            text = "₹450.00 debited from A/c ending 1234 to swiggy@icici on 15-Aug",
            timestamp = 1723700000000L
        )
        val result = parser.parse(raw)
        assertNotNull(result)
        assertEquals(450.0, result.amount)
        assertEquals(TransactionType.DEBIT, result.type)
        assertEquals("Swiggy", result.merchant)
        assertEquals("Food", result.categoryName)
        assertEquals("XX1234", result.accountId)
        assertTrue(result.confidence >= 0.85f)
    }

    @Test
    fun testPhonePeUpiDebit() {
        val raw = RawNotificationData(
            packageName = "com.phonepe.app",
            title = "Payment Successful",
            text = "Paid ₹1,250 to Uber India via PhonePe UPI. Ref: 987654321",
            timestamp = 1723700000000L
        )
        val result = parser.parse(raw)
        assertNotNull(result)
        assertEquals(1250.0, result.amount)
        assertEquals(TransactionType.DEBIT, result.type)
        assertEquals("Uber India", result.merchant)
        assertEquals("Transport", result.categoryName)
    }

    @Test
    fun testHdfcBankDebitAlert() {
        val raw = RawNotificationData(
            packageName = "com.snapwork.hdfc",
            title = "HDFC Bank Alert",
            text = "INR 3,499.00 debited from A/c **5678 on 20-Aug towards Amazon Retail. Avl bal: INR 45,000.00",
            timestamp = 1723700000000L
        )
        val result = parser.parse(raw)
        assertNotNull(result)
        assertEquals(3499.0, result.amount)
        assertEquals(TransactionType.DEBIT, result.type)
        assertEquals("Amazon Retail", result.merchant)
        assertEquals("Shopping", result.categoryName)
        assertEquals("XX5678", result.accountId)
    }

    @Test
    fun testSalaryCreditAlert() {
        val raw = RawNotificationData(
            packageName = "com.csam.icici.bank.imobile",
            title = "Salary Credited",
            text = "Your A/c ending with 9876 has been credited with INR 85,000.00 towards monthly salary payroll.",
            timestamp = 1723700000000L
        )
        val result = parser.parse(raw)
        assertNotNull(result)
        assertEquals(85000.0, result.amount)
        assertEquals(TransactionType.CREDIT, result.type)
        assertEquals("Salary", result.categoryName)
        assertEquals("XX9876", result.accountId)
    }

    @Test
    fun testRejectionOfOtp() {
        val raw = RawNotificationData(
            packageName = "com.snapwork.hdfc",
            title = "One Time Password",
            text = "Your OTP for transaction of INR 500.00 at Swiggy is 482910. Do not share with anyone.",
            timestamp = 1723700000000L
        )
        val result = parser.parse(raw)
        assertNull(result, "OTP notifications must be rejected with null")
    }

    @Test
    fun testRejectionOfPromotionalOffer() {
        val raw = RawNotificationData(
            packageName = "com.phonepe.app",
            title = "Special Offer",
            text = "Congratulations! Get cashback up to ₹500 on your next electricity bill payment. Apply now!",
            timestamp = 1723700000000L
        )
        val result = parser.parse(raw)
        assertNull(result, "Promotional notifications must be rejected with null")
    }

    @Test
    fun testRejectionOfUntrustedPackage() {
        val raw = RawNotificationData(
            packageName = "com.untrusted.fakebank",
            title = "Fake Alert",
            text = "Debited INR 500 from your account",
            timestamp = 1723700000000L
        )
        val result = parser.parse(raw)
        assertNull(result, "Untrusted packages must be rejected with null")
    }
}
