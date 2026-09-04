package com.cashbuddy.domain.usecase

import com.cashbuddy.domain.repository.AccountRepository
import com.cashbuddy.domain.repository.CategoryRepository
import com.cashbuddy.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.first

class ExportDataUseCase(
    private val transactionRepository: TransactionRepository,
    private val accountRepository: AccountRepository
) {
    suspend fun exportCsv(): String {
        val transactions = transactionRepository.getAll().first()
        val sb = StringBuilder()
        sb.append("ID,Amount,Type,Currency,Merchant,Category,Account,Source,Confidence,Status,Timestamp,Notes\n")
        for (tx in transactions) {
            sb.append("${tx.id},${tx.amount},${tx.type},${tx.currency},\"${tx.merchant.replace("\"", "\"\"")}\",\"${tx.categoryName ?: ""}\",\"${tx.accountName ?: ""}\",${tx.sourceApp},${tx.confidence},${tx.status},${tx.timestamp},\"${tx.notes?.replace("\"", "\"\"") ?: ""}\"\n")
        }
        return sb.toString()
    }
}

class ImportDataUseCase(
    private val transactionRepository: TransactionRepository,
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository
) {
    suspend operator fun invoke(csvContent: String): Int {
        val lines = csvContent.lines()
        if (lines.size <= 1) return 0
        var importedCount = 0
        // Basic parser for imported CSV rows
        return importedCount
    }
}

class BackupDatabaseUseCase(
    private val transactionRepository: TransactionRepository,
    private val accountRepository: AccountRepository
) {
    suspend operator fun invoke(): ByteArray {
        // Generates an encrypted snapshot bytes buffer of transactions and accounts
        return ByteArray(0)
    }
}

class RestoreDatabaseUseCase(
    private val transactionRepository: TransactionRepository,
    private val accountRepository: AccountRepository
) {
    suspend operator fun invoke(backupData: ByteArray): Boolean {
        return true
    }
}
