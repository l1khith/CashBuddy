// NO-NETWORK
package com.cashbuddy.debug

import com.cashbuddy.core.CategoryEngine
import com.cashbuddy.core.prob.AccountRegistry
import com.cashbuddy.core.prob.Contribution
import com.cashbuddy.core.prob.DedupEngine
import com.cashbuddy.core.prob.Evidence
import com.cashbuddy.core.prob.EvidenceExtractor
import com.cashbuddy.core.prob.FieldConfidences
import com.cashbuddy.core.prob.MessagePipeline
import com.cashbuddy.core.prob.NoOpCalibrator
import com.cashbuddy.core.prob.PolicyEngine
import com.cashbuddy.core.prob.ProbabilisticClassifier
import com.cashbuddy.core.prob.RawMessage
import com.cashbuddy.core.prob.SourceDetector
import com.cashbuddy.core.prob.SourceType
import com.cashbuddy.domain.model.Account
import com.cashbuddy.domain.model.Category
import com.cashbuddy.domain.model.DebugLogEntry
import com.cashbuddy.domain.model.DebugLogFilter
import com.cashbuddy.domain.model.Transaction
import com.cashbuddy.domain.model.TransactionStatus
import com.cashbuddy.domain.model.TransactionType
import com.cashbuddy.domain.repository.AccountRepository
import com.cashbuddy.domain.repository.CategoryRepository
import com.cashbuddy.domain.repository.DebugLogRepository
import com.cashbuddy.domain.repository.RawMessageRepository
import com.cashbuddy.domain.repository.TransactionRepository
import com.cashbuddy.domain.model.AccountType
import com.cashbuddy.domain.model.CategoryType
import com.cashbuddy.domain.repository.CategoryBreakdown
import com.cashbuddy.domain.repository.MonthlySummary
import com.cashbuddy.presentation.debug.DebugLogViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DebugLoggerTest {

    private class TestDebugLogRepository(private val maxRows: Long = 5000L) : DebugLogRepository {
        val entries = mutableListOf<DebugLogEntry>()

        override suspend fun insert(entry: DebugLogEntry) {
            entries.add(entry)
            if (entries.size > maxRows) {
                val excess = entries.size - maxRows.toInt()
                repeat(excess) {
                    entries.removeAt(0)
                }
            }
        }

        override suspend fun updateClassification(
            id: String,
            detectedSource: String?,
            evidenceJson: String?,
            pTransaction: Double?,
            contributionsJson: String?,
            fieldConfidencesJson: String?
        ) {
            val idx = entries.indexOfFirst { it.id == id }
            if (idx != -1) {
                val old = entries[idx]
                entries[idx] = old.copy(
                    detectedSource = detectedSource,
                    evidenceJson = evidenceJson,
                    pTransaction = pTransaction,
                    contributionsJson = contributionsJson,
                    fieldConfidencesJson = fieldConfidencesJson
                )
            }
        }

        override suspend fun updatePolicy(id: String, policyAction: String?) {
            val idx = entries.indexOfFirst { it.id == id }
            if (idx != -1) {
                val old = entries[idx]
                entries[idx] = old.copy(policyAction = policyAction)
            }
        }

        override suspend fun updateOutcome(
            id: String,
            policyAction: String?,
            pipelineOutcome: String?,
            resultingTxId: String?,
            mergeTargetId: String?,
            errorMessage: String?
        ) {
            val idx = entries.indexOfFirst { it.id == id }
            if (idx != -1) {
                val old = entries[idx]
                entries[idx] = old.copy(
                    policyAction = policyAction ?: old.policyAction,
                    pipelineOutcome = pipelineOutcome,
                    resultingTxId = resultingTxId,
                    mergeTargetId = mergeTargetId,
                    errorMessage = errorMessage
                )
            }
        }

        override suspend fun updateRawText(id: String, rawText: String) {
            val idx = entries.indexOfFirst { it.id == id }
            if (idx != -1) {
                val old = entries[idx]
                entries[idx] = old.copy(rawText = rawText)
            }
        }

        override suspend fun query(filter: DebugLogFilter): List<DebugLogEntry> = entries.toList()
        override suspend fun recent(limit: Int): List<DebugLogEntry> = entries.takeLast(limit).reversed()
        override suspend fun clearAll() { entries.clear() }
        override suspend fun count(): Long = entries.size.toLong()
    }

    private class TestRawMessageRepository : RawMessageRepository {
        val messages = mutableListOf<RawMessage>()
        override suspend fun insert(rawMessage: RawMessage) { messages.add(rawMessage) }
        override suspend fun getRecent(limit: Long): List<RawMessage> = messages.takeLast(limit.toInt())
        override suspend fun getById(id: String): RawMessage? = messages.find { it.id == id }
        override suspend fun updateResultingTx(rawMessageId: String, txId: String) {}
    }

    private class TestTransactionRepository : TransactionRepository {
        val txs = mutableListOf<Transaction>()
        override fun getAll(): Flow<List<Transaction>> = flowOf(txs)
        override fun getById(id: Long): Flow<Transaction?> = flowOf(txs.find { it.id == id })
        override fun getPending(): Flow<List<Transaction>> = flowOf(txs.filter { it.status == TransactionStatus.PENDING })
        override fun getByDateRange(start: Long, end: Long): Flow<List<Transaction>> = flowOf(txs.filter { it.timestamp in start..end })
        override fun getByCategory(categoryId: Long): Flow<List<Transaction>> = flowOf(txs.filter { it.categoryId == categoryId })
        override fun getMonthlySummary(): Flow<List<MonthlySummary>> = flowOf(emptyList())
        override fun getCategoryBreakdown(start: Long, end: Long): Flow<List<CategoryBreakdown>> = flowOf(emptyList())
        override suspend fun insert(transaction: Transaction): Long {
            val id = (txs.size + 1).toLong()
            txs.add(transaction.copy(id = id))
            return id
        }
        override suspend fun update(transaction: Transaction) {}
        override suspend fun updateStatus(id: Long, status: TransactionStatus) {}
        override suspend fun deleteById(id: Long) {}
        override fun getBalance(): Flow<Double> = flowOf(0.0)
        override fun getAverageAmount(): Flow<Double> = flowOf(0.0)
    }

    private class TestCategoryRepository : CategoryRepository {
        val categories = listOf(Category(id = 1L, name = "Food & Dining", type = CategoryType.EXPENSE, icon = "food", color = "#FF0000", isDefault = true))
        override fun getAll(): Flow<List<Category>> = flowOf(categories)
        override fun getByType(type: CategoryType): Flow<List<Category>> = flowOf(categories.filter { it.type == type })
        override fun getById(id: Long): Flow<Category?> = flowOf(categories.find { it.id == id })
        override suspend fun seedDefaults(currentTimestamp: Long) {}
    }

    private class TestAccountRepository : AccountRepository {
        val accounts = listOf(Account(id = 1L, name = "Primary Bank", type = AccountType.BANK, number = "1234", balance = 5000.0, createdAt = 0L, updatedAt = 0L))
        override fun getAll(): Flow<List<Account>> = flowOf(accounts)
        override fun getById(id: Long): Flow<Account?> = flowOf(accounts.find { it.id == id })
        override fun getByType(type: AccountType): Flow<List<Account>> = flowOf(accounts.filter { it.type == type })
        override suspend fun insert(account: Account): Long = 1L
        override suspend fun update(account: Account) {}
        override suspend fun updateBalance(id: Long, balance: Double) {}
        override suspend fun deleteById(id: Long) {}
        override suspend fun seedDefaults(currentTimestamp: Long) {}
    }

    @Test
    fun testEvidenceToJsonAndContributionToJsonRoundTrip() {
        val evidence = Evidence(
            hasAmount = true,
            hasAccount = true,
            hasUtr = true,
            hasDebit = true,
            hasCredit = false,
            hasOtp = false,
            hasPromo = false,
            hasOffer = false,
            hasUpiHandle = true,
            hasBalanceMention = true,
            hasTransactionVerb = true,
            hasSuccessWord = true,
            senderLooksBank = true,
            fromMerchantPackage = false,
            recentSameAmount = false,
            recentSameMerchant = false,
            velocityHigh = false
        )

        val json = evidence.toJson()
        val deserialized = Evidence.fromJson(json)
        assertEquals(evidence, deserialized)

        // Contribution single round-trip
        val contribution = Contribution("OTP", -2.99, 0.05)
        val cJson = contribution.toJson()
        val deserializedC = Contribution.fromJson(cJson)
        assertEquals(contribution, deserializedC)

        // Contribution list round-trip
        val list = listOf(
            Contribution("amount", 1.09, 3.0),
            Contribution("debit", 1.09, 3.0),
            Contribution("OTP", -2.99, 0.05)
        )
        val listJson = Contribution.listToJson(list)
        val deserializedList = Contribution.listFromJson(listJson)
        assertEquals(list.size, deserializedList.size)
        assertEquals(list[0], deserializedList[0])
        assertEquals(list[2], deserializedList[2])

        // FieldConfidences round-trip
        val fc = FieldConfidences(amount = 0.98, type = 0.95, accountLast4 = 0.95, merchant = 0.85)
        val fcJson = fc.toJson()
        val deserializedFc = FieldConfidences.fromJson(fcJson)
        assertEquals(fc, deserializedFc)
    }

    @Test
    fun testDebugConfigEnabledStates() {
        // 1. Debug build -> always enabled
        val configDebug = DebugConfig(isDebugBuild = true)
        assertTrue(configDebug.enabled)

        // 2. Release build without developer mode -> disabled
        val configRelease = DebugConfig(isDebugBuild = false)
        assertFalse(configRelease.enabled)

        // 3. Release build with developer mode turned on -> enabled
        configRelease.developerModeOverride = true
        assertTrue(configRelease.enabled)

        // 4. Release build with developer mode turned off -> disabled
        configRelease.developerModeOverride = false
        assertFalse(configRelease.enabled)
    }

    @Test
    fun testDebugLoggerCaptureRawNoOpWhenDisabled() {
        runBlocking {
            val repo = TestDebugLogRepository()
            val config = DebugConfig(isDebugBuild = false)
            val logger = DebugLogger(repo, config)

            val raw = RawMessage(
                id = "msg-1",
                sourceType = SourceType.SMS,
                packageName = null,
                senderId = "VK-BANK",
                title = "",
                text = "Rs 500 debited from A/c XX1234",
                timestamp = 1727000000000L
            )

            val logId = logger.captureRaw(raw)
            assertEquals("", logId)
            assertEquals(0L, repo.count())
        }
    }

    @Test
    fun testDebugLoggerCaptureRawWhenEnabled() {
        runBlocking {
            val repo = TestDebugLogRepository()
            val config = DebugConfig(isDebugBuild = true)
            val logger = DebugLogger(repo, config)

            val raw = RawMessage(
                id = "msg-2",
                sourceType = SourceType.NOTIFICATION,
                packageName = "com.sample.upi",
                senderId = null,
                title = "Paid to Store",
                text = "Payment of ₹150.00 to Grocery Store was successful",
                timestamp = 1727000000000L
            )

            val logId = logger.captureRaw(raw)
            assertTrue(logId.isNotBlank())
            assertEquals(1L, repo.count())

            val entry = repo.entries.first()
            assertEquals("com.sample.upi", entry.packageName)
            assertEquals("Payment of ₹150.00 to Grocery Store was successful", entry.rawText)
        }
    }

    @Test
    fun testMessagePipelineIngestWritesExactlyOneLogRow() {
        runBlocking {
            val repo = TestDebugLogRepository()
            val config = DebugConfig(isDebugBuild = true)
            val logger = DebugLogger(repo, config)

            val rawRepo = TestRawMessageRepository()
            val txRepo = TestTransactionRepository()
            val catRepo = TestCategoryRepository()
            val accountRepo = TestAccountRepository()
            val accountRegistry = AccountRegistry(accountRepo)
            val categoryEngine = CategoryEngine()

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
                debugLogger = logger
            )

            val raw = RawMessage(
                id = "msg-pipe-1",
                sourceType = SourceType.NOTIFICATION,
                packageName = "com.banking.app",
                senderId = null,
                title = "Transaction Alert",
                text = "Rs 250.00 debited from A/c XX1234 to merchant Swiggy. Available bal Rs 5000",
                timestamp = 1727000000000L
            )

            val outcome = pipeline.ingest(raw)
            assertNotNull(outcome)

            // Exactly one log entry written
            assertEquals(1L, repo.count())
            val logged = repo.entries.first()
            assertEquals("NOTIFICATION", logged.sourceType)
            assertEquals("com.banking.app", logged.packageName)
            assertNotNull(logged.policyAction)
            assertNotNull(logged.pipelineOutcome)
            assertNotNull(logged.pTransaction)
            assertNotNull(logged.contributionsJson)
            assertNotNull(logged.fieldConfidencesJson)
        }
    }

    @Test
    fun testCsvExportProducesExpectedHeaderAndRow() {
        val entry = DebugLogEntry(
            id = "test-log-1",
            timestamp = 1727000000000L,
            sourceType = "NOTIFICATION",
            packageName = "com.test.app",
            senderId = null,
            rawTitle = "Test Alert",
            rawText = "Rs.100 debited from A/c XX1234",
            rawTextHash = "hash123",
            detectedSource = "BANK",
            evidenceJson = null,
            pTransaction = 0.9925,
            contributionsJson = null,
            fieldConfidencesJson = null,
            policyAction = "AUTO_LOG",
            pipelineOutcome = "LOGGED",
            resultingTxId = "101",
            mergeTargetId = null,
            errorMessage = null
        )

        val csv = DebugLogViewModel.generateCsv(listOf(entry))

        // Check header
        val lines = csv.trim().split("\n")
        assertEquals(2, lines.size)
        assertEquals("timestamp,source_type,package_name,policy_action,p_transaction,raw_text,outcome", lines[0])

        // Check row
        val row = lines[1]
        assertTrue(row.contains("1727000000000,NOTIFICATION,\"com.test.app\",AUTO_LOG,0.9925,\"Rs.100 debited from A/c XX1234\",LOGGED"))
    }

    @Test
    fun testRingBufferCapEnforcement() {
        runBlocking {
            val repo = TestDebugLogRepository(maxRows = 5L)
            val config = DebugConfig(isDebugBuild = true)
            val logger = DebugLogger(repo, config)

            for (i in 1..10) {
                val raw = RawMessage(
                    id = "msg-$i",
                    sourceType = SourceType.NOTIFICATION,
                    packageName = "com.test.app",
                    senderId = null,
                    title = "Title $i",
                    text = "Paid ₹$i to Store",
                    timestamp = 1727000000000L + i
                )
                logger.captureRaw(raw)
            }

            // Bounded ring buffer: capped at maxRows = 5
            assertEquals(5L, repo.count())

            // The oldest 5 entries (msg-1 through msg-5) were dropped, entries 6..10 remain
            val ids = repo.entries.map { it.id }
            assertTrue(ids.any { it.contains("_msg-6_") })
            assertTrue(ids.any { it.contains("_msg-10_") })
            assertFalse(ids.any { it.contains("_msg-1_") })
        }
    }

    @Test
    fun testPrivacyRedactionForNonFinancialMessages() {
        runBlocking {
            val repo = TestDebugLogRepository()
            val config = DebugConfig(isDebugBuild = true)
            val logger = DebugLogger(repo, config)

            // 1. Recording is OFF: Non-financial personal notification
            config.stopRecording()
            val personalMsg = RawMessage(
                id = "chat-1",
                sourceType = SourceType.NOTIFICATION,
                packageName = "com.chat.social",
                senderId = null,
                title = "Friend",
                text = "Hey are we still meeting tonight?",
                timestamp = 1727000000000L
            )

            logger.captureRaw(personalMsg)
            val entry1 = repo.entries.last()
            assertTrue(entry1.rawText.contains("[REDACTED"))

            // 2. Recording is turned ON: Non-financial message is captured un-redacted
            config.startRecording()
            val personalMsg2 = RawMessage(
                id = "chat-2",
                sourceType = SourceType.NOTIFICATION,
                packageName = "com.chat.social",
                senderId = null,
                title = "Friend",
                text = "Where are you?",
                timestamp = 1727000001000L
            )

            logger.captureRaw(personalMsg2)
            val entry2 = repo.entries.last()
            assertEquals("Where are you?", entry2.rawText)
        }
    }
}
