package com.cashbuddy.domain.repository

import com.cashbuddy.platform.currentTimeMillis
import kotlinx.coroutines.flow.Flow

data class TrustedSender(
    val id: Long,
    val senderId: String,
    val bankName: String?,
    val learnedAt: Long
)

interface TrustedSenderRepository {
    suspend fun addSender(senderId: String, bankName: String?, learnedAt: Long = currentTimeMillis())
    fun getAllSenders(): Flow<List<TrustedSender>>
    suspend fun isTrusted(senderId: String): Boolean
    suspend fun removeSender(senderId: String)
}
