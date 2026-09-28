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
    suspend fun updateRawText(id: String, rawText: String)
    suspend fun query(filter: DebugLogFilter): List<DebugLogEntry>
    suspend fun recent(limit: Int): List<DebugLogEntry>
    suspend fun clearAll()
    suspend fun count(): Long
}
