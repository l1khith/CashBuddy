package com.cashbuddy.domain.usecase

import com.cashbuddy.domain.repository.AccountRepository
import com.cashbuddy.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.first

class ExportDataUseCase(
    private val transactionRepository: TransactionRepository,
    private val accountRepository: AccountRepository
) {
    suspend fun streamCsv(write: (String) -> Unit) {
        write("ID,Amount,Type,Currency,Merchant,Category,Account,Source,Confidence,Status,Timestamp,Notes\n")

        var cursorTimestamp: Long? = null
        var cursorId: Long? = null
        val chunkSize = 500L

        while (true) {
            val page = transactionRepository.getHistoryPage(
                cursorTimestamp = cursorTimestamp,
                cursorId = cursorId,
                limit = chunkSize
            )
            if (page.isEmpty()) break

            val sb = StringBuilder(page.size * 100)
            for (tx in page) {
                sb.append("${tx.id},${tx.amount},${tx.type},${tx.currency},\"${tx.merchant.replace("\"", "\"\"")}\",\"${tx.categoryName ?: ""}\",\"${tx.accountName ?: ""}\",${tx.sourceApp},${tx.confidence},${tx.status},${tx.timestamp},\"${tx.notes?.replace("\"", "\"\"") ?: ""}\"\n")
            }
            write(sb.toString())

            val last = page.last()
            cursorTimestamp = last.timestamp
            cursorId = last.id

            if (page.size < chunkSize) break
        }
    }

    suspend fun exportCsv(): String {
        val sb = StringBuilder()
        streamCsv { sb.append(it) }
        return sb.toString()
    }
}

