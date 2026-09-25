// NO-NETWORK
package com.cashbuddy.domain.parser

import com.cashbuddy.core.CategoryEngine
import com.cashbuddy.core.prob.EvidenceExtractor
import com.cashbuddy.core.prob.PolicyEngine
import com.cashbuddy.core.prob.ProbabilisticClassifier
import com.cashbuddy.core.prob.RawMessage
import com.cashbuddy.core.prob.SourceDetector
import com.cashbuddy.core.prob.SourceType
import com.cashbuddy.domain.model.TransactionType

data class RawNotificationData(
    val packageName: String,
    val title: String,
    val text: String,
    val timestamp: Long
)

data class ParsedNotificationResult(
    val amount: Double,
    val type: TransactionType,
    val categoryName: String,
    val merchant: String,
    val accountId: String?,
    val sourceApp: String,
    val rawText: String,
    val confidence: Float,
    val timestamp: Long
)

class KotlinNotificationParser(
    private val categoryEngine: CategoryEngine = CategoryEngine(),
    private val sourceDetector: SourceDetector = SourceDetector(),
    private val evidenceExtractor: EvidenceExtractor = EvidenceExtractor(),
    private val classifier: ProbabilisticClassifier = ProbabilisticClassifier(),
    private val policy: PolicyEngine = PolicyEngine()
) {
    fun parse(notification: RawNotificationData): ParsedNotificationResult? {
        val fullText = if (notification.title.isNotBlank()) {
            "${notification.title} ${notification.text}".trim()
        } else {
            notification.text.trim()
        }

        val raw = RawMessage(
            id = "notif_${notification.timestamp}",
            sourceType = SourceType.NOTIFICATION,
            packageName = notification.packageName,
            senderId = null,
            title = notification.title,
            text = notification.text,
            timestamp = notification.timestamp,
            imagePath = null
        )

        val source = sourceDetector.detect(notification.packageName, fullText)
        val evidence = evidenceExtractor.extract(raw, source)
        val classification = classifier.classify(evidence, fullText, source)

        if (policy.action(classification.pTransaction) == PolicyEngine.Action.IGNORE) {
            return null
        }

        val amount = classification.amount ?: return null
        val txType = classification.type ?: return null
        val merchant = classification.merchant ?: "Unknown Merchant"
        val categoryMatch = categoryEngine.getCategory(merchant)

        val categoryName = when (categoryMatch.category) {
            "Food & Dining" -> "Food"
            "Transportation" -> "Transport"
            "Shopping & Retail" -> "Shopping"
            "Bills & Utilities" -> "Bills"
            "Entertainment & Recreation" -> "Entertainment"
            "Healthcare & Medical" -> "Health"
            else -> categoryMatch.category
        }

        return ParsedNotificationResult(
            amount = amount,
            type = txType,
            categoryName = categoryName,
            merchant = merchant,
            accountId = classification.accountLast4?.let { "XX$it" },
            sourceApp = notification.packageName,
            rawText = fullText,
            confidence = classification.pTransaction.toFloat(),
            timestamp = notification.timestamp
        )
    }
}
