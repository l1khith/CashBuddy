// NO-NETWORK
package com.cashbuddy.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOne
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.cashbuddy.db.AppDatabase
import com.cashbuddy.domain.model.DateRangeSummary
import com.cashbuddy.domain.model.Transaction
import com.cashbuddy.domain.model.TransactionStatus
import com.cashbuddy.domain.model.TransactionType
import com.cashbuddy.domain.repository.CategoryBreakdown
import com.cashbuddy.domain.repository.MonthlySummary
import com.cashbuddy.domain.repository.TransactionRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext

class TransactionRepositoryImpl(
    private val db: AppDatabase,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) : TransactionRepository {

    private val queries = db.transactionsQueries

    override fun getAll(): Flow<List<Transaction>> =
        queries.getAll(::mapTransaction).asFlow().mapToList(dispatcher).distinctUntilChanged()

    override fun getById(id: Long): Flow<Transaction?> =
        queries.getById(id, ::mapTransaction).asFlow().mapToOneOrNull(dispatcher).distinctUntilChanged()

    override fun getPending(): Flow<List<Transaction>> =
        queries.getPending(::mapTransaction).asFlow().mapToList(dispatcher).distinctUntilChanged()

    override fun getRecent(limit: Long): Flow<List<Transaction>> =
        queries.getRecent(limit, ::mapTransaction).asFlow().mapToList(dispatcher).distinctUntilChanged()

    override fun getByDateRange(start: Long, end: Long): Flow<List<Transaction>> =
        queries.getByDateRange(start, end, ::mapTransaction).asFlow().mapToList(dispatcher).distinctUntilChanged()

    override fun getByDateRangeWithLimit(start: Long, end: Long, limit: Long): Flow<List<Transaction>> =
        queries.getByDateRangeWithLimit(start, end, limit, ::mapTransaction).asFlow().mapToList(dispatcher).distinctUntilChanged()

    override fun getSummaryByDateRange(start: Long, end: Long): Flow<DateRangeSummary> =
        queries.getSummaryByDateRange(start, end) { totalDebit, totalCredit, count ->
            DateRangeSummary(
                totalDebit = totalDebit,
                totalCredit = totalCredit,
                transactionCount = count
            )
        }.asFlow().mapToOne(dispatcher).distinctUntilChanged()

    override fun getByCategory(categoryId: Long): Flow<List<Transaction>> =
        queries.getByCategory(categoryId, ::mapTransaction).asFlow().mapToList(dispatcher).distinctUntilChanged()

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
            updated_at = transaction.updatedAt,
            is_merged = if (transaction.isMerged) 1L else 0L,
            merged_into_id = transaction.mergedIntoId
        )
        queries.lastInsertRowId().executeAsOne()
    }

    override suspend fun updateStatus(id: Long, status: TransactionStatus): Unit = withContext(dispatcher) {
        queries.updateStatus(status = status.name, updated_at = com.cashbuddy.platform.currentTimeMillis(), id = id)
    }

    override suspend fun update(transaction: Transaction): Unit = withContext(dispatcher) {
        val oldTx = queries.getById(transaction.id, ::mapTransaction).executeAsOneOrNull()
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
        if (oldTx != null && (oldTx.status == TransactionStatus.CONFIRMED || oldTx.status == TransactionStatus.MODIFIED)
            && (transaction.status == TransactionStatus.CONFIRMED || transaction.status == TransactionStatus.MODIFIED)) {
            val oldEffect = if (oldTx.type == TransactionType.CREDIT) oldTx.amount else -oldTx.amount
            val newEffect = if (transaction.type == TransactionType.CREDIT) transaction.amount else -transaction.amount
            if (oldTx.accountId != null && oldTx.accountId == transaction.accountId) {
                val delta = newEffect - oldEffect
                if (delta != 0.0) {
                    val acc = db.accountsQueries.getById(oldTx.accountId).executeAsOneOrNull()
                    if (acc != null) {
                        db.accountsQueries.updateBalance(acc.balance + delta, com.cashbuddy.platform.currentTimeMillis(), acc.id)
                    }
                }
            } else {
                if (oldTx.accountId != null) {
                    val oldAcc = db.accountsQueries.getById(oldTx.accountId).executeAsOneOrNull()
                    if (oldAcc != null) {
                        db.accountsQueries.updateBalance(oldAcc.balance - oldEffect, com.cashbuddy.platform.currentTimeMillis(), oldAcc.id)
                    }
                }
                if (transaction.accountId != null) {
                    val newAcc = db.accountsQueries.getById(transaction.accountId).executeAsOneOrNull()
                    if (newAcc != null) {
                        db.accountsQueries.updateBalance(newAcc.balance + newEffect, com.cashbuddy.platform.currentTimeMillis(), newAcc.id)
                    }
                }
            }
        }
    }

    override suspend fun deleteById(id: Long): Unit = withContext(dispatcher) {
        queries.deleteById(id)
    }

    override fun getBalance(): Flow<Double> =
        queries.getBalance().asFlow().mapToOne(dispatcher)

    override fun getCount(): Flow<Long> =
        queries.getCount().asFlow().mapToOne(dispatcher).distinctUntilChanged()

    override fun getAverageAmount(): Flow<Double> =
        queries.getAll(::mapTransaction).asFlow().mapToList(dispatcher).let { flow ->
            kotlinx.coroutines.flow.flow {
                flow.collect { list ->
                    val avg = if (list.isEmpty()) 0.0 else list.map { it.amount }.average()
                    emit(avg)
                }
            }
        }

    override suspend fun findDuplicateCandidates(): List<Transaction> = withContext(dispatcher) {
        queries.findDuplicateCandidates(::mapTransaction).executeAsList()
    }

    override suspend fun markMerged(id: Long, survivorId: Long): Unit = withContext(dispatcher) {
        queries.markMerged(merged_into_id = survivorId, updated_at = com.cashbuddy.platform.currentTimeMillis(), id = id)
    }

    override suspend fun unmarkMerged(id: Long): Unit = withContext(dispatcher) {
        queries.unmarkMerged(updated_at = com.cashbuddy.platform.currentTimeMillis(), id = id)
    }

    override suspend fun insertMergeLog(survivorId: Long, mergedId: Long, timestamp: Long): Unit = withContext(dispatcher) {
        queries.insertMergeLog(survivor_id = survivorId, merged_id = mergedId, merged_at = timestamp)
    }

    override suspend fun getMergedTransactions(survivorId: Long): List<Transaction> = withContext(dispatcher) {
        queries.getMergedRecordsForSurvivor(survivorId, ::mapTransaction).executeAsList()
    }

    override suspend fun deleteMergeLog(survivorId: Long): Unit = withContext(dispatcher) {
        queries.deleteMergeLogForSurvivor(survivorId)
    }

    override suspend fun getRecentMergeLogs(): List<com.cashbuddy.domain.repository.MergeLogEntry> = withContext(dispatcher) {
        queries.getRecentMergeLogs().executeAsList().map {
            com.cashbuddy.domain.repository.MergeLogEntry(
                id = it.id,
                survivorId = it.survivor_id,
                mergedId = it.merged_id,
                mergedAt = it.merged_at
            )
        }
    }

    override suspend fun getMergeLogCount(): Long = withContext(dispatcher) {
        queries.getMergeLogCount().executeAsOne()
    }

    override suspend fun sumByCategory(category: String, startTime: Long, endTime: Long): Double = withContext(dispatcher) {
        queries.sumByCategory(
            category = category,
            startTime = startTime,
            endTime = endTime
        ).executeAsOne()
    }

    override suspend fun sumAll(startTime: Long, endTime: Long, type: TransactionType): Double = withContext(dispatcher) {
        queries.sumAll(
            type = type.name,
            startTime = startTime,
            endTime = endTime
        ).executeAsOne()
    }

    override fun getByCategoryAndPeriod(category: String, startTime: Long, endTime: Long): Flow<List<Transaction>> =
        queries.getByCategoryAndPeriod(
            category = category,
            startTime = startTime,
            endTime = endTime,
            mapper = ::mapTransaction
        ).asFlow().mapToList(dispatcher).distinctUntilChanged()

    override suspend fun getAmountsAtMerchant(
        merchant: String,
        fromTimestamp: Long,
        toTimestamp: Long
    ): List<Double> = withContext(dispatcher) {
        queries.getAmountsAtMerchant(
            merchant = merchant,
            fromTimestamp = fromTimestamp,
            toTimestamp = toTimestamp
        ).executeAsList()
    }

    override suspend fun updateNotes(id: Long, notes: String): Unit = withContext(dispatcher) {
        val now = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
        queries.updateNotes(notes = notes, updatedAt = now, id = id)
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
        isMerged: Long,
        mergedIntoId: Long?,
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
            isMerged = isMerged == 1L,
            mergedIntoId = mergedIntoId,
            categoryName = categoryName,
            categoryColor = categoryColor,
            categoryIcon = categoryIcon,
            accountName = accountName,
            accountType = accountType
        )
    }
}
