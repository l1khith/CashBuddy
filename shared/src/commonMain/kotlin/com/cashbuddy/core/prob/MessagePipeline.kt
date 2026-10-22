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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.roundToLong

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
    private val settingsRepo: SettingsRepository? = null,
    private val budgetAlertScheduler: com.cashbuddy.core.budget.BudgetAlertScheduler? = null,
    private val fraudDetector: com.cashbuddy.core.FraudDetector = com.cashbuddy.core.FraudDetector(transactionRepo),
    private val recurringPatternRepo: com.cashbuddy.domain.repository.RecurringPatternRepository? = null,
    private val anomalyNotifier: com.cashbuddy.core.anomaly.AnomalyNotifier? = null
) {
    private val mutex = Mutex()

    suspend fun ingest(raw: RawMessage): PipelineOutcome = mutex.withLock {
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
            var classification = classifier.classifySuspending(evidence, fullText, source, raw.packageName)

            // 4.1 Recurring Pattern Check (T6 / AC-2)
            var isRecurringMatch = false
            if (classification.merchant != null && classification.amount != null && classification.amount > 0.0) {
                val amountPaise = (classification.amount * 100.0).roundToLong()
                val pattern = recurringPatternRepo?.findByMerchantAndAmount(classification.merchant, amountPaise)
                if (pattern != null) {
                    isRecurringMatch = true
                    classification = classification.copy(
                        pTransaction = maxOf(classification.pTransaction, 0.95)
                    )
                }
            }

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
            val action = if (isRecurringMatch && (classification.amount ?: 0.0) < maxAutoConfirm) {
                PolicyEngine.Action.AUTO_LOG
            } else {
                policy.action(
                    p = classification.pTransaction,
                    hasAccount = classification.accountLast4 != null,
                    amount = classification.amount ?: 0.0,
                    maxAutoConfirmAmount = maxAutoConfirm
                )
            }
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
                        // 6. Dedup against recent 5-min window (or 24-hour window if UTR present)
                        val now = raw.timestamp
                        val utr = classification.utr
                        val windowMs = if (utr != null) 24 * 60 * 60 * 1000L else DedupEngine.WINDOW_MS
                        val recentTxs = transactionRepo.getByDateRange(now - windowMs, now + windowMs).firstOrNull() ?: emptyList()
                        val candidates = recentTxs.map {
                            val candidateUtr = evidenceExtractor.extractUtr(it.rawText)
                            DedupEngine.ExistingCandidate(
                                id = it.id,
                                amount = it.amount,
                                merchant = it.merchant,
                                accountLast4 = it.sourceApp,
                                pTransaction = it.confidence.toDouble(),
                                timestamp = it.timestamp,
                                utr = candidateUtr
                            )
                        }

                        val dedupDecision = dedup.resolve(
                            newTx = DedupEngine.DedupInput(
                                amount = amount,
                                merchant = classification.merchant,
                                accountLast4 = classification.accountLast4,
                                pTransaction = classification.pTransaction,
                                timestamp = raw.timestamp,
                                utr = utr
                            ),
                            existing = candidates
                        )

                        when (dedupDecision) {
                            is DedupEngine.Decision.Insert -> {
                                // 7. Account resolution
                                val accountId = classification.accountLast4?.let {
                                    accountRegistry.findOrCreate(it, classification.bankHint)
                                } ?: 1L

                                // 8. Categorization via User DB Rules -> VPA Grammar -> Context Prior
                                val categoryMatch = categoryEngine.getCategory(
                                    rawMerchant = classification.merchant,
                                    timestamp = raw.timestamp,
                                    amount = amount
                                )
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

                                // Strict Human-in-the-Loop Gate:
                                // Both transaction confidence (PolicyEngine) AND category confidence must be >= 0.85
                                // to bypass the Review Inbox and auto-log directly as CONFIRMED.
                                val isCategoryKnownOrConfirmed = categoryMatch.confidence >= 0.85f && categoryMatch.category != "Unknown"
                                val isPolicyAutoLog = action == PolicyEngine.Action.AUTO_LOG

                                val status = if (isPolicyAutoLog && isCategoryKnownOrConfirmed) {
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

                                val anomaly = fraudDetector.checkAnomaly(
                                    merchant = tx.merchant,
                                    amount = tx.amount,
                                    timestamp = tx.timestamp
                                )
                                var finalStatus = status
                                if (anomaly.isAnomalous) {
                                    val note = formatAnomalyNote(anomaly.score, anomaly.median, anomaly.mad)
                                    transactionRepo.updateNotes(insertedId, note)
                                    if (finalStatus == TransactionStatus.CONFIRMED) {
                                        transactionRepo.updateStatus(insertedId, TransactionStatus.PENDING)
                                        finalStatus = TransactionStatus.PENDING
                                    }
                                    try {
                                        anomalyNotifier?.sendAnomalyAlert(insertedId, tx.amount, tx.merchant)
                                    } catch (_: Throwable) {
                                        // Non-blocking best-effort anomaly alert
                                    }
                                }

                                try {
                                    budgetAlertScheduler?.checkAndAlert(raw.timestamp)
                                } catch (_: Throwable) {
                                    // Non-blocking best-effort alert check
                                }

                                if (finalStatus == TransactionStatus.CONFIRMED) {
                                    if (recurringPatternRepo != null) {
                                        try {
                                            val totalCount = transactionRepo.getCount().firstOrNull() ?: 0L
                                            if (totalCount > 0 && totalCount % 10L == 0L) {
                                                val allTxs = transactionRepo.getAll().firstOrNull() ?: emptyList()
                                                val detected = com.cashbuddy.core.analytics.RecurrenceDetector.detect(allTxs, raw.timestamp)
                                                recurringPatternRepo.upsertAll(detected)
                                            }
                                        } catch (_: Throwable) {
                                            // Non-blocking recurrence detection
                                        }
                                    }
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
                                    } ?: existing.accountId

                                    val upgradedMerchant = dedupDecision.upgradedFields.merchant ?: existing.merchant
                                    val combinedRawText = if (existing.rawText.contains(fullText)) existing.rawText else "${existing.rawText}\n---\n$fullText"

                                    transactionRepo.update(existing.copy(
                                        accountId = upgradedAccount,
                                        merchant = upgradedMerchant,
                                        status = newStatus,
                                        confidence = maxOf(existing.confidence, classification.pTransaction.toFloat()),
                                        rawText = combinedRawText,
                                        updatedAt = raw.timestamp
                                    ))
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

    private fun format2Decimals(v: Double): String {
        val isNegative = v < 0
        val absVal = kotlin.math.abs(v)
        val scaled = kotlin.math.round(absVal * 100.0).toLong()
        val whole = scaled / 100
        val fraction = scaled % 100
        val prefix = if (isNegative) "-" else ""
        val fracStr = if (fraction < 10) "0$fraction" else "$fraction"
        return "$prefix$whole.$fracStr"
    }

    private fun formatAnomalyNote(score: Double, median: Double, mad: Double): String {
        return "anomaly_score=${format2Decimals(score)} median=${format2Decimals(median)} mad=${format2Decimals(mad)}"
    }
}
