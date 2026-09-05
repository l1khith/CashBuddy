package com.cashbuddy.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOne
import com.cashbuddy.db.AppDatabase
import com.cashbuddy.domain.model.RawTrainingData
import com.cashbuddy.domain.model.TrainingStats
import com.cashbuddy.domain.model.UserCorrection
import com.cashbuddy.domain.repository.TrainingDataRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext

class TrainingDataRepositoryImpl(
    private val db: AppDatabase,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) : TrainingDataRepository {

    private val queries = db.training_dataQueries

    override suspend fun recordRawNotification(
        rawText: String,
        source: String,
        sourceApp: String?,
        extractedAmount: Double?,
        extractedType: String?,
        extractedMerchant: String?,
        timestamp: Long
    ): Long = withContext(dispatcher) {
        queries.insertRaw(
            raw_text = rawText,
            source = source,
            source_app = sourceApp,
            extracted_amount = extractedAmount,
            extracted_type = extractedType,
            extracted_merchant = extractedMerchant,
            timestamp = timestamp
        )
        db.transactionsQueries.lastInsertRowId().executeAsOne()
    }

    override suspend fun recordCorrection(
        merchant: String,
        sourceApp: String?,
        rawText: String?,
        oldCategory: String?,
        newCategory: String,
        timestamp: Long
    ): Long = withContext(dispatcher) {
        queries.insertCorrection(
            merchant = merchant,
            source_app = sourceApp,
            raw_text = rawText,
            old_category = oldCategory,
            new_category = newCategory,
            timestamp = timestamp
        )
        db.transactionsQueries.lastInsertRowId().executeAsOne()
    }

    override fun getAllRaw(): Flow<List<RawTrainingData>> =
        queries.getAllRaw(::mapRaw).asFlow().mapToList(dispatcher)

    override fun getAllCorrections(): Flow<List<UserCorrection>> =
        queries.getAllCorrections(::mapCorrection).asFlow().mapToList(dispatcher)

    override fun getStats(): Flow<TrainingStats> {
        val rawCountFlow = queries.getRawCount().asFlow().mapToOne(dispatcher)
        val correctionsCountFlow = queries.getCorrectionCount().asFlow().mapToOne(dispatcher)
        return combine(rawCountFlow, correctionsCountFlow) { raw, corr ->
            TrainingStats(rawCount = raw, correctionsCount = corr)
        }
    }

    override suspend fun exportTrainingDataJsonl(): String = withContext(dispatcher) {
        val corrections = queries.getAllCorrections(::mapCorrection).executeAsList()
        val rawSamples = queries.getAllRaw(::mapRaw).executeAsList()

        val sb = StringBuilder()

        // 1. Export User Corrections (High Value Ground Truth)
        for (c in corrections) {
            val escapedMerchant = escapeJson(c.merchant)
            val escapedSource = escapeJson(c.sourceApp ?: "")
            val escapedText = escapeJson(c.rawText ?: "")
            val escapedOld = escapeJson(c.oldCategory ?: "")
            val escapedLabel = escapeJson(c.newCategory)

            sb.append("{\"type\":\"user_correction\",\"merchant\":\"$escapedMerchant\",\"source_app\":\"$escapedSource\",\"raw_text\":\"$escapedText\",\"old_category\":\"$escapedOld\",\"label\":\"$escapedLabel\",\"timestamp\":${c.timestamp}}\n")
        }

        // 2. Export Raw Ingested Notifications (For DistilBERT / Parser Retraining)
        for (r in rawSamples) {
            val escapedText = escapeJson(r.rawText)
            val escapedSource = escapeJson(r.source)
            val escapedApp = escapeJson(r.sourceApp ?: "")
            val escapedMerchant = escapeJson(r.extractedMerchant ?: "")
            val escapedType = escapeJson(r.extractedType ?: "")
            val amountStr = r.extractedAmount?.toString() ?: "null"

            sb.append("{\"type\":\"raw_notification\",\"raw_text\":\"$escapedText\",\"source\":\"$escapedSource\",\"source_app\":\"$escapedApp\",\"extracted_merchant\":\"$escapedMerchant\",\"extracted_amount\":$amountStr,\"extracted_type\":\"$escapedType\",\"timestamp\":${r.timestamp}}\n")
        }

        sb.toString()
    }

    override suspend fun clearTrainingData(): Unit = withContext(dispatcher) {
        queries.clearCorrections()
        queries.clearRaw()
    }

    private fun escapeJson(value: String): String {
        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\b", "\\b")
            .replace("\u000c", "\\f")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }

    private fun mapRaw(
        id: Long,
        rawText: String,
        source: String,
        sourceApp: String?,
        extractedAmount: Double?,
        extractedType: String?,
        extractedMerchant: String?,
        timestamp: Long
    ): RawTrainingData {
        return RawTrainingData(
            id = id,
            rawText = rawText,
            source = source,
            sourceApp = sourceApp,
            extractedAmount = extractedAmount,
            extractedType = extractedType,
            extractedMerchant = extractedMerchant,
            timestamp = timestamp
        )
    }

    private fun mapCorrection(
        id: Long,
        merchant: String,
        sourceApp: String?,
        rawText: String?,
        oldCategory: String?,
        newCategory: String,
        timestamp: Long
    ): UserCorrection {
        return UserCorrection(
            id = id,
            merchant = merchant,
            sourceApp = sourceApp,
            rawText = rawText,
            oldCategory = oldCategory,
            newCategory = newCategory,
            timestamp = timestamp
        )
    }
}
