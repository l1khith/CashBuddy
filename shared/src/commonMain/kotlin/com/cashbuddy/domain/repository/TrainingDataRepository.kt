package com.cashbuddy.domain.repository

import com.cashbuddy.domain.model.RawTrainingData
import com.cashbuddy.domain.model.TrainingStats
import com.cashbuddy.domain.model.UserCorrection
import kotlinx.coroutines.flow.Flow

interface TrainingDataRepository {
    suspend fun recordRawNotification(
        rawText: String,
        source: String = "notification",
        sourceApp: String? = null,
        extractedAmount: Double? = null,
        extractedType: String? = null,
        extractedMerchant: String? = null,
        timestamp: Long
    ): Long

    suspend fun recordCorrection(
        merchant: String,
        sourceApp: String? = null,
        rawText: String? = null,
        oldCategory: String? = null,
        newCategory: String,
        timestamp: Long
    ): Long

    fun getAllRaw(): Flow<List<RawTrainingData>>
    fun getAllCorrections(): Flow<List<UserCorrection>>
    fun getStats(): Flow<TrainingStats>
    suspend fun exportTrainingDataJsonl(): String
    suspend fun clearTrainingData()
}
