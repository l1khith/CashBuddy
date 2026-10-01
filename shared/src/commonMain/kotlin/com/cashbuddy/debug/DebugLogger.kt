// NO-NETWORK
package com.cashbuddy.debug

import com.cashbuddy.core.prob.Contribution
import com.cashbuddy.core.prob.Evidence
import com.cashbuddy.core.prob.FieldConfidences
import com.cashbuddy.core.prob.RawMessage
import com.cashbuddy.domain.model.DebugLogEntry
import com.cashbuddy.domain.repository.DebugLogRepository
import com.cashbuddy.platform.currentTimeMillis

class DebugLogger(
    private val repository: DebugLogRepository,
    private val config: DebugConfig
) {
    val enabled: Boolean
        get() = config.enabled

    suspend fun captureRaw(
        raw: RawMessage,
        sourceType: String = raw.sourceType.name,
        packageName: String? = raw.packageName,
        senderId: String? = raw.senderId
    ): String {
        if (!config.enabled || !config.isEnabled()) return ""

        val logId = "dbg_${raw.id}_${currentTimeMillis()}"
        val isRecording = config.isRecordingActive()

        // Privacy rule: redact body of non-financial messages unless recording mode is active
        val rawTextToStore = if (isRecording || isLikelyFinancial(raw.title, raw.text)) {
            raw.text
        } else {
            "[REDACTED - Non-financial message (Enable Recording in Debug Log to capture)]"
        }

        val entry = DebugLogEntry(
            id = logId,
            timestamp = raw.timestamp,
            sourceType = sourceType,
            packageName = packageName,
            senderId = senderId,
            rawTitle = raw.title,
            rawText = rawTextToStore,
            rawTextHash = raw.text.hashCode().toString(),
            detectedSource = null,
            evidenceJson = null,
            pTransaction = null,
            contributionsJson = null,
            fieldConfidencesJson = null,
            policyAction = null,
            pipelineOutcome = null,
            resultingTxId = null,
            mergeTargetId = null,
            errorMessage = null
        )

        try {
            repository.insert(entry)
        } catch (_: Throwable) {
            // Logger never crashes pipeline
        }
        return logId
    }

    suspend fun recordClassification(
        logId: String,
        detectedSource: String,
        evidence: Evidence,
        pTransaction: Double,
        contributions: List<Contribution>,
        fieldConfidences: FieldConfidences
    ) {
        if (!config.enabled || logId.isBlank()) return
        try {
            repository.updateClassification(
                id = logId,
                detectedSource = detectedSource,
                evidenceJson = evidence.toJson(),
                pTransaction = pTransaction,
                contributionsJson = Contribution.listToJson(contributions),
                fieldConfidencesJson = fieldConfidences.toJson()
            )
        } catch (_: Throwable) { }
    }

    suspend fun recordPolicy(logId: String, action: String) {
        if (!config.enabled || logId.isBlank()) return
        try {
            repository.updatePolicy(
                id = logId,
                policyAction = action
            )
        } catch (_: Throwable) { }
    }

    suspend fun recordOutcome(
        logId: String,
        outcome: String,
        txId: String?,
        mergeTarget: String?,
        error: String?
    ) {
        if (!config.enabled || logId.isBlank()) return
        try {
            repository.updateOutcome(
                id = logId,
                policyAction = null,
                pipelineOutcome = outcome,
                resultingTxId = txId,
                mergeTargetId = mergeTarget,
                errorMessage = error
            )
        } catch (_: Throwable) { }
    }

    private fun isLikelyFinancial(title: String, text: String): Boolean {
        val lower = "$title $text".lowercase()
        return lower.contains("₹") || lower.contains("rs.") || lower.contains("rs ") ||
               lower.contains("inr") || lower.contains("debited") || lower.contains("credited") ||
               lower.contains("spent") || lower.contains("paid") || lower.contains("a/c") ||
               lower.contains("acct") || lower.contains("upi") || lower.contains("bank") ||
               lower.contains("vpa") || lower.contains("sent") || lower.contains("received")
    }
}
