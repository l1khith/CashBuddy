// NO-NETWORK
package com.cashbuddy.core

import com.cashbuddy.core.prob.DedupEngine
import com.cashbuddy.core.prob.EvidenceExtractor
import com.cashbuddy.core.prob.MerchantMap
import com.cashbuddy.core.prob.NoOpCalibrator
import com.cashbuddy.core.prob.NotificationSource
import com.cashbuddy.core.prob.PolicyEngine
import com.cashbuddy.core.prob.ProbabilisticClassifier
import com.cashbuddy.core.prob.RawMessage
import com.cashbuddy.core.prob.SourceDetector
import com.cashbuddy.core.prob.SourceType
import com.cashbuddy.domain.model.TransactionType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProbabilisticEngineTest {

    private val sourceDetector = SourceDetector()
    private val evidenceExtractor = EvidenceExtractor()
    private val classifier = ProbabilisticClassifier(calibrator = NoOpCalibrator)
    private val policy = PolicyEngine()

    @Test
    fun testScenario1_BankDebitSmsHighConfidence() {
        val text = "Rs.100 debited from A/c XX1234 to VPA smartq@axis"
        val raw = RawMessage(
            id = "test-1",
            sourceType = SourceType.SMS,
            packageName = null,
            senderId = "VK-BANK",
            title = "VK-BANK",
            text = text,
            timestamp = 1727000000000L
        )

        val source = sourceDetector.detect("VK-BANK", text)
        val evidence = evidenceExtractor.extract(raw, source)
        val result = classifier.classify(evidence, text, source)

        assertTrue(result.pTransaction > 0.95, "Expected p > 0.95, got ${result.pTransaction}")
        assertEquals("1234", result.accountLast4)
        assertEquals("smartq", result.merchant?.lowercase())
        assertEquals(TransactionType.DEBIT, result.type)
        assertEquals(PolicyEngine.Action.AUTO_LOG, policy.action(result.pTransaction))
    }

    @Test
    fun testScenario2_OtpMessageIgnored() {
        val text = "Your OTP is 123456"
        val raw = RawMessage(
            id = "test-2",
            sourceType = SourceType.SMS,
            packageName = null,
            senderId = "VK-BANK",
            title = "VK-BANK",
            text = text,
            timestamp = 1727000000000L
        )

        val source = sourceDetector.detect("VK-BANK", text)
        val evidence = evidenceExtractor.extract(raw, source)
        val result = classifier.classify(evidence, text, source)

        assertTrue(result.pTransaction < 0.05, "Expected p < 0.05, got ${result.pTransaction}")
        assertEquals(PolicyEngine.Action.IGNORE, policy.action(result.pTransaction))
    }

    @Test
    fun testScenario3_PromoMessageIgnored() {
        val text = "Win cash up to ₹5000. Apply now."
        val raw = RawMessage(
            id = "test-3",
            sourceType = SourceType.NOTIFICATION,
            packageName = "com.promo.app",
            senderId = null,
            title = "Offer Alert",
            text = text,
            timestamp = 1727000000000L
        )

        val source = sourceDetector.detect("com.promo.app", text)
        val evidence = evidenceExtractor.extract(raw, source)
        val result = classifier.classify(evidence, text, source)

        assertTrue(result.pTransaction < 0.20, "Expected p < 0.20, got ${result.pTransaction}")
        assertEquals(PolicyEngine.Action.IGNORE, policy.action(result.pTransaction))
    }

    @Test
    fun testScenario4_MerchantAppNotificationFlaggedForReview() {
        val text = "SmartQ · Payment Successful · Your payment of ₹100.0 was successful."
        val raw = RawMessage(
            id = "test-4",
            sourceType = SourceType.NOTIFICATION,
            packageName = "com.smartq.app",
            senderId = null,
            title = "SmartQ",
            text = text,
            timestamp = 1727000000000L
        )

        val source = sourceDetector.detect("com.smartq.app", text)
        val evidence = evidenceExtractor.extract(raw, source)
        val result = classifier.classify(evidence, text, source)

        assertTrue(
            result.pTransaction > 0.70 && result.pTransaction < 0.85,
            "Expected 0.70 < p < 0.85, got ${result.pTransaction}"
        )
        assertEquals(PolicyEngine.Action.LOG_AND_FLAG, policy.action(result.pTransaction))
    }

    @Test
    fun testScenario5_CreditMessageHighConfidence() {
        val text = "INR 1,200.00 credited to A/c 5678"
        val raw = RawMessage(
            id = "test-5",
            sourceType = SourceType.SMS,
            packageName = null,
            senderId = "VK-BANK",
            title = "VK-BANK",
            text = text,
            timestamp = 1727000000000L
        )

        val source = sourceDetector.detect("VK-BANK", text)
        val evidence = evidenceExtractor.extract(raw, source)
        val result = classifier.classify(evidence, text, source)

        assertTrue(result.pTransaction > 0.95, "Expected p > 0.95, got ${result.pTransaction}")
        assertEquals(TransactionType.CREDIT, result.type)
        assertEquals("5678", result.accountLast4)
    }

    @Test
    fun testScenario6_MerchantNotificationFollowedByBankSmsDedupMerge() {
        val t0 = 1727000000000L
        val t1 = t0 + 60_000L // 1 minute later

        // 1. Initial merchant notification
        val smartQInput = DedupEngine.DedupInput(
            amount = 100.0,
            merchant = "SmartQ",
            accountLast4 = null,
            pTransaction = 0.79,
            timestamp = t0
        )

        val existingList = listOf(
            DedupEngine.ExistingCandidate(
                id = 42L,
                amount = smartQInput.amount,
                merchant = smartQInput.merchant,
                accountLast4 = null,
                pTransaction = smartQInput.pTransaction,
                timestamp = smartQInput.timestamp
            )
        )

        // 2. Subsequent bank SMS with higher confidence and account number
        val bankSmsInput = DedupEngine.DedupInput(
            amount = 100.0,
            merchant = "SmartQ",
            accountLast4 = "1234",
            pTransaction = 0.98,
            timestamp = t1
        )

        val decision = DedupEngine.resolve(bankSmsInput, existingList)
        assertTrue(decision is DedupEngine.Decision.Merge, "Expected DedupEngine to Merge duplicate transaction")
        val merge = decision as DedupEngine.Decision.Merge
        assertEquals(42L, merge.existingId)
        assertEquals("1234", merge.upgradedFields.accountLast4)
        assertTrue(merge.upgradedFields.pTransaction > 0.95)
        assertTrue(merge.upgradedFields.clearReview)
    }

    @Test
    fun testEvidenceExtractor_MerchantPackageProducesFromMerchantPackageTrue() {
        val raw = RawMessage(
            id = "pkg-test-1",
            sourceType = SourceType.NOTIFICATION,
            packageName = "com.smartq",
            senderId = null,
            title = "Order Placed",
            text = "Your order has been placed for Rs 150",
            timestamp = 1727000000000L
        )
        val evidence = evidenceExtractor.extract(raw, NotificationSource.UNKNOWN)
        assertTrue(evidence.fromMerchantPackage, "Expected fromMerchantPackage to be true for com.smartq")
    }

    @Test
    fun testEvidenceExtractor_TextPrefixWithoutMerchantPackageProducesFromMerchantPackageFalse() {
        val raw = RawMessage(
            id = "pkg-test-2",
            sourceType = SourceType.NOTIFICATION,
            packageName = "com.android.systemui",
            senderId = null,
            title = "SmartQ · Now",
            text = "Your order is ready",
            timestamp = 1727000000000L
        )
        val evidence = evidenceExtractor.extract(raw, NotificationSource.UNKNOWN)
        assertFalse(evidence.fromMerchantPackage, "Expected fromMerchantPackage to be false when package is not a merchant")
    }

    @Test
    fun testScreenshotParserEngine_DetectsGooglePayAppName() {
        val ocrText = """
            Google Pay
            Payment of ₹450 to Chai Point
            UPI transaction ID: 123456789012
            Completed
        """.trimIndent()
        val engine = ScreenshotParserEngine()
        val result = engine.parse(ocrText)
        assertNotNull(result)
        assertEquals("Google Pay", result.appName)
    }

    @Test
    fun testScreenshotParserEngine_NoAppNameProducesNull() {
        val ocrText = """
            Payment of ₹450 to Chai Point
            UPI transaction ID: 123456789012
            Completed
        """.trimIndent()
        val engine = ScreenshotParserEngine()
        val result = engine.parse(ocrText)
        assertNotNull(result)
        assertNull(result.appName)
    }

    @Test
    fun testMerchantMap_LookupSwiggyReturnsFoodWithHighConfidence() {
        val match = MerchantMap.lookup("swiggy")
        assertEquals("Food & Dining", match.category)
        assertTrue(match.confidence >= 0.90f)
        assertEquals(MerchantMap.CategorySource.MERCHANT_MAP, match.source)
    }

    @Test
    fun testBankDebitSmsWithColonAmountAndFvgMerchant() {
        val text = "Bank A/c *0383 Debited Rs:60.00 on 28-09-2026 13:02:20 by Mob Bk ref no 663757559850, Fvg: BOTTLE L Avl Bal Rs:1675.08."
        val raw = RawMessage(
            id = "test-colon-amount",
            sourceType = SourceType.SMS,
            packageName = null,
            senderId = "JM-BANK-T",
            title = "JM-BANK-T",
            text = text,
            timestamp = 1727500000000L
        )

        val source = sourceDetector.detect("JM-BANK-T", text)
        val evidence = evidenceExtractor.extract(raw, source)
        val result = classifier.classify(evidence, text, source)

        assertTrue(evidence.hasAmount, "Expected hasAmount to be true for Rs:60.00")
        assertEquals(60.0, result.amount)
        assertEquals(TransactionType.DEBIT, result.type)
        assertEquals("0383", result.accountLast4)
        assertEquals("Bottle L", result.merchant)
        assertTrue(result.pTransaction > 0.95, "Expected p > 0.95, got ${result.pTransaction}")
    }

    @Test
    fun testSmartQPaymentNotification() {
        val title = "Payment Successful"
        val text = "Your payment of ₹140.0 was successful. Thank you for ordering!"
        val raw = RawMessage(
            id = "test-smartq",
            sourceType = SourceType.NOTIFICATION,
            packageName = "com.smartq",
            senderId = null,
            title = title,
            text = text,
            timestamp = 1727500000000L
        )

        val source = sourceDetector.detect("com.smartq", "$title: $text")
        val evidence = evidenceExtractor.extract(raw, source)
        val result = classifier.classify(evidence, "$title $text", source, raw.packageName)

        assertTrue(evidence.hasAmount)
        assertEquals(140.0, result.amount)
        assertEquals("Smartq", result.merchant)
        assertTrue(result.pTransaction >= 0.65, "Expected p >= 0.65, got ${result.pTransaction}")
    }

    @Test
    fun testScreenshotParserEngine_ParsesImage3MinimalUpiReceipt() {
        val ocrText = """
            ₹18
            Paid to
            BMTC
            Banking name: BMTC
            28 September 2026, 8:02 pm
            POWERED BY UPI
        """.trimIndent()

        val engine = ScreenshotParserEngine()
        val result = engine.parse(ocrText)

        assertNotNull(result, "Expected Image 3 receipt to be parsable")
        assertEquals(18.0, result.amount)
        assertEquals("BMTC", result.merchant)
        assertEquals("Transportation", result.category)
        assertEquals("UPI", result.appName)
        assertEquals(com.cashbuddy.core.TransactionType.DEBIT, result.transactionType)
        assertTrue(result.confidence >= 0.85f)
    }

    @Test
    fun testScreenshotParserEngine_ParsesImage3WhenRupeeSymbolDroppedByOcr() {
        // ML Kit Latin sometimes misses the ₹ glyph and outputs only digits '18'
        val ocrText = """
            18
            Paid to
            BMTC
            Banking name: BMTC
            28 September 2026, 8:02 pm
            POWERED BY UPI
        """.trimIndent()

        val engine = ScreenshotParserEngine()
        val result = engine.parse(ocrText)

        assertNotNull(result, "Expected Image 3 receipt with dropped ₹ to be parsable")
        assertEquals(18.0, result.amount)
        assertEquals("BMTC", result.merchant)
        assertEquals("Transportation", result.category)
    }

    @Test
    fun testFailure1_JobEmailPaidKeywordIgnored() {
        val text = "🔔 A I LIKHITH, check out jobs applied by your peers QA Intern ... Mean Stack Developer Intern (Paid) ... ₹3L - ₹7L a year"
        val raw = RawMessage(
            id = "job-email-1",
            sourceType = SourceType.NOTIFICATION,
            packageName = "com.google.android.gm",
            senderId = null,
            title = "Job Alert",
            text = text,
            timestamp = 1727600000000L
        )

        val source = sourceDetector.detect("com.google.android.gm", text)
        val evidence = evidenceExtractor.extract(raw, source)
        val result = classifier.classify(evidence, text, source)

        assertTrue(evidence.amountHasForbiddenShape, "Expected forbidden shape (range / a year) to be detected")
        assertFalse(evidence.amountAndDebitSameSentence, "Paid in job title must not match debit in same sentence as amount")
        assertTrue(result.pTransaction < 0.20, "Expected p < 0.20 for job email, got ${result.pTransaction}")
        assertEquals(PolicyEngine.Action.IGNORE, policy.action(result.pTransaction))
    }

    @Test
    fun testFailure2_NmatSalaryLpaEmailIgnored() {
        val text = "Update: Your Application for NMAT 2026 ... Career outcomes ₹24.60 LPA Average CTC ₹41.28 LPA Highest CTC ₹31.25 LPA Top 10%"
        val raw = RawMessage(
            id = "nmat-email-1",
            sourceType = SourceType.NOTIFICATION,
            packageName = "com.google.android.gm",
            senderId = null,
            title = "NMAT 2026",
            text = text,
            timestamp = 1727600000000L
        )

        val source = sourceDetector.detect("com.google.android.gm", text)
        val evidence = evidenceExtractor.extract(raw, source)
        val result = classifier.classify(evidence, text, source)

        assertTrue(evidence.amountHasForbiddenShape, "Expected forbidden shape (LPA / CTC) to be detected")
        assertFalse(evidence.amountInTransactionContext, "Salary figures must not have transaction context")
        assertTrue(result.pTransaction < 0.10, "Expected p < 0.10 for NMAT salary email, got ${result.pTransaction}")
        assertEquals(PolicyEngine.Action.IGNORE, policy.action(result.pTransaction))
    }

    @Test
    fun testFailure3_JioDiscountPromoIgnored() {
        val text = "8 brands, 1 Mega Sale — Up to ₹600 off BGMI UC. 1-4 Oct"
        val raw = RawMessage(
            id = "jio-promo-1",
            sourceType = SourceType.NOTIFICATION,
            packageName = "com.jio.myjio",
            senderId = null,
            title = "Mega Sale",
            text = text,
            timestamp = 1727600000000L
        )

        val source = sourceDetector.detect("com.jio.myjio", text)
        val evidence = evidenceExtractor.extract(raw, source)
        val result = classifier.classify(evidence, text, source)

        assertTrue(evidence.amountHasForbiddenShape, "Expected forbidden shape (up to / off) to be detected")
        assertTrue(result.pTransaction < 0.10, "Expected p < 0.10 for Jio promo discount, got ${result.pTransaction}")
        assertEquals(PolicyEngine.Action.IGNORE, policy.action(result.pTransaction))
    }

    @Test
    fun testRealUnionBankDebitSmsAutoLogged() {
        val text = "Dear Customer, INR 250.00 debited from A/c XX0383 on 01-10-2026 14:15:00 at Swiggy UPI Ref 4293810294. Bal: INR 3500.00"
        val raw = RawMessage(
            id = "union-bank-sms",
            sourceType = SourceType.SMS,
            packageName = null,
            senderId = "UB-UNIONB",
            title = "UB-UNIONB",
            text = text,
            timestamp = 1727600000000L
        )

        val source = sourceDetector.detect("UB-UNIONB", text)
        val evidence = evidenceExtractor.extract(raw, source)
        val result = classifier.classify(evidence, text, source)

        assertTrue(evidence.amountAndDebitSameSentence, "Expected debit verb in same sentence as amount")
        assertTrue(evidence.amountInTransactionContext, "Expected amount in transaction context")
        assertFalse(evidence.amountHasForbiddenShape, "Bank debit SMS must not trigger forbidden shape")
        assertTrue(result.pTransaction > 0.95, "Expected p > 0.95 for genuine Union Bank SMS, got ${result.pTransaction}")
        assertEquals("0383", result.accountLast4)
        assertEquals(TransactionType.DEBIT, result.type)
        assertEquals(PolicyEngine.Action.AUTO_LOG, policy.action(result.pTransaction))
    }

    @Test
    fun testRecentStateRepository_IndependentSameAmountNotPenalized() {
        val repo = com.cashbuddy.core.prob.RecentStateRepository()
        val t0 = 1727600000000L

        // Rapido ₹50 transaction recorded
        repo.record(amount = 50.0, merchant = "Rapido", sourcePackage = "com.rapido.passenger", timestamp = t0)

        // 2 minutes later, Zomato ₹50 transaction arrives
        val isDuplicate = repo.recentSimilarAmount(
            newAmount = 50.0,
            newMerchant = "Zomato",
            newPackage = "com.application.zomato",
            now = t0 + 120_000L
        )

        assertFalse(isDuplicate, "Independent ₹50 transactions with different merchants/packages must NOT be penalized")
    }

    @Test
    fun testRecentStateRepository_SameMerchantSameAmountDetected() {
        val repo = com.cashbuddy.core.prob.RecentStateRepository()
        val t0 = 1727600000000L

        // SmartQ ₹130 transaction recorded
        repo.record(amount = 130.0, merchant = "SmartQ", sourcePackage = "com.smartq", timestamp = t0)

        // 30 seconds later, duplicate SmartQ ₹130 arrives
        val isDuplicate = repo.recentSimilarAmount(
            newAmount = 130.0,
            newMerchant = "SmartQ",
            newPackage = "com.smartq",
            now = t0 + 30_000L
        )

        assertTrue(isDuplicate, "Same merchant with same amount in 5m window must be detected as duplicate")
    }

    @Test
    fun testRecentStateRepository_BurstDetected() {
        val repo = com.cashbuddy.core.prob.RecentStateRepository()
        val t0 = 1727600000000L

        repo.recordRaw(t0, "com.pkg1")
        repo.recordRaw(t0 + 5_000L, "com.pkg2")
        repo.recordRaw(t0 + 10_000L, "com.pkg3")
        repo.recordRaw(t0 + 15_000L, "com.pkg4")

        assertTrue(repo.burstDetected(t0 + 20_000L), "More than 3 raw messages within 60s must trigger burstDetected")
        assertFalse(repo.burstDetected(t0 + 100_000L), "Burst must clear after 60s window passes")
    }

    @Test
    fun testP2PIncomingPaymentCreditAndMerchant() {
        val text = "MANJAPPA SON OF DANAPPA paid you ₹7,000.00 Payment from PhonePe"
        val raw = RawMessage(
            id = "test-gpay-incoming",
            sourceType = SourceType.NOTIFICATION,
            packageName = "com.google.android.apps.nbu.paisa.user",
            senderId = null,
            title = "",
            text = text,
            timestamp = 1727600000000L
        )

        val source = sourceDetector.detect("com.google.android.apps.nbu.paisa.user", text)
        val evidence = evidenceExtractor.extract(raw, source)
        val result = classifier.classify(evidence, text, source, raw.packageName)

        assertEquals(7000.0, result.amount)
        assertEquals(TransactionType.CREDIT, result.type, "P2P 'paid you' must be classified as CREDIT, not DEBIT")
        assertEquals("Manjappa Son Of Danappa", result.merchant, "Sender before 'paid you' must be extracted as merchant")
        assertTrue(result.pTransaction >= 0.85, "Legitimate P2P transfer must have high confidence, got ${result.pTransaction}")
    }

    @Test
    fun testP2POutgoingPaymentDebitAndMerchant() {
        val text = "Paid ₹350.00 to Chai Point successfully. Transaction ID: T2610011234."
        val raw = RawMessage(
            id = "test-phonepe-outgoing",
            sourceType = SourceType.NOTIFICATION,
            packageName = "com.phonepe.app",
            senderId = null,
            title = "",
            text = text,
            timestamp = 1727600000000L
        )

        val source = sourceDetector.detect("com.phonepe.app", text)
        val evidence = evidenceExtractor.extract(raw, source)
        val result = classifier.classify(evidence, text, source, raw.packageName)

        assertEquals(350.0, result.amount)
        assertEquals(TransactionType.DEBIT, result.type, "Outgoing 'Paid ... to' must be classified as DEBIT")
        assertEquals("Chai Point", result.merchant)
    }

    @Test
    fun testP2PIncomingReceivedFrom() {
        val text = "Received ₹500 from Ramesh Kumar via PhonePe"
        val raw = RawMessage(
            id = "test-received-from",
            sourceType = SourceType.NOTIFICATION,
            packageName = "net.one97.paytm",
            senderId = null,
            title = "",
            text = text,
            timestamp = 1727600000000L
        )

        val source = sourceDetector.detect("net.one97.paytm", text)
        val evidence = evidenceExtractor.extract(raw, source)
        val result = classifier.classify(evidence, text, source, raw.packageName)

        assertEquals(500.0, result.amount)
        assertEquals(TransactionType.CREDIT, result.type)
        assertEquals("Ramesh Kumar", result.merchant)
    }
}

