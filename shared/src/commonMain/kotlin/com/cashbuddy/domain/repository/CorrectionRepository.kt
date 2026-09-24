package com.cashbuddy.domain.repository

import com.cashbuddy.domain.model.UserCorrection
import com.cashbuddy.platform.currentTimeMillis
import kotlinx.coroutines.flow.Flow

interface CorrectionRepository {
    suspend fun recordCorrection(
        merchant: String,
        oldCategory: String? = null,
        newCategory: String,
        timestamp: Long = currentTimeMillis()
    ): Long

    fun getAllCorrections(): Flow<List<UserCorrection>>
    suspend fun getCorrectionCount(): Long
    suspend fun clearCorrections()
}
