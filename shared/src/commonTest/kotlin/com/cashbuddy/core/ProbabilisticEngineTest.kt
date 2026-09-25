// NO-NETWORK
package com.cashbuddy.core

import com.cashbuddy.core.prob.DedupEngine
import com.cashbuddy.core.prob.EvidenceExtractor
import com.cashbuddy.core.prob.NoOpCalibrator
import com.cashbuddy.core.prob.PolicyEngine
import com.cashbuddy.core.prob.ProbabilisticClassifier
import com.cashbuddy.core.prob.RawMessage
import com.cashbuddy.core.prob.SourceDetector
import com.cashbuddy.core.prob.SourceType
import com.cashbuddy.domain.model.TransactionType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
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
}
