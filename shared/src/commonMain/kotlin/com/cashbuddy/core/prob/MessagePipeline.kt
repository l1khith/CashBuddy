// NO-NETWORK
package com.cashbuddy.core.prob

import com.cashbuddy.core.CategoryEngine
import com.cashbuddy.debug.DebugLogger
import com.cashbuddy.domain.model.CategoryType
import com.cashbuddy.domain.model.Transaction
import com.cashbuddy.domain.model.TransactionStatus
import com.cashbuddy.domain.model.TransactionType
import com.cashbuddy.domain.repository.AccountRepository
import com.cashbuddy.domain.repository.CategoryRepository
import com.cashbuddy.domain.repository.RawMessageRepository
import com.cashbuddy.domain.repository.SettingsRepository
import com.cashbuddy.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.firstOrNull

sealed interface PipelineOutcome {
    val outcomeName: String
    val resultingTxId: String? get() = null
    val mergeTargetId: String? get() = null

    data class Ignored(val rawId: String, val p: Double, val reason: String? = null) : PipelineOutcome {
        override val outcomeName: String = "IGNORED"
    }
    data class PendingReview(
        val txId: Long,
        val p: Double,
        val amount: Double = 0.0,
        val merchant: String = ""
    ) : PipelineOutcome {
        override val outcomeName: String = "PENDING_REVIEW"
        override val resultingTxId: String = txId.toString()
    }
    data class Logged(
        val txId: Long,
        val p: Double,
        val amount: Double = 0.0,
        val merchant: String = ""
    ) : PipelineOutcome {
        override val outcomeName: String = "LOGGED"
        override val resultingTxId: String = txId.toString()
    }
    data class Merged(val txId: Long, val p: Double) : PipelineOutcome {
        override val outcomeName: String = "MERGED"
        override val mergeTargetId: String = txId.toString()
    }
    data class DuplicateSkipped(val rawId: String) : PipelineOutcome {
        override val outcomeName: String = "DUPLICATE_SKIPPED"
    }
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
    private val categoryRepo: CategoryRepository,
    private val debugLogger: DebugLogger? = null,
    private val recentStateRepository: RecentStateRepository? = null,
    private val settingsRepo: SettingsRepository? = null
) {
    suspend fun ingest(raw: RawMessage): PipelineOutcome {
        recentStateRepository?.recordRaw(raw.timestamp, raw.packageName)
        val logId = debugLogger?.captureRaw(raw, raw.sourceType.name, raw.packageName, raw.senderId) ?: ""
        try {
            // 1. Audit trail
            rawMessageRepo.insert(raw)

            // 2. Source detection
            val source = sourceDetector.detect(raw.packageName ?: raw.senderId ?: "", raw.text)

            // 3. Evidence extraction
            val evidence = evidenceExtractor.extract(raw, source)

            // 4. Classify
            val fullText = if (raw.title.isNotBlank()) "${raw.title} ${raw.text}" else raw.text
            val classification = classifier.classifySuspending(evidence, fullText, source, raw.packageName)

            debugLogger?.recordClassification(
                logId = logId,
                detectedSource = source.name,
                evidence = evidence,
                pTransaction = classification.pTransaction,
                contributions = classification.contributions,
                fieldConfidences = classification.fieldConfidences
            )

            // 5. Policy decision
            val maxAutoConfirm = settingsRepo?.getAutoConfirmThreshold()?.firstOrNull() ?: 10000.0
            val action = policy.action(
                p = classification.pTransaction,
                hasAccount = classification.accountLast4 != null,
                amount = classification.amount ?: 0.0,
                maxAutoConfirmAmount = maxAutoConfirm
            )
            debugLogger?.recordPolicy(logId, action.name)

            val outcome: PipelineOutcome = when (action) {
                PolicyEngine.Action.IGNORE -> {
                    PipelineOutcome.Ignored(raw.id, classification.pTransaction)
                }

                PolicyEngine.Action.ASK_USER,
                PolicyEngine.Action.LOG_AND_FLAG,
                PolicyEngine.Action.AUTO_LOG -> {
                    val amount = classification.amount ?: 0.0
                    if (amount <= 0.0) {
                        PipelineOutcome.Ignored(raw.id, classification.pTransaction, "Zero or unparsed amount")
                    } else {
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
                                } ?: if (classification.type == TransactionType.CREDIT) {
                                    allCategories.find { it.name.equals("Other Income", ignoreCase = true) }
                                        ?: allCategories.find { it.name.equals("Uncategorized", ignoreCase = true) }
                                        ?: allCategories.find { it.type == CategoryType.INCOME }
                                        ?: allCategories.firstOrNull()
                                } else {
                                    allCategories.find { it.name.equals("Uncategorized", ignoreCase = true) }
                                        ?: allCategories.firstOrNull()
                                }
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
                                recentStateRepository?.record(
                                    RecentTx(
                                        amount = amount,
                                        merchant = tx.merchant,
                                        sourcePackage = raw.packageName,
                                        timestamp = raw.timestamp
                                    )
                                )
                                rawMessageRepo.updateResultingTx(raw.id, insertedId.toString())

                                if (status == TransactionStatus.CONFIRMED) {
                                    PipelineOutcome.Logged(insertedId, classification.pTransaction, amount, tx.merchant)
                                } else {
                                    PipelineOutcome.PendingReview(insertedId, classification.pTransaction, amount, tx.merchant)
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
                                    val upgradedAccount = classification.accountLast4?.let {
                                        accountRegistry.findOrCreate(it, classification.bankHint)
                                    }
                                    if (upgradedAccount != null && upgradedAccount != existing.accountId) {
                                        transactionRepo.update(existing.copy(
                                            accountId = upgradedAccount,
                                            status = newStatus,
                                            confidence = maxOf(existing.confidence, classification.pTransaction.toFloat()),
                                            updatedAt = raw.timestamp
                                        ))
                                    } else {
                                        transactionRepo.updateStatus(dedupDecision.existingId, newStatus)
                                    }
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

            debugLogger?.recordOutcome(
                logId = logId,
                outcome = outcome.outcomeName,
                txId = outcome.resultingTxId,
                mergeTarget = outcome.mergeTargetId,
                error = if (outcome is PipelineOutcome.Ignored) outcome.reason else null
            )
            return outcome
        } catch (t: Throwable) {
            debugLogger?.recordOutcome(
                logId = logId,
                outcome = "ERROR",
                txId = null,
                mergeTarget = null,
                error = t.message
            )
            throw t
        }
    }
}
