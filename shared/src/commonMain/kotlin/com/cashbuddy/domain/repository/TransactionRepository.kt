package com.cashbuddy.domain.repository

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
    fun getByDateRange(start: Long, end: Long): Flow<List<Transaction>>
    fun getByCategory(categoryId: Long): Flow<List<Transaction>>
    fun getMonthlySummary(): Flow<List<MonthlySummary>>
    fun getCategoryBreakdown(start: Long, end: Long): Flow<List<CategoryBreakdown>>
    suspend fun insert(transaction: Transaction): Long
    suspend fun updateStatus(id: Long, status: TransactionStatus)
    suspend fun update(transaction: Transaction)
    suspend fun deleteById(id: Long)
    fun getBalance(): Flow<Double>
    fun getAverageAmount(): Flow<Double>
}
