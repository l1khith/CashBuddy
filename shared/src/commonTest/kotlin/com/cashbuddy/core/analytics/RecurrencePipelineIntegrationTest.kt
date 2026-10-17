// NO-NETWORK
package com.cashbuddy.core.analytics

import com.cashbuddy.core.CategoryEngine
import com.cashbuddy.core.prob.AccountRegistry
import com.cashbuddy.core.prob.DedupEngine
import com.cashbuddy.core.prob.EvidenceExtractor
import com.cashbuddy.core.prob.MessagePipeline
import com.cashbuddy.core.prob.NoOpCalibrator
import com.cashbuddy.core.prob.PipelineOutcome
import com.cashbuddy.core.prob.PolicyEngine
import com.cashbuddy.core.prob.ProbabilisticClassifier
import com.cashbuddy.core.prob.RawMessage
import com.cashbuddy.core.prob.SourceDetector
import com.cashbuddy.core.prob.SourceType
import com.cashbuddy.domain.model.Account
import com.cashbuddy.domain.model.AccountType
import com.cashbuddy.domain.model.Category
import com.cashbuddy.domain.model.CategoryType
import com.cashbuddy.domain.model.DateRangeSummary
import com.cashbuddy.domain.model.RecurringPattern
import com.cashbuddy.domain.model.Transaction
import com.cashbuddy.domain.model.TransactionStatus
import com.cashbuddy.domain.model.TransactionType
import com.cashbuddy.domain.repository.AccountRepository
import com.cashbuddy.domain.repository.CategoryBreakdown
import com.cashbuddy.domain.repository.CategoryRepository
import com.cashbuddy.domain.repository.MonthlySummary
import com.cashbuddy.domain.repository.RawMessageRepository
import com.cashbuddy.domain.repository.RecurringPatternRepository
import com.cashbuddy.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RecurrencePipelineIntegrationTest {

    private val baseMonday: Long = LocalDateTime(2026, 10, 5, 9, 0, 0, 0)
        .toInstant(TimeZone.UTC)
        .toEpochMilliseconds()

    private val oneDayMs: Long = 24L * 3600_000L

    private class InMemoryTransactionRepository : TransactionRepository {
        val txs = mutableListOf<Transaction>()
        override fun getAll(): Flow<List<Transaction>> = flowOf(txs)
        override fun getById(id: Long): Flow<Transaction?> = flowOf(txs.find { it.id == id })
        override fun getPending(): Flow<List<Transaction>> = flowOf(txs.filter { it.status == TransactionStatus.PENDING })
        override fun getRecent(limit: Long): Flow<List<Transaction>> = flowOf(txs.takeLast(limit.toInt()))
        override fun getByDateRange(start: Long, end: Long): Flow<List<Transaction>> = flowOf(txs.filter { it.timestamp in start..end })
        override fun getByDateRangeWithLimit(start: Long, end: Long, limit: Long): Flow<List<Transaction>> =
            flowOf(txs.filter { it.timestamp in start..end }.take(limit.toInt()))
        override fun getSummaryByDateRange(start: Long, end: Long): Flow<DateRangeSummary> = flowOf(DateRangeSummary(0.0, 0.0, 0L))
        override fun getByCategory(categoryId: Long): Flow<List<Transaction>> = flowOf(txs.filter { it.categoryId == categoryId })
        override fun getMonthlySummary(): Flow<List<MonthlySummary>> = flowOf(emptyList())
        override fun getCategoryBreakdown(start: Long, end: Long): Flow<List<CategoryBreakdown>> = flowOf(emptyList())
        override suspend fun insert(transaction: Transaction): Long {
            val id = (txs.size + 1).toLong()
            txs.add(transaction.copy(id = id))
            return id
        }
        override suspend fun update(transaction: Transaction) {}
        override suspend fun updateStatus(id: Long, status: TransactionStatus) {
            val idx = txs.indexOfFirst { it.id == id }
            if (idx != -1) txs[idx] = txs[idx].copy(status = status)
        }
        override suspend fun deleteById(id: Long) {}
        override fun getBalance(): Flow<Double> = flowOf(0.0)
        override fun getAverageAmount(): Flow<Double> = flowOf(0.0)
        override fun getCount(): Flow<Long> = flowOf(txs.size.toLong())
        override suspend fun findDuplicateCandidates(): List<Transaction> = emptyList()
        override suspend fun markMerged(id: Long, survivorId: Long) {}
        override suspend fun unmarkMerged(id: Long) {}
        override suspend fun insertMergeLog(survivorId: Long, mergedId: Long, timestamp: Long) {}
        override suspend fun getMergedTransactions(survivorId: Long): List<Transaction> = emptyList()
        override suspend fun deleteMergeLog(survivorId: Long) {}
        override suspend fun getRecentMergeLogs(): List<com.cashbuddy.domain.repository.MergeLogEntry> = emptyList()
        override suspend fun getMergeLogCount(): Long = 0L
        override suspend fun sumByCategory(category: String, startTime: Long, endTime: Long): Double = 0.0
        override fun getByCategoryAndPeriod(category: String, startTime: Long, endTime: Long): Flow<List<Transaction>> = flowOf(emptyList())
        override suspend fun getAmountsAtMerchant(merchant: String, fromTimestamp: Long, toTimestamp: Long): List<Double> {
            return txs.filter {
                it.merchant.trim().equals(merchant.trim(), ignoreCase = true) &&
                it.timestamp in fromTimestamp..toTimestamp
            }.map { it.amount }
        }
        override suspend fun updateNotes(id: Long, notes: String) {
            val idx = txs.indexOfFirst { it.id == id }
            if (idx != -1) txs[idx] = txs[idx].copy(notes = notes)
        }
    }

    private class InMemoryRecurringPatternRepository : RecurringPatternRepository {
        val patterns = mutableListOf<RecurringPattern>()
        override suspend fun upsert(pattern: RecurringPattern) {
            patterns.removeAll { it.merchantNormalized == pattern.merchantNormalized && it.amountPaise == pattern.amountPaise }
            patterns.add(pattern)
        }
        override suspend fun upsertAll(patterns: List<RecurringPattern>) {
            patterns.forEach { upsert(it) }
        }
        override fun getAll(): Flow<List<RecurringPattern>> = flowOf(patterns)
        override suspend fun findByMerchantAndAmount(merchant: String, amountPaise: Long): RecurringPattern? {
            val norm = merchant.trim().lowercase()
            return patterns.find { it.merchantNormalized == norm && it.amountPaise == amountPaise }
        }
        override suspend fun deleteById(id: Long) { patterns.removeAll { it.id == id } }
        override suspend fun deleteAll() { patterns.clear() }
        override suspend fun deleteStale(staleBefore: Long) { patterns.removeAll { it.lastUpdated < staleBefore } }
    }

    private class InMemoryRawMessageRepository : RawMessageRepository {
        val messages = mutableListOf<RawMessage>()
        override suspend fun insert(rawMessage: RawMessage) { messages.add(rawMessage) }
        override suspend fun getRecent(limit: Long): List<RawMessage> = messages.takeLast(limit.toInt())
        override suspend fun getById(id: String): RawMessage? = messages.find { it.id == id }
        override suspend fun updateResultingTx(rawMessageId: String, txId: String) {}
    }

    private class InMemoryCategoryRepository : CategoryRepository {
        val categories = listOf(Category(id = 1L, name = "Transport", type = CategoryType.EXPENSE, icon = "bus", color = "#0000FF", isDefault = true))
        override fun getAll(): Flow<List<Category>> = flowOf(categories)
        override fun getByType(type: CategoryType): Flow<List<Category>> = flowOf(categories.filter { it.type == type })
        override fun getById(id: Long): Flow<Category?> = flowOf(categories.find { it.id == id })
        override suspend fun seedDefaults(currentTimestamp: Long) {}
    }

    private class InMemoryAccountRepository : AccountRepository {
        val accounts = listOf(Account(id = 1L, name = "Primary Bank", type = AccountType.BANK, number = "1234", balance = 50000.0, createdAt = 0L, updatedAt = 0L))
        override fun getAll(): Flow<List<Account>> = flowOf(accounts)
        override fun getById(id: Long): Flow<Account?> = flowOf(accounts.find { it.id == id })
        override fun getByType(type: AccountType): Flow<List<Account>> = flowOf(accounts.filter { it.type == type })
        override suspend fun insert(account: Account): Long = 1L
        override suspend fun update(account: Account) {}
        override suspend fun updateBalance(id: Long, balance: Double) {}
        override suspend fun deleteById(id: Long) {}
        override suspend fun seedDefaults(currentTimestamp: Long) {}
        override fun getCount(): Flow<Long> = flowOf(accounts.size.toLong())
    }

    @Test
    fun testManualEndToEndRecurrenceAndAnomalyFlow() = runBlocking {
        val txRepo = InMemoryTransactionRepository()
        val patternRepo = InMemoryRecurringPatternRepository()
        val rawRepo = InMemoryRawMessageRepository()
        val catRepo = InMemoryCategoryRepository()
        val accountRepo = InMemoryAccountRepository()
        val accountRegistry = AccountRegistry(accountRepo)
        val categoryEngine = CategoryEngine()

        // 1. Insert 20 synthetic Mon-Fri BMTC transactions
        val historyTxs = mutableListOf<Transaction>()
        for (week in 0 until 4) {
            for (day in 0..4) { // Mon - Fri
                val t = baseMonday + (week * 7L + day) * oneDayMs
                val tx = Transaction(
                    id = (historyTxs.size + 1).toLong(),
                    accountId = 1L,
                    categoryId = 1L,
                    amount = 20.0,
                    type = TransactionType.DEBIT,
                    status = TransactionStatus.CONFIRMED,
                    rawText = "Rs 20.00 debited from A/c XX1234 to BMTC",
                    sourceApp = "com.bmtc",
                    merchant = "BMTC",
                    confidence = 1.0f,
                    timestamp = t,
                    createdAt = t,
                    updatedAt = t
                )
                historyTxs.add(tx)
                txRepo.insert(tx)
            }
        }

        // 2. Run detector & confirm merchant flagged recurring
        val now = historyTxs.last().timestamp + 3600_000L
        val detected = RecurrenceDetector.detect(historyTxs, now, TimeZone.UTC)
        assertEquals(1, detected.size)
        assertEquals("bmtc", detected.first().merchantNormalized)
        assertEquals(2000L, detected.first().amountPaise)
        assertEquals(20, detected.first().occurrenceCount)

        patternRepo.upsertAll(detected)

        // 3. Set up MessagePipeline with patternRepo
        val pipeline = MessagePipeline(
            sourceDetector = SourceDetector(),
            evidenceExtractor = EvidenceExtractor(),
            classifier = ProbabilisticClassifier(calibrator = NoOpCalibrator),
            policy = PolicyEngine(),
            dedup = DedupEngine,
            accountRegistry = accountRegistry,
            categoryEngine = categoryEngine,
            transactionRepo = txRepo,
            rawMessageRepo = rawRepo,
            categoryRepo = catRepo,
            recurringPatternRepo = patternRepo
        )

        // 4. Insert 1 new BMTC transaction on Tuesday (regular ₹20 ride)
        val tuesdayTimestamp = now + (2 * oneDayMs)
        val normalRaw = RawMessage(
            id = "bmtc-normal-1",
            sourceType = SourceType.NOTIFICATION,
            packageName = "com.sbi.upi",
            senderId = null,
            title = "Payment Alert",
            text = "Rs 20.00 debited from A/c XX1234 to BMTC",
            timestamp = tuesdayTimestamp
        )

        val normalOutcome = pipeline.ingest(normalRaw)
        assertTrue(normalOutcome is PipelineOutcome.Logged, "Normal recurring BMTC ride should bypass Review Inbox and be auto-logged")
        val loggedTx = txRepo.txs.last()
        assertEquals(TransactionStatus.CONFIRMED, loggedTx.status)

        // 5. Insert 1 new BMTC transaction of ₹12,500 (anomaly)
        val anomalyTimestamp = tuesdayTimestamp + 3600_000L
        val anomalyRaw = RawMessage(
            id = "bmtc-anomaly-1",
            sourceType = SourceType.NOTIFICATION,
            packageName = "com.sbi.upi",
            senderId = null,
            title = "Payment Alert",
            text = "Rs 12500.00 debited from A/c XX1234 to BMTC",
            timestamp = anomalyTimestamp
        )

        val anomalyOutcome = pipeline.ingest(anomalyRaw)
        assertTrue(anomalyOutcome is PipelineOutcome.PendingReview, "Anomalous ₹12,500 charge must go to Review Inbox")
        val anomalyTx = txRepo.txs.last()
        assertEquals(TransactionStatus.PENDING, anomalyTx.status)
        assertTrue(anomalyTx.notes?.contains("anomaly", ignoreCase = true) == true)
    }
}
