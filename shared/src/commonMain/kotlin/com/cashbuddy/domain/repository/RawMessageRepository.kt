// NO-NETWORK
package com.cashbuddy.domain.repository

import com.cashbuddy.core.prob.RawMessage

interface RawMessageRepository {
    suspend fun insert(rawMessage: RawMessage)
    suspend fun getRecent(limit: Long = 100): List<RawMessage>
    suspend fun getById(id: String): RawMessage?
    suspend fun updateResultingTx(rawMessageId: String, txId: String)
}
