// NO-NETWORK
package com.cashbuddy.domain.model

data class DebugLogEntry(
    val id: String,
    val timestamp: Long,
    val sourceType: String,
    val packageName: String?,
    val senderId: String?,
    val rawTitle: String?,
    val rawText: String,
    val rawTextHash: String?,
    val detectedSource: String?,
    val evidenceJson: String?,
    val pTransaction: Double?,
    val contributionsJson: String?,
    val fieldConfidencesJson: String?,
    val policyAction: String?,
    val pipelineOutcome: String?,
    val resultingTxId: String?,
    val mergeTargetId: String?,
    val errorMessage: String?
)

data class DebugLogFilter(
    val sourceType: String? = null,
    val policyAction: String? = null,
    val packageName: String? = null,
    val startTime: Long? = null,
    val endTime: Long? = null,
    val outcome: String? = null
)
