// NO-NETWORK
package com.cashbuddy.domain.repository

import com.cashbuddy.domain.model.DebugLogEntry
import com.cashbuddy.domain.model.DebugLogFilter

interface DebugLogRepository {
    suspend fun insert(entry: DebugLogEntry)
    suspend fun updateClassification(
        id: String,
        detectedSource: String?,
        evidenceJson: String?,
        pTransaction: Double?,
        contributionsJson: String?,
        fieldConfidencesJson: String?
    )
    suspend fun updatePolicy(id: String, policyAction: String?)
    suspend fun updateOutcome(
        id: String,
        policyAction: String?,
        pipelineOutcome: String?,
        resultingTxId: String?,
        mergeTargetId: String? = null,
        errorMessage: String?
    )

    /**
     * Calibrator feedback hook: returns the debug-log entry that produced the
     * transaction with the given id, so user confirm/reject decisions can be
     * attributed back to the classification's per-signal contributions.
     */
    suspend fun getByResultingTxId(txId: String): DebugLogEntry?
    suspend fun clearAll()
    suspend fun count(): Long
    suspend fun filterLogsPage(
        filter: DebugLogFilter,
        cursorTimestamp: Long?,
        cursorId: String?,
        limit: Long = 50L
    ): List<DebugLogEntry> = emptyList()
}
