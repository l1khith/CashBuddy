// NO-NETWORK
package com.cashbuddy.domain.repository

/**
 * Atomic write-side contract for duplicate merge bookkeeping.
 *
 * A merge spans three tables (transactions, merge_log, accounts). Executing those writes
 * as separate repository calls means each gets its own SQLDelight transaction, so a crash
 * mid-merge can leave a duplicate marked merged with no audit log (un-undoable ghost) or
 * with an unreverted balance. Implementations MUST commit every operation in a single
 * driver-level transaction so the batch is all-or-nothing.
 */
interface MergeTransactionStore {

    /**
     * Applies a batch of duplicate merges in ONE commit:
     * for each operation - mark the duplicate merged into its survivor, insert the
     * merge_log audit row, and (when applicable) revert the duplicate's account
     * balance impact.
     */
    suspend fun applyMerges(operations: List<MergeOperation>, mergedAt: Long)

    /**
     * Reverts a batch of merges in ONE commit: unmark the duplicates, restore their
     * account balance impact, and delete the merge_log rows for the given survivors.
     */
    suspend fun undoMerges(survivorIds: List<Long>, operations: List<UndoMergeOperation>, updatedAt: Long)
}

/**
 * One duplicate-merge write. [balanceDelta] is the balance correction for the duplicate's
 * account (non-zero only for confirmed/modified duplicates); 0.0 / null account means
 * "no balance write for this operation".
 */
data class MergeOperation(
    val duplicateId: Long,
    val survivorId: Long,
    val balanceAccountId: Long? = null,
    val balanceDelta: Double = 0.0
)

/** One merge-undo write, mirroring [MergeOperation] with the inverse balance delta. */
data class UndoMergeOperation(
    val duplicateId: Long,
    val balanceAccountId: Long? = null,
    val balanceDelta: Double = 0.0
)
