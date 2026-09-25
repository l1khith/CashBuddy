// NO-NETWORK
package com.cashbuddy.core

import com.cashbuddy.core.prob.EvidenceExtractor
import com.cashbuddy.core.prob.PolicyEngine
import com.cashbuddy.core.prob.ProbabilisticClassifier
import com.cashbuddy.core.prob.RawMessage
import com.cashbuddy.core.prob.SourceDetector
import com.cashbuddy.core.prob.SourceType

/**
 * Probabilistic Notification Parser for CashBuddy.
 * Evaluates messages based on content-first probabilistic confidence.
 * Hardcoded sender and package allowlists have been completely purged.
 */
class NotificationParser(
    private val categoryEngine: CategoryEngine = CategoryEngine(),
    private val sourceDetector: SourceDetector = SourceDetector(),
    private val evidenceExtractor: EvidenceExtractor = EvidenceExtractor(),
    private val classifier: ProbabilisticClassifier = ProbabilisticClassifier(),
    private val policy: PolicyEngine = PolicyEngine()
) {
    fun parse(notification: RawNotification): ParsedTransaction? {
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

        val action = policy.action(classification.pTransaction)
        if (action == PolicyEngine.Action.IGNORE) {
            return null
        }

        val amount = classification.amount ?: return null
        val txType = classification.type ?: return null
        val merchant = classification.merchant ?: "Unknown Merchant"
        val categoryMatch = categoryEngine.getCategory(merchant)

        val category = when (categoryMatch.category) {
            "Food & Dining", "Food" -> Category.Food
            "Transportation", "Transport" -> Category.Transport
            "Shopping & Retail", "Shopping" -> Category.Shopping
            "Bills & Utilities", "Bills" -> Category.Bills
            "Entertainment & Recreation", "Entertainment" -> Category.Entertainment
            "Healthcare & Medical", "Health" -> Category.Health
            "Salary" -> Category.Salary
            "Refund" -> Category.Refund
            else -> Category.Unknown
        }

        val coreTxType = if (txType == com.cashbuddy.domain.model.TransactionType.DEBIT) {
            TransactionType.DEBIT
        } else {
            TransactionType.CREDIT
        }

        return ParsedTransaction(
            amount = amount,
            transactionType = coreTxType,
            category = category,
            merchant = merchant,
            accountId = classification.accountLast4?.let { "XX$it" },
            sourceApp = notification.packageName,
            rawText = fullText,
            confidence = classification.pTransaction.toFloat(),
            timestamp = notification.timestamp
        )
    }
}
