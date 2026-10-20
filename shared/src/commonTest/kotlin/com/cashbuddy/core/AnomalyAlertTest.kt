// NO-NETWORK
package com.cashbuddy.core

import com.cashbuddy.core.anomaly.AnomalyNotifier
import com.cashbuddy.core.prob.AccountRegistry
import com.cashbuddy.core.prob.DedupEngine
import com.cashbuddy.core.prob.EvidenceExtractor
import com.cashbuddy.core.prob.MessagePipeline
import com.cashbuddy.core.prob.NoOpCalibrator
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
import com.cashbuddy.domain.model.Transaction
import com.cashbuddy.domain.model.TransactionStatus
import com.cashbuddy.domain.model.TransactionType
import com.cashbuddy.domain.repository.AccountRepository
import com.cashbuddy.domain.repository.CategoryBreakdown
import com.cashbuddy.domain.repository.CategoryRepository
import com.cashbuddy.domain.repository.MonthlySummary
import com.cashbuddy.domain.repository.RawMessageRepository
import com.cashbuddy.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class AnomalyAlertTest {

    private class RecordingAnomalyNotifier : AnomalyNotifier {
        var alertedTxId: Long? = null
        var alertedAmount: Double? = null
        var alertedMerchant: String? = null

        override fun sendAnomalyAlert(transactionId: Long, amount: Double, merchant: String) {
            alertedTxId = transactionId
            alertedAmount = amount
            alertedMerchant = merchant
        }
    }

    private class TestTxRepo(private val amountsAtMerchant: List<Double>) : TransactionRepository {
        var lastInsertedTx: Transaction? = null
        var currentStatus: TransactionStatus? = null

        override fun getAll(): Flow<List<Transaction>> = flowOf(emptyList())
        override fun getById(id: Long): Flow<Transaction?> = flowOf(lastInsertedTx)
        override fun getPending(): Flow<List<Transaction>> = flowOf(emptyList())
        override fun getRecent(limit: Long): Flow<List<Transaction>> = flowOf(emptyList())
        override fun getByDateRange(start: Long, end: Long): Flow<List<Transaction>> = flowOf(emptyList())
        override fun getByDateRangeWithLimit(start: Long, end: Long, limit: Long): Flow<List<Transaction>> = flowOf(emptyList())
        override fun getSummaryByDateRange(start: Long, end: Long): Flow<DateRangeSummary> = flowOf(DateRangeSummary(0.0, 0.0, 0L))
        override fun getByCategory(categoryId: Long): Flow<List<Transaction>> = flowOf(emptyList())
        override fun getMonthlySummary(): Flow<List<MonthlySummary>> = flowOf(emptyList())
        override fun getCategoryBreakdown(start: Long, end: Long): Flow<List<CategoryBreakdown>> = flowOf(emptyList())
        override suspend fun insert(transaction: Transaction): Long {
            lastInsertedTx = transaction.copy(id = 999L)
            currentStatus = transaction.status
            return 999L
        }
        override suspend fun updateStatus(id: Long, status: TransactionStatus) {
            currentStatus = status
        }
        override suspend fun update(transaction: Transaction) {}
        override suspend fun deleteById(id: Long) {}
        override fun getBalance(): Flow<Double> = flowOf(0.0)
        override fun getAverageAmount(): Flow<Double> = flowOf(0.0)
        override fun getCount(): Flow<Long> = flowOf(1L)
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
        override suspend fun sumAll(startTime: Long, endTime: Long, type: TransactionType): Double = 0.0
        override suspend fun updateNotes(id: Long, notes: String) {}

        override suspend fun getAmountsAtMerchant(
            merchant: String,
            fromTimestamp: Long,
            toTimestamp: Long
        ): List<Double> = amountsAtMerchant
    }

    private class TestRawMessageRepo : RawMessageRepository {
        val messages = mutableListOf<RawMessage>()
        override suspend fun insert(message: RawMessage) { messages.add(message) }
        override suspend fun getById(id: String): RawMessage? = messages.find { it.id == id }
        override suspend fun getRecent(limit: Long): List<RawMessage> = messages.takeLast(limit.toInt())
        override suspend fun updateResultingTx(id: String, resultingTxId: String) {}
    }

    private class TestCategoryRepo : CategoryRepository {
        val categories = listOf(
            Category(id = 1L, name = "Food & Dining", type = CategoryType.EXPENSE, icon = "food", color = "#FF0000", isDefault = true),
            Category(id = 2L, name = "Shopping", type = CategoryType.EXPENSE, icon = "shopping", color = "#00FF00", isDefault = true)
        )
        override fun getAll(): Flow<List<Category>> = flowOf(categories)
        override fun getByType(type: CategoryType): Flow<List<Category>> = flowOf(categories.filter { it.type == type })
        override fun getById(id: Long): Flow<Category?> = flowOf(categories.find { it.id == id })
        override suspend fun seedDefaults(currentTimestamp: Long) {}
    }

    private class TestAccountRepo : AccountRepository {
        val accounts = listOf(
            Account(id = 1L, name = "Primary Bank", type = AccountType.BANK, number = "1234", balance = 50000.0, createdAt = 0L, updatedAt = 0L)
        )
        override fun getAll(): Flow<List<Account>> = flowOf(accounts)
        override fun getById(id: Long): Flow<Account?> = flowOf(accounts.find { it.id == id })
        override fun getByType(type: AccountType): Flow<List<Account>> = flowOf(accounts.filter { it.type == type })
        override suspend fun insert(account: Account): Long = 1L
        override suspend fun update(account: Account) {}
        override suspend fun updateBalance(id: Long, balance: Double) {}
        override suspend fun deleteById(id: Long) {}
        override fun getCount(): Flow<Long> = flowOf(accounts.size.toLong())
        override suspend fun seedDefaults(currentTimestamp: Long) {}
    }

    @Test
    fun testAnomalyFiresNotificationAlert() = runBlocking {
        // 12 historical transactions around ₹100
        val history = listOf(95.0, 100.0, 105.0, 100.0, 98.0, 102.0, 100.0, 101.0, 99.0, 100.0, 103.0, 97.0)
        val txRepo = TestTxRepo(history)
        val fraudDetector = FraudDetector(txRepo)
        val anomalyNotifier = RecordingAnomalyNotifier()

        val pipeline = MessagePipeline(
            sourceDetector = SourceDetector(),
            evidenceExtractor = EvidenceExtractor(),
            classifier = ProbabilisticClassifier(calibrator = NoOpCalibrator),
            policy = PolicyEngine(),
            dedup = DedupEngine,
            accountRegistry = AccountRegistry(TestAccountRepo()),
            categoryEngine = CategoryEngine(),
            transactionRepo = txRepo,
            rawMessageRepo = TestRawMessageRepo(),
            categoryRepo = TestCategoryRepo(),
            fraudDetector = fraudDetector,
            anomalyNotifier = anomalyNotifier
        )

        // Incoming transaction of ₹5,000 at Swiggy (where median is ₹100) -> Outlier with MAD Z > 3.5
        val rawMessage = RawMessage(
            id = "raw_1",
            sourceType = SourceType.NOTIFICATION,
            packageName = "com.phonepe.app",
            senderId = "PhonePe",
            title = "Paid to Swiggy",
            text = "Debited INR 5,000.00 from A/c XX1234 to Swiggy. UTR: 123456789012",
            timestamp = 1727600000000L
        )

        pipeline.ingest(rawMessage)

        assertNotNull(anomalyNotifier.alertedTxId, "AnomalyNotifier must be triggered on high anomaly")
        assertEquals(999L, anomalyNotifier.alertedTxId)
        assertEquals(5000.0, anomalyNotifier.alertedAmount)
        assertEquals(TransactionStatus.PENDING, txRepo.currentStatus, "Anomalous transaction must be flagged PENDING")
    }

    @Test
    fun testNormalTransactionDoesNotFireAnomalyAlert() = runBlocking {
        val history = listOf(95.0, 100.0, 105.0, 100.0, 98.0, 102.0, 100.0, 101.0, 99.0, 100.0, 103.0, 97.0)
        val txRepo = TestTxRepo(history)
        val fraudDetector = FraudDetector(txRepo)
        val anomalyNotifier = RecordingAnomalyNotifier()

        val pipeline = MessagePipeline(
            sourceDetector = SourceDetector(),
            evidenceExtractor = EvidenceExtractor(),
            classifier = ProbabilisticClassifier(calibrator = NoOpCalibrator),
            policy = PolicyEngine(),
            dedup = DedupEngine,
            accountRegistry = AccountRegistry(TestAccountRepo()),
            categoryEngine = CategoryEngine(),
            transactionRepo = txRepo,
            rawMessageRepo = TestRawMessageRepo(),
            categoryRepo = TestCategoryRepo(),
            fraudDetector = fraudDetector,
            anomalyNotifier = anomalyNotifier
        )

        // Normal transaction of ₹102
        val rawMessage = RawMessage(
            id = "raw_2",
            sourceType = SourceType.NOTIFICATION,
            packageName = "com.phonepe.app",
            senderId = "PhonePe",
            title = "Paid to Swiggy",
            text = "Debited INR 102.00 from A/c XX1234 to Swiggy. UTR: 123456789013",
            timestamp = 1727600000000L
        )

        pipeline.ingest(rawMessage)

        assertNull(anomalyNotifier.alertedTxId, "AnomalyNotifier must not be triggered for normal transactions")
    }
}
