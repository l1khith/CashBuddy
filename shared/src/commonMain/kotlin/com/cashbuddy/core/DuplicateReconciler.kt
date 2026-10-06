// NO-NETWORK
package com.cashbuddy.core

import com.cashbuddy.domain.model.Transaction
import com.cashbuddy.domain.model.TransactionStatus
import com.cashbuddy.domain.model.TransactionType
import com.cashbuddy.domain.repository.AccountRepository
import com.cashbuddy.domain.repository.TransactionRepository
import com.cashbuddy.platform.currentTimeMillis
import kotlinx.coroutines.flow.firstOrNull
import kotlin.math.roundToLong

/**
 * Deterministic Duplicate Reconciler for PaisaPal / CashBuddy.
 * Retroactively identifies duplicate screenshot and payment entries,
 * deterministically picks the highest-fidelity survivor, merges duplicates,
 * preserves raw records, and reverts duplicate account ledger impacts.
 */
class DuplicateReconciler(
    private val transactionRepository: TransactionRepository,
    private val accountRepository: AccountRepository
) {

    data class DuplicateGroup(
        val fingerprint: String,
        val survivor: Transaction,
        val duplicates: List<Transaction>
    ) {
        val totalCount: Int get() = duplicates.size + 1
        val allTransactions: List<Transaction> get() = listOf(survivor) + duplicates
    }

    data class MergeSummary(
        val groupsMerged: Int,
        val totalDuplicatesMerged: Int,
        val survivorIds: List<Long>
    )

    private val utrRegex = Regex(
        """(?i)(?:UPI\s+(?:transaction\s+id|ref(?:\s+no|\s+id|\s+number)?)|Transaction\s+ID|UTR)[:\s]+([A-Za-z0-9]{8,35})"""
    )

    private val dateRegex = Regex(
        """(?i)\b([0-3]?[0-9]\s*(?:st|nd|rd|th)?\s+(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Sept|October|Nov|Dec)[a-z]*\s*,?\s*(?:20\d\d)?\s*(?:at|,)?\s*[0-1]?[0-9]:[0-5][0-9]\s*(?:am|pm)?)\b"""
    )

    private val ddmmyyyyRegex = Regex(
        """\b([0-3]?[0-9][\/\-][0-1]?[0-9][\/\-]20\d\d\s*(?:at|,)?\s*[0-1]?[0-9]:[0-5][0-9]\s*(?:am|pm)?)\b""",
        RegexOption.IGNORE_CASE
    )

    /**
     * Scans all active transactions in the database and groups duplicates together.
     */
    suspend fun findDuplicateGroups(): List<DuplicateGroup> {
        val candidates = transactionRepository.findDuplicateCandidates()
        return groupDuplicates(candidates)
    }

    /**
     * Pure grouping function for testability and offline evaluation.
     */
    fun groupDuplicates(transactions: List<Transaction>): List<DuplicateGroup> {
        val groups = mutableMapOf<String, MutableList<Transaction>>()

        for (tx in transactions) {
            val fingerprint = computeFingerprint(tx)
            groups.getOrPut(fingerprint) { mutableListOf() }.add(tx)
        }

        return groups.values
            .filter { it.size > 1 }
            .map { list ->
                val survivor = pickSurvivor(list)
                val duplicates = list.filter { it.id != survivor.id }
                val fp = computeFingerprint(survivor)
                DuplicateGroup(
                    fingerprint = fp,
                    survivor = survivor,
                    duplicates = duplicates
                )
            }
            .sortedByDescending { it.survivor.timestamp }
    }

    /**
     * Computes a deterministic fingerprint for a transaction.
     * Uses UTR if available; otherwise uses Amount + Normalized Merchant + Receipt DateTime (or 5-min bucket).
     */
    fun computeFingerprint(tx: Transaction): String {
        val utr = extractUtr(tx.rawText)
        if (utr != null && utr.length >= 8) {
            return "utr:${utr.uppercase()}"
        }

        val amountCents = (tx.amount * 100.0).roundToLong()
        val normMerchant = normalizeMerchant(tx.merchant)
            .replace(Regex("[^a-z0-9]"), "")

        val receiptDate = extractReceiptDate(tx.rawText)
        return if (receiptDate != null) {
            "rec:$amountCents:$normMerchant:$receiptDate"
        } else {
            val bucket = tx.timestamp / 300_000L
            "win:$amountCents:$normMerchant:$bucket"
        }
    }

    /**
     * Deterministically picks a survivor:
     * 1. Highest confidence (p_transaction)
     * 2. Detailed layout / presence of UPI transaction ID or UTR
     * 3. Most recent created_at / timestamp
     */
    fun pickSurvivor(group: List<Transaction>): Transaction {
        return group.maxWithOrNull(
            compareBy<Transaction> { it.confidence }
                .thenBy { if (hasDetailedLayout(it)) 1 else 0 }
                .thenByDescending { it.createdAt }
                .thenByDescending { it.id }
        ) ?: group.first()
    }

    private fun hasDetailedLayout(tx: Transaction): Boolean {
        val text = tx.rawText
        return text.contains("UPI transaction ID", ignoreCase = true) ||
                text.contains("UTR", ignoreCase = true) ||
                text.contains("Banking name", ignoreCase = true) ||
                text.contains("Google transaction ID", ignoreCase = true) ||
                text.contains("Transaction ID", ignoreCase = true)
    }

    private fun extractUtr(text: String): String? {
        val match = utrRegex.find(text) ?: return null
        return match.groups[1]?.value?.trim()
    }

    private fun extractReceiptDate(text: String): String? {
        val match = dateRegex.find(text)
        if (match != null) {
            return normalizeDate(match.groupValues[1])
        }
        val dMatch = ddmmyyyyRegex.find(text)
        if (dMatch != null) {
            return normalizeDate(dMatch.groupValues[1])
        }
        return null
    }

    private fun normalizeDate(raw: String): String {
        return raw.lowercase()
            .replace("september", "sep")
            .replace("sept", "sep")
            .replace("october", "oct")
            .replace("november", "nov")
            .replace("december", "dec")
            .replace("january", "jan")
            .replace("february", "feb")
            .replace("march", "mar")
            .replace("april", "apr")
            .replace("june", "jun")
            .replace("july", "jul")
            .replace("august", "aug")
            .replace(" at ", "")
            .replace(Regex("[^a-z0-9]"), "")
    }

    /**
     * Executes merge on the given duplicate groups:
     * - Marks duplicate transactions as merged with survivor ID
     * - Records audit trail in merge_log table
     * - Reverts double-deducted account balances for confirmed transactions
     */
    suspend fun mergeDuplicateGroups(groups: List<DuplicateGroup>): MergeSummary {
        var mergedCount = 0
        val survivorIds = mutableListOf<Long>()
        val now = currentTimeMillis()

        for (group in groups) {
            val survivor = group.survivor
            survivorIds.add(survivor.id)

            for (dup in group.duplicates) {
                // 1. Mark merged in transactions table
                transactionRepository.markMerged(id = dup.id, survivorId = survivor.id)

                // 2. Insert audit log entry
                transactionRepository.insertMergeLog(
                    survivorId = survivor.id,
                    mergedId = dup.id,
                    timestamp = now
                )

                // 3. Revert duplicate balance impact if duplicate was confirmed or modified
                if (dup.accountId != null &&
                    (dup.status == TransactionStatus.CONFIRMED || dup.status == TransactionStatus.MODIFIED)
                ) {
                    val acc = accountRepository.getById(dup.accountId).firstOrNull()
                    if (acc != null) {
                        val delta = if (dup.type == TransactionType.DEBIT) dup.amount else -dup.amount
                        accountRepository.update(acc.copy(balance = acc.balance + delta))
                    }
                }
                mergedCount++
            }
        }

        return MergeSummary(
            groupsMerged = groups.size,
            totalDuplicatesMerged = mergedCount,
            survivorIds = survivorIds
        )
    }

    /**
     * Undoes merge for a specific survivor transaction:
     * - Restores merged records
     * - Reapplies account balance
     * - Clears merge_log records
     */
    suspend fun undoMergeForSurvivor(survivorId: Long): Int {
        val mergedList = transactionRepository.getMergedTransactions(survivorId)
        if (mergedList.isEmpty()) return 0

        for (dup in mergedList) {
            transactionRepository.unmarkMerged(dup.id)

            if (dup.accountId != null &&
                (dup.status == TransactionStatus.CONFIRMED || dup.status == TransactionStatus.MODIFIED)
            ) {
                val acc = accountRepository.getById(dup.accountId).firstOrNull()
                if (acc != null) {
                    val delta = if (dup.type == TransactionType.DEBIT) -dup.amount else dup.amount
                    accountRepository.update(acc.copy(balance = acc.balance + delta))
                }
            }
        }

        transactionRepository.deleteMergeLog(survivorId)
        return mergedList.size
    }

    /**
     * Undoes the most recent merge operation across all survivors in the last batch.
     */
    suspend fun undoLastMerge(): Int {
        val recentLogs = transactionRepository.getRecentMergeLogs()
        if (recentLogs.isEmpty()) return 0

        // Find the most recent timestamp batch (within 5 seconds of the latest merge entry)
        val latestTimestamp = recentLogs.first().mergedAt
        val batchSurvivorIds = recentLogs
            .filter { it.mergedAt >= latestTimestamp - 5000L }
            .map { it.survivorId }
            .distinct()

        var totalUndone = 0
        for (survivorId in batchSurvivorIds) {
            totalUndone += undoMergeForSurvivor(survivorId)
        }
        return totalUndone
    }
}
