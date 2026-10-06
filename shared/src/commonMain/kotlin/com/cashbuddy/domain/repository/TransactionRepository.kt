package com.cashbuddy.domain.repository

import com.cashbuddy.domain.model.DateRangeSummary
import com.cashbuddy.domain.model.Transaction
import com.cashbuddy.domain.model.TransactionStatus
import kotlinx.coroutines.flow.Flow

data class MonthlySummary(
    val month: String,
    val totalDebit: Double,
    val totalCredit: Double,
    val transactionCount: Long
)

data class CategoryBreakdown(
    val categoryName: String,
    val categoryColor: String,
    val categoryIcon: String,
    val totalAmount: Double,
    val transactionCount: Long,
    val averageAmount: Double
)

interface TransactionRepository {
    fun getAll(): Flow<List<Transaction>>
    fun getById(id: Long): Flow<Transaction?>
    fun getPending(): Flow<List<Transaction>>
    fun getRecent(limit: Long = 15): Flow<List<Transaction>>
    fun getByDateRange(start: Long, end: Long): Flow<List<Transaction>>
    fun getByDateRangeWithLimit(start: Long, end: Long, limit: Long): Flow<List<Transaction>>
    fun getSummaryByDateRange(start: Long, end: Long): Flow<DateRangeSummary>
    fun getByCategory(categoryId: Long): Flow<List<Transaction>>
    fun getMonthlySummary(): Flow<List<MonthlySummary>>
    fun getCategoryBreakdown(start: Long, end: Long): Flow<List<CategoryBreakdown>>
    suspend fun insert(transaction: Transaction): Long
    suspend fun updateStatus(id: Long, status: TransactionStatus)
    suspend fun update(transaction: Transaction)
    suspend fun deleteById(id: Long)
    fun getBalance(): Flow<Double>
    fun getAverageAmount(): Flow<Double>
    fun getCount(): Flow<Long>
    suspend fun findDuplicateCandidates(): List<Transaction>
    suspend fun markMerged(id: Long, survivorId: Long)
    suspend fun unmarkMerged(id: Long)
    suspend fun insertMergeLog(survivorId: Long, mergedId: Long, timestamp: Long)
    suspend fun getMergedTransactions(survivorId: Long): List<Transaction>
    suspend fun deleteMergeLog(survivorId: Long)
    suspend fun getRecentMergeLogs(): List<MergeLogEntry>
    suspend fun getMergeLogCount(): Long
}

data class MergeLogEntry(
    val id: Long,
    val survivorId: Long,
    val mergedId: Long,
    val mergedAt: Long
)
