// NO-NETWORK
package com.cashbuddy.data.repository

import com.cashbuddy.core.prob.RawMessage
import com.cashbuddy.core.prob.SourceType
import com.cashbuddy.db.AppDatabase
import com.cashbuddy.domain.repository.RawMessageRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class RawMessageRepositoryImpl(
    private val database: AppDatabase,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) : RawMessageRepository {

    private val queries = database.raw_messagesQueries

    override suspend fun insert(rawMessage: RawMessage): Unit = withContext(dispatcher) {
        queries.insert(
            id = rawMessage.id,
            source_type = rawMessage.sourceType.name,
            package_name = rawMessage.packageName,
            sender_id = rawMessage.senderId,
            title = rawMessage.title,
            text = rawMessage.text,
            timestamp = rawMessage.timestamp,
            processed_at = rawMessage.timestamp,
            resulting_tx_id = null
        )
    }

    override suspend fun getRecent(limit: Long): List<RawMessage> = withContext(dispatcher) {
        queries.getRecent(limit).executeAsList().map {
            RawMessage(
                id = it.id,
                sourceType = try { SourceType.valueOf(it.source_type) } catch (_: Exception) { SourceType.NOTIFICATION },
                packageName = it.package_name,
                senderId = it.sender_id,
                title = it.title ?: "",
                text = it.text,
                timestamp = it.timestamp,
                imagePath = null
            )
        }
    }

    override suspend fun getById(id: String): RawMessage? = withContext(dispatcher) {
        queries.getById(id).executeAsOneOrNull()?.let {
            RawMessage(
                id = it.id,
                sourceType = try { SourceType.valueOf(it.source_type) } catch (_: Exception) { SourceType.NOTIFICATION },
                packageName = it.package_name,
                senderId = it.sender_id,
                title = it.title ?: "",
                text = it.text,
                timestamp = it.timestamp,
                imagePath = null
            )
        }
    }

    override suspend fun updateResultingTx(rawMessageId: String, txId: String): Unit = withContext(dispatcher) {
        queries.updateResultingTx(txId, rawMessageId)
    }
}
