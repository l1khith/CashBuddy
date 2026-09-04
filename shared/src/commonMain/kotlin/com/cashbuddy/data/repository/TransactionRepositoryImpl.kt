package com.cashbuddy.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOne
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.cashbuddy.db.AppDatabase
import com.cashbuddy.domain.model.Transaction
import com.cashbuddy.domain.model.TransactionStatus
import com.cashbuddy.domain.model.TransactionType
import com.cashbuddy.domain.repository.CategoryBreakdown
import com.cashbuddy.domain.repository.MonthlySummary
import com.cashbuddy.domain.repository.TransactionRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class TransactionRepositoryImpl(
    private val db: AppDatabase,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) : TransactionRepository {

    private val queries = db.transactionsQueries

    override fun getAll(): Flow<List<Transaction>> =
        queries.getAll(::mapTransaction).asFlow().mapToList(dispatcher)

    override fun getById(id: Long): Flow<Transaction?> =
        queries.getById(id, ::mapTransaction).asFlow().mapToOneOrNull(dispatcher)

    override fun getPending(): Flow<List<Transaction>> =
        queries.getPending(::mapTransaction).asFlow().mapToList(dispatcher)

    override fun getByDateRange(start: Long, end: Long): Flow<List<Transaction>> =
        queries.getByDateRange(start, end) { id, amount, type, currency, merchant, categoryId, accountId, sourceApp, rawText, confidence, status, notes, timestamp, createdAt, updatedAt, categoryName, categoryColor ->
            Transaction(
                id = id,
                amount = amount,
                type = TransactionType.valueOf(type),
                currency = currency,
                merchant = merchant,
                categoryId = categoryId,
                accountId = accountId,
                sourceApp = sourceApp,
                rawText = rawText,
                confidence = confidence.toFloat(),
                status = TransactionStatus.valueOf(status),
                notes = notes,
                timestamp = timestamp,
                createdAt = createdAt,
                updatedAt = updatedAt,
                categoryName = categoryName,
                categoryColor = categoryColor
            )
        }.asFlow().mapToList(dispatcher)

    override fun getByCategory(categoryId: Long): Flow<List<Transaction>> =
        queries.getByCategory(categoryId) { id, amount, type, currency, merchant, categoryId, accountId, sourceApp, rawText, confidence, status, notes, timestamp, createdAt, updatedAt, categoryName, categoryColor ->
            Transaction(
                id = id,
                amount = amount,
                type = TransactionType.valueOf(type),
                currency = currency,
                merchant = merchant,
                categoryId = categoryId,
                accountId = accountId,
                sourceApp = sourceApp,
                rawText = rawText,
                confidence = confidence.toFloat(),
                status = TransactionStatus.valueOf(status),
                notes = notes,
                timestamp = timestamp,
                createdAt = createdAt,
                updatedAt = updatedAt,
                categoryName = categoryName,
                categoryColor = categoryColor
            )
        }.asFlow().mapToList(dispatcher)

    override fun getMonthlySummary(): Flow<List<MonthlySummary>> =
        queries.getMonthlySummary { month, totalDebit, totalCredit, transactionCount ->
            MonthlySummary(
                month = month ?: "",
                totalDebit = totalDebit ?: 0.0,
                totalCredit = totalCredit ?: 0.0,
                transactionCount = transactionCount
            )
        }.asFlow().mapToList(dispatcher)

    override fun getCategoryBreakdown(start: Long, end: Long): Flow<List<CategoryBreakdown>> =
        queries.getCategoryBreakdown(start, end) { categoryName, categoryColor, categoryIcon, totalAmount, transactionCount, averageAmount ->
            CategoryBreakdown(
                categoryName = categoryName,
                categoryColor = categoryColor,
                categoryIcon = categoryIcon,
                totalAmount = totalAmount ?: 0.0,
                transactionCount = transactionCount,
                averageAmount = averageAmount ?: 0.0
            )
        }.asFlow().mapToList(dispatcher)

    override suspend fun insert(transaction: Transaction): Long = withContext(dispatcher) {
        queries.insert(
            amount = transaction.amount,
            type = transaction.type.name,
            currency = transaction.currency,
            merchant = transaction.merchant,
            category_id = transaction.categoryId,
            account_id = transaction.accountId,
            source_app = transaction.sourceApp,
            raw_text = transaction.rawText,
            confidence = transaction.confidence.toDouble(),
            status = transaction.status.name,
            notes = transaction.notes,
            timestamp = transaction.timestamp,
            created_at = transaction.createdAt,
            updated_at = transaction.updatedAt
        )
        // Return rowid
        db.transactionsQueries.getBalance().executeAsOne() // verify trigger
        transaction.id
    }

    override suspend fun updateStatus(id: Long, status: TransactionStatus): Unit = withContext(dispatcher) {
        queries.updateStatus(status = status.name, updated_at = com.cashbuddy.platform.currentTimeMillis(), id = id)
    }

    override suspend fun update(transaction: Transaction): Unit = withContext(dispatcher) {
        queries.updateTransaction(
            amount = transaction.amount,
            type = transaction.type.name,
            merchant = transaction.merchant,
            category_id = transaction.categoryId,
            account_id = transaction.accountId,
            notes = transaction.notes,
            updated_at = com.cashbuddy.platform.currentTimeMillis(),
            id = transaction.id
        )
    }

    override suspend fun deleteById(id: Long): Unit = withContext(dispatcher) {
        queries.deleteById(id)
    }

    override fun getBalance(): Flow<Double> =
        queries.getBalance().asFlow().mapToOne(dispatcher)

    override fun getAverageAmount(): Flow<Double> =
        queries.getAll(::mapTransaction).asFlow().mapToList(dispatcher).let { flow ->
            kotlinx.coroutines.flow.flow {
                flow.collect { list ->
                    val avg = if (list.isEmpty()) 0.0 else list.map { it.amount }.average()
                    emit(avg)
                }
            }
        }

    private fun mapTransaction(
        id: Long,
        amount: Double,
        type: String,
        currency: String,
        merchant: String,
        categoryId: Long,
        accountId: Long?,
        sourceApp: String,
        rawText: String,
        confidence: Double,
        status: String,
        notes: String?,
        timestamp: Long,
        createdAt: Long,
        updatedAt: Long,
        categoryName: String?,
        categoryColor: String?,
        categoryIcon: String?,
        accountName: String?,
        accountType: String?
    ): Transaction {
        return Transaction(
            id = id,
            amount = amount,
            type = TransactionType.valueOf(type),
            currency = currency,
            merchant = merchant,
            categoryId = categoryId,
            accountId = accountId,
            sourceApp = sourceApp,
            rawText = rawText,
            confidence = confidence.toFloat(),
            status = TransactionStatus.valueOf(status),
            notes = notes,
            timestamp = timestamp,
            createdAt = createdAt,
            updatedAt = updatedAt,
            categoryName = categoryName,
            categoryColor = categoryColor,
            categoryIcon = categoryIcon,
            accountName = accountName,
            accountType = accountType
        )
    }
}
