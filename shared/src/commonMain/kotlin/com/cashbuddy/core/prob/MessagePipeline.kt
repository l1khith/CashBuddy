// NO-NETWORK
package com.cashbuddy.core.prob

import com.cashbuddy.core.CategoryEngine
import com.cashbuddy.domain.model.Transaction
import com.cashbuddy.domain.model.TransactionStatus
import com.cashbuddy.domain.model.TransactionType
import com.cashbuddy.domain.repository.AccountRepository
import com.cashbuddy.domain.repository.CategoryRepository
import com.cashbuddy.domain.repository.RawMessageRepository
import com.cashbuddy.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.firstOrNull

sealed interface PipelineOutcome {
    data class Ignored(val rawId: String, val p: Double) : PipelineOutcome
    data class PendingReview(val txId: Long, val p: Double) : PipelineOutcome
    data class Logged(val txId: Long, val p: Double) : PipelineOutcome
    data class Merged(val txId: Long, val p: Double) : PipelineOutcome
    data class DuplicateSkipped(val rawId: String) : PipelineOutcome
}

class MessagePipeline(
    private val sourceDetector: SourceDetector,
    private val evidenceExtractor: EvidenceExtractor,
    private val classifier: ProbabilisticClassifier,
    private val policy: PolicyEngine,
    private val dedup: DedupEngine,
    private val accountRegistry: AccountRegistry,
    private val categoryEngine: CategoryEngine,
    private val transactionRepo: TransactionRepository,
    private val rawMessageRepo: RawMessageRepository,
    private val categoryRepo: CategoryRepository
) {
    suspend fun ingest(raw: RawMessage): PipelineOutcome {
        // 1. Audit trail
        rawMessageRepo.insert(raw)

        // 2. Source detection
        val source = sourceDetector.detect(raw.packageName ?: raw.senderId ?: "", raw.text)

        // 3. Evidence extraction
        val evidence = evidenceExtractor.extract(raw, source)

        // 4. Classify
        val fullText = if (raw.title.isNotBlank()) "${raw.title} ${raw.text}" else raw.text
        val classification = classifier.classifySuspending(evidence, fullText, source)

        // 5. Policy decision
        val action = policy.action(classification.pTransaction)

        return when (action) {
            PolicyEngine.Action.IGNORE -> {
                PipelineOutcome.Ignored(raw.id, classification.pTransaction)
            }

            PolicyEngine.Action.ASK_USER,
            PolicyEngine.Action.LOG_AND_FLAG,
            PolicyEngine.Action.AUTO_LOG -> {
                val amount = classification.amount ?: 0.0
                if (amount <= 0.0) {
                    return PipelineOutcome.Ignored(raw.id, classification.pTransaction)
                }

                // 6. Dedup against recent 5-min window
                val now = raw.timestamp
                val recentTxs = transactionRepo.getByDateRange(now - DedupEngine.WINDOW_MS, now + DedupEngine.WINDOW_MS).firstOrNull() ?: emptyList()
                val candidates = recentTxs.map {
                    DedupEngine.ExistingCandidate(
                        id = it.id,
                        amount = it.amount,
                        merchant = it.merchant,
                        accountLast4 = it.sourceApp,
                        pTransaction = it.confidence.toDouble(),
                        timestamp = it.timestamp
                    )
                }

                val dedupDecision = dedup.resolve(
                    newTx = DedupEngine.DedupInput(
                        amount = amount,
                        merchant = classification.merchant,
                        accountLast4 = classification.accountLast4,
                        pTransaction = classification.pTransaction,
                        timestamp = raw.timestamp
                    ),
                    existing = candidates
                )

                when (dedupDecision) {
                    is DedupEngine.Decision.Insert -> {
                        // 7. Account resolution
                        val accountId = classification.accountLast4?.let {
                            accountRegistry.findOrCreate(it, classification.bankHint)
                        } ?: 1L

                        // 8. Categorization
                        val categoryMatch = categoryEngine.getCategory(classification.merchant)
                        val allCategories = categoryRepo.getAll().firstOrNull() ?: emptyList()
                        val matchedCategory = allCategories.find {
                            it.name.equals(categoryMatch.category, ignoreCase = true)
                        } ?: allCategories.firstOrNull()
                        val categoryId = matchedCategory?.id ?: 1L

                        val status = if (action == PolicyEngine.Action.AUTO_LOG) {
                            TransactionStatus.CONFIRMED
                        } else {
                            TransactionStatus.PENDING
                        }

                        val tx = Transaction(
                            id = 0L,
                            accountId = accountId,
                            categoryId = categoryId,
                            amount = amount,
                            type = classification.type ?: TransactionType.DEBIT,
                            status = status,
                            rawText = fullText,
                            sourceApp = raw.packageName ?: raw.senderId ?: "unknown",
                            merchant = classification.merchant ?: "Unknown Merchant",
                            confidence = classification.pTransaction.toFloat(),
                            timestamp = raw.timestamp,
                            createdAt = raw.timestamp,
                            updatedAt = raw.timestamp
                        )

                        val insertedId = transactionRepo.insert(tx)
                        rawMessageRepo.updateResultingTx(raw.id, insertedId.toString())

                        if (status == TransactionStatus.CONFIRMED) {
                            PipelineOutcome.Logged(insertedId, classification.pTransaction)
                        } else {
                            PipelineOutcome.PendingReview(insertedId, classification.pTransaction)
                        }
                    }

                    is DedupEngine.Decision.Merge -> {
                        val existing = recentTxs.find { it.id == dedupDecision.existingId }
                        if (existing != null) {
                            val newStatus = if (dedupDecision.upgradedFields.clearReview) {
                                TransactionStatus.CONFIRMED
                            } else {
                                existing.status
                            }
                            transactionRepo.updateStatus(dedupDecision.existingId, newStatus)
                            rawMessageRepo.updateResultingTx(raw.id, dedupDecision.existingId.toString())
                        }
                        PipelineOutcome.Merged(dedupDecision.existingId, classification.pTransaction)
                    }

                    is DedupEngine.Decision.Skip -> {
                        PipelineOutcome.DuplicateSkipped(raw.id)
                    }
                }
            }
        }
    }
}
