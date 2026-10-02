// NO-NETWORK
package com.cashbuddy.core

import com.cashbuddy.core.prob.EvidenceExtractor
import com.cashbuddy.core.prob.NoOpCalibrator
import com.cashbuddy.core.prob.PolicyEngine
import com.cashbuddy.core.prob.ProbabilisticClassifier
import com.cashbuddy.core.prob.RawMessage
import com.cashbuddy.core.prob.SourceDetector
import com.cashbuddy.core.prob.SourceType
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AccuracyHarnessTest {

    data class LabeledSample(
        val id: Int,
        val sourceType: SourceType,
        val packageName: String?,
        val senderId: String?,
        val text: String,
        val groundTruthAction: String, // AUTO_LOG, LOG_AND_FLAG, IGNORE
        val expectedAmount: Double? = null
    )

    private val sourceDetector = SourceDetector()
    private val evidenceExtractor = EvidenceExtractor()
    private val classifier = ProbabilisticClassifier(calibrator = NoOpCalibrator)
    private val policy = PolicyEngine()

    private fun loadDataset(): List<LabeledSample> {
        var current = File(".").canonicalFile
        while (current.parentFile != null && !File(current, "settings.gradle.kts").exists()) {
            current = current.parentFile!!
        }
        val csvFile = File(current, "docs/accuracy/labelled_v1.csv")
        assertTrue(csvFile.exists(), "labelled_v1.csv must exist at ${csvFile.path}")

        val lines = csvFile.readLines().drop(1).filter { it.isNotBlank() }
        val samples = mutableListOf<LabeledSample>()

        for (line in lines) {
            val tokens = mutableListOf<String>()
            var sb = StringBuilder()
            var inQuotes = false
            for (ch in line) {
                if (ch == '\"') {
                    inQuotes = !inQuotes
                } else if (ch == ',' && !inQuotes) {
                    tokens.add(sb.toString().trim())
                    sb = StringBuilder()
                } else {
                    sb.append(ch)
                }
            }
            tokens.add(sb.toString().trim())

            if (tokens.size >= 6) {
                val id = tokens[0].toIntOrNull() ?: continue
                val sourceType = if (tokens[1].equals("SMS", ignoreCase = true)) SourceType.SMS else SourceType.NOTIFICATION
                val pkg = tokens[2].ifBlank { null }
                val sender = tokens[3].ifBlank { null }
                val text = tokens[4].trim('\"')
                val groundTruth = tokens[5]
                val amt = if (tokens.size > 6) tokens[6].toDoubleOrNull() else null

                samples.add(LabeledSample(id, sourceType, pkg, sender, text, groundTruth, amt))
            }
        }
        return samples
    }

    @Test
    fun testAccuracyHarnessOnDataset() {
        val samples = loadDataset()
        assertTrue(samples.size >= 100, "Dataset must have at least 100 samples, had ${samples.size}")

        var tp = 0
        var fp = 0
        var fn = 0
        var tn = 0

        var falsePositiveJobEmails = 0
        var falsePositiveSalaryEmails = 0
        var falsePositiveDiscounts = 0

        for (sample in samples) {
            val raw = RawMessage(
                id = "bench-${sample.id}",
                sourceType = sample.sourceType,
                packageName = sample.packageName,
                senderId = sample.senderId,
                title = sample.senderId ?: sample.packageName ?: "",
                text = sample.text,
                timestamp = 1727600000000L
            )

            val source = sourceDetector.detect(sample.packageName ?: sample.senderId ?: "", sample.text)
            val evidence = evidenceExtractor.extract(raw, source)
            val classification = classifier.classify(evidence, sample.text, source, sample.packageName)

            val predictedAction = policy.action(
                p = classification.pTransaction,
                hasAccount = classification.accountLast4 != null,
                amount = classification.amount ?: 0.0
            )

            val isActualTx = sample.groundTruthAction != "IGNORE"
            val isPredictedTx = predictedAction == PolicyEngine.Action.AUTO_LOG || predictedAction == PolicyEngine.Action.LOG_AND_FLAG

            if (isActualTx && isPredictedTx) {
                tp++
            } else if (!isActualTx && isPredictedTx) {
                fp++
                val textLower = sample.text.lowercase()
                if (textLower.contains("intern") || textLower.contains("job")) falsePositiveJobEmails++
                if (textLower.contains("lpa") || textLower.contains("ctc")) falsePositiveSalaryEmails++
                if (textLower.contains("off") || textLower.contains("sale")) falsePositiveDiscounts++
            } else if (isActualTx && !isPredictedTx) {
                fn++
            } else {
                tn++
            }

            if (sample.groundTruthAction == "AUTO_LOG") {
                assertEquals(PolicyEngine.Action.AUTO_LOG, predictedAction, "Sample ${sample.id} expected AUTO_LOG: '${sample.text}'")
            } else if (sample.groundTruthAction == "LOG_AND_FLAG") {
                assertEquals(PolicyEngine.Action.LOG_AND_FLAG, predictedAction, "Sample ${sample.id} expected LOG_AND_FLAG: '${sample.text}'")
            } else {
                assertTrue(
                    predictedAction == PolicyEngine.Action.IGNORE || predictedAction == PolicyEngine.Action.ASK_USER,
                    "Sample ${sample.id} expected IGNORE: '${sample.text}' but got $predictedAction (p=${classification.pTransaction})"
                )
            }
        }

        val precision = tp.toDouble() / (tp + fp)
        val recall = tp.toDouble() / (tp + fn)
        val f1 = 2.0 * precision * recall / (precision + recall)

        println("=== Accuracy Benchmark Results ===")
        println("Total samples: ${samples.size}")
        println("TP: $tp, FP: $fp, FN: $fn, TN: $tn")
        println("Precision: ${((precision * 10000).toInt() / 100.0)}%")
        println("Recall: ${((recall * 10000).toInt() / 100.0)}%")
        println("F1-Score: ${((f1 * 10000).toInt() / 100.0)}%")

        assertEquals(0, falsePositiveJobEmails, "Zero false positives allowed on job / recruitment emails")
        assertEquals(0, falsePositiveSalaryEmails, "Zero false positives allowed on salary / LPA reports")
        assertEquals(0, falsePositiveDiscounts, "Zero false positives allowed on promotional discounts")

        assertTrue(precision >= 0.98, "Precision must be >= 98%, was $precision")
        assertTrue(recall >= 0.98, "Recall must be >= 98%, was $recall")
        assertTrue(f1 >= 0.98, "F1 must be >= 98%, was $f1")
    }
}
