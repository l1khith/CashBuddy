// NO-NETWORK
package com.cashbuddy.data.repository

import com.cashbuddy.db.AppDatabase
import com.cashbuddy.domain.repository.MergeOperation
import com.cashbuddy.domain.repository.MergeTransactionStore
import com.cashbuddy.domain.repository.UndoMergeOperation
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Atomic duplicate-merge write cluster (audit fix B1/B2, DuplicateReconciler).
 *
 * `markMerged` does not touch `status`, so the balance triggers in transactions.sq
 * do not fire for it — the manual `applyBalanceDelta` here is the only balance effect
 * of a merge, and it now commits in the SAME driver-level transaction as the merge
 * itself. A crash mid-batch rolls the whole batch back instead of leaving ghost rows
 * or double-counted balances.
 *
 * IMPORTANT: the transaction body must stay free of suspend calls and dispatcher hops —
 * SQLDelight transactions are thread-bound; queries here run directly and synchronously.
 */
class SqlDelightMergeStore(
    private val database: AppDatabase,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) : MergeTransactionStore {

    override suspend fun applyMerges(operations: List<MergeOperation>, mergedAt: Long): Unit =
        withContext(dispatcher) {
            if (operations.isEmpty()) return@withContext
            database.transaction {
                for (op in operations) {
                    // Arg order per transactions.sq `markMerged`: (merged_into_id, updated_at, id)
                    database.transactionsQueries.markMerged(
                        merged_into_id = op.survivorId,
                        updated_at = mergedAt,
                        id = op.duplicateId
                    )
                    database.transactionsQueries.insertMergeLog(
                        survivor_id = op.survivorId,
                        merged_id = op.duplicateId,
                        merged_at = mergedAt
                    )
                    if (op.balanceAccountId != null && op.balanceDelta != 0.0) {
                        database.accountsQueries.applyBalanceDelta(
                            delta = op.balanceDelta,
                            updatedAt = mergedAt,
                            id = op.balanceAccountId
                        )
                    }
                }
            }
        }

    override suspend fun undoMerges(
        survivorIds: List<Long>,
        operations: List<UndoMergeOperation>,
        updatedAt: Long
    ): Unit = withContext(dispatcher) {
        if (survivorIds.isEmpty() && operations.isEmpty()) return@withContext
        database.transaction {
            for (op in operations) {
                // Arg order per transactions.sq `unmarkMerged`: (updated_at, id)
                database.transactionsQueries.unmarkMerged(
                    updated_at = updatedAt,
                    id = op.duplicateId
                )
                if (op.balanceAccountId != null && op.balanceDelta != 0.0) {
                    database.accountsQueries.applyBalanceDelta(
                        delta = op.balanceDelta,
                        updatedAt = updatedAt,
                        id = op.balanceAccountId
                    )
                }
            }
            for (survivorId in survivorIds) {
                database.transactionsQueries.deleteMergeLogForSurvivor(survivor_id = survivorId)
            }
        }
    }
}
