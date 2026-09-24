package com.cashbuddy.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.cashbuddy.db.AppDatabase
import com.cashbuddy.domain.model.UserCorrection
import com.cashbuddy.domain.repository.CorrectionRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class CorrectionRepositoryImpl(
    private val database: AppDatabase,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) : CorrectionRepository {

    private val queries = database.training_dataQueries

    override suspend fun recordCorrection(
        merchant: String,
        oldCategory: String?,
        newCategory: String,
        timestamp: Long
    ): Long = withContext(dispatcher) {
        queries.insertCorrection(
            merchant = merchant,
            source_app = "user_input",
            raw_text = "Correction: $merchant -> $newCategory",
            old_category = oldCategory,
            new_category = newCategory,
            timestamp = timestamp
        )
        database.transactionsQueries.lastInsertRowId().executeAsOne()
    }

    override fun getAllCorrections(): Flow<List<UserCorrection>> {
        return queries.getAllCorrections()
            .asFlow()
            .mapToList(dispatcher)
            .map { list ->
                list.map {
                    UserCorrection(
                        id = it.id,
                        merchant = it.merchant,
                        sourceApp = it.source_app,
                        rawText = it.raw_text,
                        oldCategory = it.old_category,
                        newCategory = it.new_category,
                        timestamp = it.timestamp
                    )
                }
            }
    }

    override suspend fun getCorrectionCount(): Long = withContext(dispatcher) {
        queries.getCorrectionCount().executeAsOne()
    }

    override suspend fun clearCorrections(): Unit = withContext(dispatcher) {
        queries.clearCorrections()
    }
}
