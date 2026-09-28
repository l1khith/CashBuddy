// NO-NETWORK
package com.cashbuddy.data.repository

import com.cashbuddy.db.AppDatabase
import com.cashbuddy.db.Debug_log
import com.cashbuddy.domain.model.DebugLogEntry
import com.cashbuddy.domain.model.DebugLogFilter
import com.cashbuddy.domain.repository.DebugLogRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DebugLogRepositoryImpl(
    private val database: AppDatabase,
    private val maxRows: Long = 5000L,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) : DebugLogRepository {

    private val queries = database.debug_logQueries

    override suspend fun insert(entry: DebugLogEntry): Unit = withContext(dispatcher) {
        database.transaction {
            queries.insert(
                id = entry.id,
                timestamp = entry.timestamp,
                sourceType = entry.sourceType,
                packageName = entry.packageName,
                senderId = entry.senderId,
                rawTitle = entry.rawTitle,
                rawText = entry.rawText,
                rawTextHash = entry.rawTextHash,
                detectedSource = entry.detectedSource,
                evidenceJson = entry.evidenceJson,
                pTransaction = entry.pTransaction,
                contributionsJson = entry.contributionsJson,
                fieldConfidencesJson = entry.fieldConfidencesJson,
                policyAction = entry.policyAction,
                pipelineOutcome = entry.pipelineOutcome,
                resultingTxId = entry.resultingTxId,
                mergeTargetId = entry.mergeTargetId,
                errorMessage = entry.errorMessage
            )
            val currentCount = queries.count().executeAsOne()
            if (currentCount > maxRows) {
                val excess = currentCount - maxRows
                queries.deleteOldest(excess)
            }
        }
    }

    override suspend fun updateClassification(
        id: String,
        detectedSource: String?,
        evidenceJson: String?,
        pTransaction: Double?,
        contributionsJson: String?,
        fieldConfidencesJson: String?
    ): Unit = withContext(dispatcher) {
        queries.updateClassification(
            detectedSource = detectedSource,
            evidenceJson = evidenceJson,
            pTransaction = pTransaction,
            contributionsJson = contributionsJson,
            fieldConfidencesJson = fieldConfidencesJson,
            id = id
        )
    }

    override suspend fun updatePolicy(id: String, policyAction: String?): Unit = withContext(dispatcher) {
        queries.updatePolicy(
            policyAction = policyAction,
            id = id
        )
    }

    override suspend fun updateOutcome(
        id: String,
        policyAction: String?,
        pipelineOutcome: String?,
        resultingTxId: String?,
        mergeTargetId: String?,
        errorMessage: String?
    ): Unit = withContext(dispatcher) {
        queries.updateOutcome(
            policyAction = policyAction,
            pipelineOutcome = pipelineOutcome,
            resultingTxId = resultingTxId,
            mergeTargetId = mergeTargetId,
            errorMessage = errorMessage,
            id = id
        )
    }

    override suspend fun updateRawText(id: String, rawText: String): Unit = withContext(dispatcher) {
        queries.updateRawText(rawText = rawText, id = id)
    }

    override suspend fun query(filter: DebugLogFilter): List<DebugLogEntry> = withContext(dispatcher) {
        val list = queries.filterLogs(
            sourceType = filter.sourceType,
            policyAction = filter.policyAction,
            packageName = filter.packageName,
            startTime = filter.startTime,
            endTime = filter.endTime
        ).executeAsList().map { it.toEntry() }

        if (filter.outcome != null) {
            list.filter { it.pipelineOutcome.equals(filter.outcome, ignoreCase = true) }
        } else {
            list
        }
    }

    override suspend fun recent(limit: Int): List<DebugLogEntry> = withContext(dispatcher) {
        queries.recent(limit = limit.toLong()).executeAsList().map { it.toEntry() }
    }

    override suspend fun clearAll(): Unit = withContext(dispatcher) {
        queries.clearAll()
    }

    override suspend fun count(): Long = withContext(dispatcher) {
        queries.count().executeAsOne()
    }

    private fun Debug_log.toEntry(): DebugLogEntry = DebugLogEntry(
        id = id,
        timestamp = timestamp,
        sourceType = source_type,
        packageName = package_name,
        senderId = sender_id,
        rawTitle = raw_title,
        rawText = raw_text,
        rawTextHash = raw_text_hash,
        detectedSource = detected_source,
        evidenceJson = evidence_json,
        pTransaction = p_transaction,
        contributionsJson = contributions_json,
        fieldConfidencesJson = field_confidences_json,
        policyAction = policy_action,
        pipelineOutcome = pipeline_outcome,
        resultingTxId = resulting_tx_id,
        mergeTargetId = merge_target_id,
        errorMessage = error_message
    )
}
