// NO-NETWORK
package com.cashbuddy.core.prob.gate

import com.cashbuddy.core.prob.Evidence
import com.cashbuddy.core.prob.NoOpCalibrator
import com.cashbuddy.core.prob.ProbabilisticClassifier
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GateModelTest {

    @Test
    fun testGateTokenizerFnv1aParity() {
        val mask = (1 shl 18) - 1 // 262143
        // Python: fnv1a32('w:swiggy') == 3438296886; 3438296886 & 262143 == 16182
        val hashSwiggy = GateTokenizer.fnv1a32("w:swiggy")
        assertEquals(16182, hashSwiggy and mask)

        // Python: fnv1a32('w:debited') & 262143 == 98603
        val hashDebited = GateTokenizer.fnv1a32("w:debited")
        assertEquals(98603, hashDebited and mask)

        // Python: fnv1a32('w:credited') & 262143 == 61016
        val hashCredited = GateTokenizer.fnv1a32("w:credited")
        assertEquals(61016, hashCredited and mask)
    }

    @Test
    fun testGateTokenizerBucketsNormalized() {
        val mask = (1 shl 18) - 1
        val buckets = GateTokenizer.buckets("HDFC Bank: Rs.1,250 debited to SWIGGY", mask)
        assertTrue(buckets.isNotEmpty(), "Buckets must not be empty")

        // Should include word unigram for 'swiggy'
        val expectedSwiggy = GateTokenizer.fnv1a32("w:swiggy") and mask
        assertTrue(buckets.contains(expectedSwiggy), "Buckets must contain w:swiggy")

        // Should replace Rs.1,250 with cur and num
        val expectedCur = GateTokenizer.fnv1a32("w:cur") and mask
        val expectedNum = GateTokenizer.fnv1a32("w:num") and mask
        assertTrue(buckets.contains(expectedCur), "Buckets must contain w:cur")
        assertTrue(buckets.contains(expectedNum), "Buckets must contain w:num")
    }

    @Test
    fun testGateNgramsParsingAndScoring() {
        val scale = 4096
        val entryCount = 2
        val bytes = ByteArray(8 + entryCount * 6)
        // Magic "CBG1"
        bytes[0] = 0x43; bytes[1] = 0x42; bytes[2] = 0x47; bytes[3] = 0x31
        // Entry count = 2 (little-endian)
        bytes[4] = 2; bytes[5] = 0; bytes[6] = 0; bytes[7] = 0

        // Entry 0: bucket = 100, weight = 4096 (+1.0 logit)
        bytes[8] = 100; bytes[9] = 0; bytes[10] = 0; bytes[11] = 0
        bytes[12] = 0x00; bytes[13] = 0x10 // 4096 in little endian

        // Entry 1: bucket = 500, weight = -2048 (-0.5 logit)
        bytes[14] = (-12).toByte(); bytes[15] = 1; bytes[16] = 0; bytes[17] = 0 // 500 = 0x01F4
        // 500 in little endian: 0xF4, 0x01, 0x00, 0x00
        bytes[14] = 0xF4.toByte(); bytes[15] = 0x01; bytes[16] = 0; bytes[17] = 0
        // -2048 in int16 little endian = 0xF800 -> 0x00, 0xF8
        bytes[18] = 0x00; bytes[19] = 0xF8.toByte()

        val ngrams = GateNgrams.parse(bytes, expectedBuckets = 1000)
        assertNotNull(ngrams)
        assertEquals(2, ngrams.size)

        // Query only bucket 100 -> score = +1.0
        val score1 = ngrams.score(setOf(100))
        assertEquals(1.0, score1, 1e-4)

        // Query bucket 100 and 500 -> score = 1.0 - 0.5 = 0.5
        val scoreBoth = ngrams.score(setOf(100, 500))
        assertEquals(0.5, scoreBoth, 1e-4)

        // Query unknown bucket -> score = 0.0
        val scoreUnknown = ngrams.score(setOf(999))
        assertEquals(0.0, scoreUnknown, 1e-4)
    }

    @Test
    fun testGateNgramsCorruptionsReturnNull() {
        // Bad magic
        val badMagic = ByteArray(20)
        badMagic[0] = 0x58
        assertNull(GateNgrams.parse(badMagic, 1000))

        // Truncated buffer
        val truncated = ByteArray(4)
        truncated[0] = 0x43; truncated[1] = 0x42; truncated[2] = 0x47; truncated[3] = 0x31
        assertNull(GateNgrams.parse(truncated, 1000))

        // Unsorted buckets
        val unsorted = ByteArray(8 + 2 * 6)
        unsorted[0] = 0x43; unsorted[1] = 0x42; unsorted[2] = 0x47; unsorted[3] = 0x31
        unsorted[4] = 2; unsorted[5] = 0; unsorted[6] = 0; unsorted[7] = 0
        // bucket 200 then bucket 100
        unsorted[8] = 200.toByte(); unsorted[9] = 0; unsorted[10] = 0; unsorted[11] = 0
        unsorted[14] = 100; unsorted[15] = 0; unsorted[16] = 0; unsorted[17] = 0
        assertNull(GateNgrams.parse(unsorted, 1000))
    }

    @Test
    fun testGateModelScoringCalculation() {
        val features = mutableMapOf<String, Double>()
        GateSignal.entries.forEach { features[it.key] = 1.0 }
        // Set specific LRs for testing
        features["amount_in_context"] = 4.0 // ln(4) = 1.38629
        features["account"] = 2.0           // ln(2) = 0.69315
        features["OTP"] = 0.1               // ln(0.1) = -2.30259

        val prior = 0.20
        val priorLogit = ln(prior / (1.0 - prior)) // -1.386294
        val biasAdjust = -0.5
        val intercept = priorLogit + biasAdjust

        val weights = GateWeights(
            modelVersion = "gate-test",
            schemaVersion = 1,
            prior = prior,
            intercept = intercept,
            biasAdjust = biasAdjust,
            threshold = 0.60,
            features = features,
            ngrams = GateNgramSpec(enabled = false)
        )

        val model = GateModel(weights = weights, ngrams = null)
        val evidence = Evidence(
            amountInTransactionContext = true,
            hasAccount = true,
            hasOtp = false
        )

        val score = model.score(evidence, "text")
        // Expected logit = baseLogit + ln(4) + ln(2)
        // baseLogit = priorLogit + biasAdjust = -1.386294 - 0.5 = -1.886294
        // logit = -1.886294 + 1.386294 + 0.693147 = 0.193147
        val expectedLogit = (priorLogit + biasAdjust) + ln(4.0) + ln(2.0)
        assertEquals(expectedLogit, score.logit, 1e-4)

        val expectedP = 1.0 / (1.0 + exp(-expectedLogit))
        assertEquals(expectedP, score.pTransaction, 1e-4)
    }

    @Test
    fun testGateModelProviderGracefulFallback() = runBlocking {
        // Missing files reader -> returns empty/throws
        val failingProvider = GateModelProvider(readBytes = { throw IllegalArgumentException("file not found: $it") })
        val model = failingProvider.model()
        assertNull(model, "Model must be null on load failure")
        assertNotNull(failingProvider.failureReason(), "Failure reason must be recorded")

        // Classifier should fall back to legacy heuristics without crashing
        val classifier = ProbabilisticClassifier(
            calibrator = NoOpCalibrator,
            gateModelProvider = failingProvider
        )

        val evidence = Evidence(
            hasAmount = true,
            amountInTransactionContext = true,
            hasAccount = true
        )
        val result = classifier.classify(evidence, "Rs 500 debited from A/c XX1234")
        assertTrue(result.pTransaction > 0.5, "Should classify successfully using fallback table")
        assertFalse(result.contributions.any { it.signal == "ngrams" }, "Should not have ngram contribution in fallback")
    }

    @Test
    fun testGateModelProviderDegradedWhenNgramsFail() = runBlocking {
        val features = mutableMapOf<String, Double>()
        GateSignal.entries.forEach { features[it.key] = 1.0 }
        val prior = 0.20
        val priorLogit = ln(prior / (1.0 - prior))
        val biasAdjust = -0.5

        val validWeights = GateWeights(
            modelVersion = "gate-test-degraded",
            schemaVersion = 1,
            prior = prior,
            intercept = priorLogit + biasAdjust,
            biasAdjust = biasAdjust,
            threshold = 0.60,
            features = features,
            ngrams = GateNgramSpec(enabled = true, buckets = 262144, file = "gate_ngrams.bin")
        )
        val jsonWeights = Json.encodeToString(GateWeights.serializer(), validWeights)

        val degradedProvider = GateModelProvider(readBytes = { path ->
            if (path.endsWith("gate_weights.json")) {
                jsonWeights.encodeToByteArray()
            } else {
                throw IllegalStateException("corrupt binary")
            }
        })

        val model = degradedProvider.model()
        assertNotNull(model, "Model must load even if ngrams fail")
        assertNotNull(model.degradedReason, "Degraded reason must be populated")

        val evidence = Evidence(hasAmount = true, amountInTransactionContext = true)
        val score = model.score(evidence, "Rs 100 spent")
        assertEquals(0.0, score.ngramScore, "Ngram score must be 0.0 when degraded")
    }

    @Test
    fun testUpdatedWeightsScoreMobilePaymentNotificationsHighAndNoiseLow() {
        val features = mutableMapOf<String, Double>()
        GateSignal.entries.forEach { features[it.key] = 1.0 }
        features["amount_in_context"] = 3.16214682
        features["amount_debit_sent"] = 5.07865564
        features["amount"] = 2.90264961
        features["account"] = 4.49647516
        features["utr"] = 7.20660988
        features["merchant_pkg"] = 0.65921071
        features["success"] = 3.61304232
        features["OTP"] = 0.62983484
        features["promo"] = 0.58535188
        features["offer"] = 0.34531764
        features["failure"] = 0.16128421
        features["failure_in_context"] = 0.18100943
        features["pending_in_context"] = 0.85142167

        val prior = 0.20
        val priorLogit = ln(prior / (1.0 - prior))
        val biasAdjust = -0.13008257
        val intercept = priorLogit + biasAdjust

        val weights = GateWeights(
            modelVersion = "gate-2026.11.19",
            schemaVersion = 1,
            prior = prior,
            intercept = intercept,
            biasAdjust = biasAdjust,
            threshold = 0.75,
            features = features,
            ngrams = GateNgramSpec(enabled = false)
        )
        val model = GateModel(weights = weights, ngrams = null)

        // 1. Mobile app notification without account and without UTR: "Paid ₹150 to Chai Point"
        val mobileEvidence = Evidence(
            hasAmount = true,
            amountInTransactionContext = true,
            amountAndDebitSameSentence = true,
            fromMerchantPackage = true
        )
        val mobileScore = model.score(mobileEvidence, "Paid ₹150 to Chai Point")
        assertTrue(mobileScore.pTransaction >= 0.65, "Mobile payment notification must score high in LOG_AND_FLAG range without n-grams (got ${mobileScore.pTransaction})")

        // 2. Bank SMS with Account and UTR
        val bankEvidence = Evidence(
            hasAmount = true,
            amountInTransactionContext = true,
            amountAndDebitSameSentence = true,
            hasAccount = true,
            hasUtr = true
        )
        val bankScore = model.score(bankEvidence, "Rs 150 debited from A/c XX1234. Ref 123456789012")
        assertTrue(bankScore.pTransaction >= 0.95, "Bank SMS must score very high (got ${bankScore.pTransaction})")

        // 3. Noise / Non-transaction: no amount
        val noiseEvidence = Evidence(
            hasAmount = false,
            amountInTransactionContext = false,
            amountAndDebitSameSentence = false
        )
        val noiseScore = model.score(noiseEvidence, "Bitcoin selloff accelerating")
        assertTrue(noiseScore.pTransaction < 0.25, "Noise must score below 0.25 (got ${noiseScore.pTransaction})")
    }

    @Test
    fun testScoreText_FastPathEvaluatesWithoutEvidence() {
        val features = mutableMapOf<String, Double>()
        GateSignal.entries.forEach { features[it.key] = 1.0 }
        val prior = 0.20
        val priorLogit = ln(prior / (1.0 - prior))
        val biasAdjust = 0.08571638
        val intercept = priorLogit + biasAdjust
        val weights = GateWeights(
            modelVersion = "gate-text-2026.11.19",
            schemaVersion = 1,
            prior = prior,
            intercept = intercept,
            biasAdjust = biasAdjust,
            threshold = 0.65,
            features = features,
            ngrams = GateNgramSpec(enabled = false)
        )
        val model = GateModel(weights = weights, ngrams = null)

        val score = model.scoreText("Test fast path")
        assertEquals(intercept, score.logit, 1e-4)
        assertTrue(score.pTransaction > 0.0 && score.pTransaction < 1.0)
    }
}
