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
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val maxRows: Long = 5000L
) : DebugLogRepository {

    constructor(database: AppDatabase) : this(database, Dispatchers.Default, 5000L)
    constructor(database: AppDatabase, maxRows: Long) : this(database, Dispatchers.Default, maxRows)

    private val queries = database.debugLogQueries

    override suspend fun insert(entry: DebugLogEntry): Unit = withContext(dispatcher) {
        database.transaction {
            queries.insert(
                id = entry.id,
                timestamp = entry.timestamp,
                source_type = entry.sourceType,
                package_name = entry.packageName,
                sender_id = entry.senderId,
                raw_title = entry.rawTitle,
                raw_text = entry.rawText,
                raw_text_hash = entry.rawTextHash,
                detected_source = entry.detectedSource,
                evidence_json = entry.evidenceJson,
                p_transaction = entry.pTransaction,
                contributions_json = entry.contributionsJson,
                field_confidences_json = entry.fieldConfidencesJson,
                policy_action = entry.policyAction,
                pipeline_outcome = entry.pipelineOutcome,
                resulting_tx_id = entry.resultingTxId,
                merge_target_id = entry.mergeTargetId,
                error_message = entry.errorMessage
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
            detected_source = detectedSource,
            evidence_json = evidenceJson,
            p_transaction = pTransaction,
            contributions_json = contributionsJson,
            field_confidences_json = fieldConfidencesJson,
            id = id
        )
    }

    override suspend fun updatePolicy(id: String, policyAction: String?): Unit = withContext(dispatcher) {
        queries.updatePolicy(
            policy_action = policyAction,
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
            value = policyAction,
            pipeline_outcome = pipelineOutcome,
            resulting_tx_id = resultingTxId,
            merge_target_id = mergeTargetId,
            error_message = errorMessage,
            id = id
        )
    }

    override suspend fun updateRawText(id: String, rawText: String): Unit = withContext(dispatcher) {
        queries.updateRawText(raw_text = rawText, id = id)
    }

    override suspend fun getById(id: String): DebugLogEntry? = withContext(dispatcher) {
        queries.getById(id).executeAsOneOrNull()?.toEntry()
    }

    override suspend fun countWithFilter(filter: DebugLogFilter): Long = withContext(dispatcher) {
        queries.countWithFilter(
            sourceType = filter.sourceType,
            policyAction = filter.policyAction,
            packageName = filter.packageName,
            startTime = filter.startTime,
            endTime = filter.endTime
        ).executeAsOne()
    }

    override suspend fun distinctPackages(): List<String> = withContext(dispatcher) {
        queries.distinctPackages().executeAsList()
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
        queries.recent(value_ = limit.toLong()).executeAsList().map { it.toEntry() }
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
