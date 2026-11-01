// NO-NETWORK
package com.cashbuddy.data.repository

import com.cashbuddy.db.AppDatabase
import com.cashbuddy.domain.model.Transaction
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Atomic ingest write cluster for the notification pipeline (audit fix C1, docs/AUDIT_REPORT.md §C1).
 *
 * SQLDelight transactions are driver-level: opening one here makes the transaction `INSERT` and
 * the `raw_messages.resulting_tx_id` audit-link `UPDATE` a single commit. Previously a crash
 * (or process kill) between the two writes left a half-linked raw_message + transaction, which
 * was irreparable except via the duplicates dialog.
 *
 * IMPORTANT: the body must stay free of suspend calls and dispatcher hops — SQLDelight
 * transactions are thread-bound; queries here are executed directly and synchronously.
 * KEEP THE INSERT/UPDATE ARG LISTS IN SYNC WITH `TransactionRepositoryImpl.insert/update`
 * until the mapping is unified.
 */
class TransactionIngestStore(
    private val database: AppDatabase,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) {

    /**
     * Inserts [transaction] and links it to the already-stored raw_message [rawMessageId]
     * in ONE commit. @return the new transaction id.
     */
    suspend fun insertAndLink(rawMessageId: String, transaction: Transaction): Long = withContext(dispatcher) {
        database.transactionWithResult {
            database.transactionsQueries.insert(
                amount = transaction.amount,
                type = transaction.type.name,
                currency = transaction.currency,
                merchant = transaction.merchant,
                category_id = transaction.categoryId,
                account_id = transaction.accountId,
                source_app = transaction.sourceApp,
                raw_text = transaction.rawText,
                confidence = transaction.confidence.toDouble(),
                status = transaction.status.name,
                notes = transaction.notes,
                timestamp = transaction.timestamp,
                created_at = transaction.createdAt,
                updated_at = transaction.updatedAt,
                is_merged = if (transaction.isMerged) 1L else 0L,
                merged_into_id = transaction.mergedIntoId
            )
            val insertedId = database.transactionsQueries.lastInsertRowId().executeAsOne()
            // Arg order per raw_messages.sq `updateResultingTx`: (resulting_tx_id, id)
            database.raw_messagesQueries.updateResultingTx(insertedId.toString(), rawMessageId)
            insertedId
        }
    }
}
