package com.cashbuddy.domain.repository

import com.cashbuddy.domain.model.DateRangeSummary
import com.cashbuddy.domain.model.Transaction
import com.cashbuddy.domain.model.TransactionStatus
import com.cashbuddy.domain.model.TransactionType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow

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
    /** Emits whenever the underlying transactions table is mutated (insert/update/delete/merge). */
    val transactionUpdates: Flow<Unit> get() = emptyFlow()

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
    suspend fun insertAll(transactions: List<Transaction>): List<Long> = transactions.map { insert(it) }
    suspend fun updateStatus(id: Long, status: TransactionStatus)
    suspend fun update(transaction: Transaction)
    suspend fun deleteById(id: Long)
    suspend fun deleteAll() {}
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
    suspend fun sumByCategory(category: String, startTime: Long, endTime: Long): Double
    fun getByCategoryAndPeriod(category: String, startTime: Long, endTime: Long): Flow<List<Transaction>>
    suspend fun sumAll(startTime: Long, endTime: Long, type: TransactionType = TransactionType.DEBIT): Double = 0.0
    suspend fun sumActualSpent(startTime: Long, endTime: Long): Double = 0.0
    suspend fun sumCardOutstanding(startTime: Long, endTime: Long): Double = 0.0
    suspend fun sumLentOut(startTime: Long, endTime: Long): Double = 0.0
    suspend fun updatePaymentMethod(id: Long, paymentMethod: String) {}
    suspend fun updateLoanStatus(id: Long, isLoanGiven: Boolean) {}
    suspend fun getAmountsAtMerchant(merchant: String, fromTimestamp: Long, toTimestamp: Long): List<Double> = emptyList()
    suspend fun updateNotes(id: Long, notes: String) {}
    suspend fun findByUtr(utr: String): Transaction? = null
    suspend fun getHistoryPage(
        searchQuery: String? = null,
        type: String? = null,
        categoryId: Long? = null,
        startTime: Long? = null,
        endTime: Long? = null,
        minAmount: Double? = null,
        maxAmount: Double? = null,
        cursorTimestamp: Long? = null,
        cursorId: Long? = null,
        limit: Long = 50
    ): List<Transaction> = emptyList()
    suspend fun getHistorySummary(
        searchQuery: String? = null,
        type: String? = null,
        categoryId: Long? = null,
        startTime: Long? = null,
        endTime: Long? = null,
        minAmount: Double? = null,
        maxAmount: Double? = null
    ): HistorySummary = HistorySummary(0.0, 0.0, 0L)

    fun getHistorySummaryFlow(
        searchQuery: String? = null,
        type: String? = null,
        categoryId: Long? = null,
        startTime: Long? = null,
        endTime: Long? = null,
        minAmount: Double? = null,
        maxAmount: Double? = null
    ): Flow<HistorySummary> = flow {
        emit(
            getHistorySummary(
                searchQuery = searchQuery,
                type = type,
                categoryId = categoryId,
                startTime = startTime,
                endTime = endTime,
                minAmount = minAmount,
                maxAmount = maxAmount
            )
        )
    }

    fun getHistoryPageFlow(
        searchQuery: String? = null,
        type: String? = null,
        categoryId: Long? = null,
        startTime: Long? = null,
        endTime: Long? = null,
        minAmount: Double? = null,
        maxAmount: Double? = null,
        cursorTimestamp: Long? = null,
        cursorId: Long? = null,
        limit: Long = 50
    ): Flow<List<Transaction>> = flow {
        emit(
            getHistoryPage(
                searchQuery = searchQuery,
                type = type,
                categoryId = categoryId,
                startTime = startTime,
                endTime = endTime,
                minAmount = minAmount,
                maxAmount = maxAmount,
                cursorTimestamp = cursorTimestamp,
                cursorId = cursorId,
                limit = limit
            )
        )
    }
}

data class HistorySummary(
    val totalDebit: Double,
    val totalCredit: Double,
    val totalCount: Long
)

data class MergeLogEntry(
    val id: Long,
    val survivorId: Long,
    val mergedId: Long,
    val mergedAt: Long
)
