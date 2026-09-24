package com.cashbuddy.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.cashbuddy.db.AppDatabase
import com.cashbuddy.domain.repository.TrustedSender
import com.cashbuddy.domain.repository.TrustedSenderRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class TrustedSenderRepositoryImpl(
    private val database: AppDatabase,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) : TrustedSenderRepository {

    private val queries = database.trusted_sendersQueries

    override suspend fun addSender(
        senderId: String,
        bankName: String?,
        learnedAt: Long
    ): Unit = withContext(dispatcher) {
        queries.insertSender(
            sender_id = senderId,
            bank_name = bankName,
            learned_at = learnedAt
        )
    }

    override fun getAllSenders(): Flow<List<TrustedSender>> {
        return queries.getAllSenders()
            .asFlow()
            .mapToList(dispatcher)
            .map { list ->
                list.map {
                    TrustedSender(
                        id = it.id,
                        senderId = it.sender_id,
                        bankName = it.bank_name,
                        learnedAt = it.learned_at
                    )
                }
            }
    }

    override suspend fun isTrusted(senderId: String): Boolean = withContext(dispatcher) {
        queries.isSenderTrusted(senderId).executeAsOne() > 0
    }

    override suspend fun removeSender(senderId: String): Unit = withContext(dispatcher) {
        queries.deleteSender(senderId)
    }
}
