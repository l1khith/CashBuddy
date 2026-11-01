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

class ImportDataUseCase(
    private val transactionRepository: TransactionRepository,
    private val accountRepository: AccountRepository,
    private val categoryRepository: com.cashbuddy.domain.repository.CategoryRepository? = null
) {
    data class ImportResult(
        val totalProcessed: Int,
        val successCount: Int,
        val failedCount: Int
    )

    suspend fun importCsv(csvContent: String): ImportResult {
        var total = 0
        var success = 0
        var failed = 0

        val lines = csvContent.lineSequence()
        var isHeader = true

        val accounts = accountRepository.getAll().first()
        val defaultAccountId = accounts.firstOrNull()?.id ?: 1L
        val categories = categoryRepository?.getAll()?.first() ?: emptyList()
        val defaultCategoryId = categories.find { it.name.equals("Uncategorized", ignoreCase = true) }?.id
            ?: categories.firstOrNull()?.id
            ?: 15L

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isBlank()) continue
            if (isHeader) {
                isHeader = false
                // Check if line looks like header
                if (trimmed.contains("Amount", ignoreCase = true) || trimmed.contains("Merchant", ignoreCase = true)) {
                    continue
                }
            }

            total++
            try {
                val tokens = parseCsvLine(trimmed)
                if (tokens.size < 5) {
                    failed++
                    continue
                }

                // Header mapping: ID,Amount,Type,Currency,Merchant,Category,Account,Source,Confidence,Status,Timestamp,Notes
                // Or standard 5+ column format: Amount, Type, Currency, Merchant, Category, ...
                val amount: Double
                val type: com.cashbuddy.domain.model.TransactionType
                val currency: String
                val merchant: String
                val timestamp: Long
                val notes: String?

                if (tokens[0].toLongOrNull() != null && tokens.size >= 11) {
                    // ID present
                    amount = tokens[1].toDoubleOrNull() ?: throw IllegalArgumentException("Invalid amount")
                    type = try { com.cashbuddy.domain.model.TransactionType.valueOf(tokens[2].uppercase()) } catch (_: Exception) { com.cashbuddy.domain.model.TransactionType.DEBIT }
                    currency = tokens[3].ifBlank { "INR" }
                    merchant = tokens[4].ifBlank { "Manual Entry" }
                    timestamp = tokens[10].toLongOrNull() ?: com.cashbuddy.platform.currentTimeMillis()
                    notes = tokens.getOrNull(11)?.takeIf { it.isNotBlank() }
                } else {
                    amount = tokens[0].toDoubleOrNull() ?: throw IllegalArgumentException("Invalid amount")
                    type = try { com.cashbuddy.domain.model.TransactionType.valueOf(tokens[1].uppercase()) } catch (_: Exception) { com.cashbuddy.domain.model.TransactionType.DEBIT }
                    currency = tokens.getOrNull(2)?.ifBlank { "INR" } ?: "INR"
                    merchant = tokens.getOrNull(3)?.ifBlank { "Manual Entry" } ?: "Manual Entry"
                    timestamp = com.cashbuddy.platform.currentTimeMillis()
                    notes = tokens.getOrNull(4)
                }

                val now = com.cashbuddy.platform.currentTimeMillis()
                val tx = com.cashbuddy.domain.model.Transaction(
                    id = 0L,
                    amount = amount,
                    type = type,
                    currency = currency,
                    merchant = merchant,
                    categoryId = defaultCategoryId,
                    accountId = defaultAccountId,
                    sourceApp = "csv_import",
                    rawText = "CSV Import: $merchant",
                    confidence = 1.0f,
                    status = com.cashbuddy.domain.model.TransactionStatus.CONFIRMED,
                    notes = notes,
                    timestamp = timestamp,
                    createdAt = now,
                    updatedAt = now
                )

                transactionRepository.insert(tx)
                success++
            } catch (e: Exception) {
                failed++
            }
        }

        return ImportResult(totalProcessed = total, successCount = success, failedCount = failed)
    }

    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            if (c == '\"') {
                if (inQuotes && i + 1 < line.length && line[i + 1] == '\"') {
                    sb.append('\"')
                    i++
                } else {
                    inQuotes = !inQuotes
                }
            } else if (c == ',' && !inQuotes) {
                result.add(sb.toString().trim())
                sb.clear()
            } else {
                sb.append(c)
            }
            i++
        }
        result.add(sb.toString().trim())
        return result
    }
}


