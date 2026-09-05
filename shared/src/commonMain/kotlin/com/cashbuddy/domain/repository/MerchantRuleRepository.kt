package com.cashbuddy.domain.repository

import com.cashbuddy.domain.model.MerchantRule
import kotlinx.coroutines.flow.Flow

interface MerchantRuleRepository {
    fun getAll(): Flow<List<MerchantRule>>
    suspend fun findMatch(merchant: String): MerchantRule?
    suspend fun learnRule(merchant: String, categoryId: Long, priority: Long = 100L)
    suspend fun deleteById(id: Long)
}
