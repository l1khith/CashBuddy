package com.cashbuddy.core

import com.cashbuddy.domain.model.Account
import com.cashbuddy.domain.model.AccountType
import com.cashbuddy.domain.model.Transaction
import com.cashbuddy.domain.model.TransactionStatus
import com.cashbuddy.domain.model.TransactionType
import com.cashbuddy.domain.repository.AccountRepository
import com.cashbuddy.domain.repository.MergeLogEntry
import com.cashbuddy.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DuplicateReconcilerTest {

    private class FakeTransactionRepo : TransactionRepository {
        val txs = mutableListOf<Transaction>()
        val mergeLogs = mutableListOf<MergeLogEntry>()

        override fun getAll(): Flow<List<Transaction>> = flowOf(txs.filter { !it.isMerged })
        override fun getById(id: Long): Flow<Transaction?> = flowOf(txs.find { it.id == id })
        override fun getPending(): Flow<List<Transaction>> = flowOf(txs.filter { it.status == TransactionStatus.PENDING && !it.isMerged })
        override fun getRecent(limit: Long): Flow<List<Transaction>> = flowOf(txs.filter { !it.isMerged }.takeLast(limit.toInt()))
        override fun getByDateRange(start: Long, end: Long): Flow<List<Transaction>> = flowOf(txs.filter { it.timestamp in start..end && !it.isMerged })
        override fun getByDateRangeWithLimit(start: Long, end: Long, limit: Long): Flow<List<Transaction>> =
            flowOf(txs.filter { it.timestamp in start..end && !it.isMerged }.take(limit.toInt()))
        override fun getSummaryByDateRange(start: Long, end: Long): Flow<com.cashbuddy.domain.model.DateRangeSummary> =
            flowOf(com.cashbuddy.domain.model.DateRangeSummary(0.0, 0.0, 0L))
        override fun getByCategory(categoryId: Long): Flow<List<Transaction>> = flowOf(txs.filter { it.categoryId == categoryId && !it.isMerged })
        override fun getMonthlySummary(): Flow<List<com.cashbuddy.domain.repository.MonthlySummary>> = flowOf(emptyList())
        override fun getCategoryBreakdown(start: Long, end: Long): Flow<List<com.cashbuddy.domain.repository.CategoryBreakdown>> = flowOf(emptyList())
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
        override fun getCount(): Flow<Long> = flowOf(txs.count { !it.isMerged }.toLong())

        override suspend fun findDuplicateCandidates(): List<Transaction> = txs.filter { !it.isMerged }
        override suspend fun markMerged(id: Long, survivorId: Long) {
            val idx = txs.indexOfFirst { it.id == id }
            if (idx != -1) txs[idx] = txs[idx].copy(isMerged = true, mergedIntoId = survivorId)
        }
        override suspend fun unmarkMerged(id: Long) {
            val idx = txs.indexOfFirst { it.id == id }
            if (idx != -1) txs[idx] = txs[idx].copy(isMerged = false, mergedIntoId = null)
        }
        override suspend fun insertMergeLog(survivorId: Long, mergedId: Long, timestamp: Long) {
            mergeLogs.add(MergeLogEntry(id = (mergeLogs.size + 1).toLong(), survivorId = survivorId, mergedId = mergedId, mergedAt = timestamp))
        }
        override suspend fun getMergedTransactions(survivorId: Long): List<Transaction> = txs.filter { it.mergedIntoId == survivorId }
        override suspend fun deleteMergeLog(survivorId: Long) {
            mergeLogs.removeAll { it.survivorId == survivorId }
        }
        override suspend fun getRecentMergeLogs(): List<MergeLogEntry> = mergeLogs.sortedByDescending { it.mergedAt }
        override suspend fun getMergeLogCount(): Long = mergeLogs.size.toLong()
        override suspend fun sumByCategory(category: String, startTime: Long, endTime: Long): Double =
            txs.filter {
                (it.categoryName == category || it.categoryId.toString() == category) &&
                it.type == TransactionType.DEBIT &&
                it.timestamp in startTime..endTime &&
                !it.isMerged
            }.sumOf { it.amount }
        override fun getByCategoryAndPeriod(category: String, startTime: Long, endTime: Long): Flow<List<Transaction>> =
            flowOf(txs.filter {
                (it.categoryName == category || it.categoryId.toString() == category) &&
                it.type == TransactionType.DEBIT &&
                it.timestamp in startTime..endTime &&
                !it.isMerged
            })
    }

    private class FakeAccountRepo : AccountRepository {
        val accounts = mutableListOf<Account>()
        override fun getAll(): Flow<List<Account>> = flowOf(accounts)
        override fun getById(id: Long): Flow<Account?> = flowOf(accounts.find { it.id == id })
        override fun getByType(type: AccountType): Flow<List<Account>> = flowOf(accounts.filter { it.type == type })
        override suspend fun insert(account: Account): Long {
            accounts.add(account)
            return account.id
        }
        override suspend fun update(account: Account) {
            val idx = accounts.indexOfFirst { it.id == account.id }
            if (idx != -1) accounts[idx] = account
        }
        override suspend fun updateBalance(id: Long, balance: Double) {
            val idx = accounts.indexOfFirst { it.id == id }
            if (idx != -1) accounts[idx] = accounts[idx].copy(balance = balance)
        }
        override suspend fun seedDefaults(currentTimestamp: Long) {}
        override suspend fun deleteById(id: Long) {}
        override fun getCount(): Flow<Long> = flowOf(accounts.size.toLong())
    }

    @Test
    fun testDuplicateReconciler_GroupsIdenticalScreenshotsByParsedDate() {
        val reconciler = DuplicateReconciler(FakeTransactionRepo(), FakeAccountRepo())

        val ocr1 = """
            ₹18
            Paid to BMTC
            Banking name: BMTC
            28 September 2026, 8:02 pm
            POWERED BY UPI
        """.trimIndent()

        val ocr2 = """
            18
            Paid to BMTC
            Banking name: BMTC
            28 September 2026, 8:02 pm
            POWERED BY UPI
        """.trimIndent()

        val ocr3 = """
            ₹18
            Paid to BMTC
            28 Sept 2026, 8:02 pm
        """.trimIndent()

        val tx1 = Transaction(
            id = 1L,
            amount = 18.0,
            type = TransactionType.DEBIT,
            merchant = "BMTC",
            categoryId = 1L,
            sourceApp = "Google Pay",
            rawText = ocr1,
            confidence = 0.99f,
            timestamp = 1727546520000L,
            createdAt = 1727546520000L
        )

        val tx2 = Transaction(
            id = 2L,
            amount = 18.0,
            type = TransactionType.DEBIT,
            merchant = "BMTC",
            categoryId = 1L,
            sourceApp = "screenshot",
            rawText = ocr2,
            confidence = 0.85f,
            timestamp = 1727550000000L,
            createdAt = 1727550000000L
        )

        val tx3 = Transaction(
            id = 3L,
            amount = 18.0,
            type = TransactionType.DEBIT,
            merchant = "BMTC",
            categoryId = 1L,
            sourceApp = "screenshot",
            rawText = ocr3,
            confidence = 0.90f,
            timestamp = 1727560000000L,
            createdAt = 1727560000000L
        )

        val groups = reconciler.groupDuplicates(listOf(tx1, tx2, tx3))
        assertEquals(1, groups.size, "Expected 1 duplicate group for 3 identical BMTC screenshots")

        val group = groups.first()
        assertEquals(3, group.totalCount)
        assertEquals(2, group.duplicates.size)
        assertEquals(1L, group.survivor.id, "Expected tx1 to be survivor (highest confidence 0.99 + detailed layout)")
    }

    @Test
    fun testDuplicateReconciler_GroupsByUtr() {
        val reconciler = DuplicateReconciler(FakeTransactionRepo(), FakeAccountRepo())

        val tx1 = Transaction(
            id = 10L,
            amount = 130.0,
            type = TransactionType.DEBIT,
            merchant = "SmartQ",
            categoryId = 1L,
            sourceApp = "PhonePe",
            rawText = "Paid ₹130 to SmartQ. UTR: 427182938472",
            confidence = 0.95f,
            timestamp = 1727600000000L
        )

        val tx2 = Transaction(
            id = 11L,
            amount = 130.0,
            type = TransactionType.DEBIT,
            merchant = "SmartQ Cafeteria",
            categoryId = 1L,
            sourceApp = "screenshot",
            rawText = "UPI transaction id: 427182938472. 130 paid.",
            confidence = 0.88f,
            timestamp = 1727600500000L
        )

        val groups = reconciler.groupDuplicates(listOf(tx1, tx2))
        assertEquals(1, groups.size)
        assertEquals(10L, groups.first().survivor.id)
    }

    @Test
    fun testDuplicateReconciler_GroupsBy5MinuteWindowWhenNoDateInText() {
        val reconciler = DuplicateReconciler(FakeTransactionRepo(), FakeAccountRepo())
        val baseTime = 1727610000000L

        val tx1 = Transaction(
            id = 20L,
            amount = 49.0,
            type = TransactionType.DEBIT,
            merchant = "Rapido",
            categoryId = 2L,
            sourceApp = "Rapido",
            rawText = "Paid 49 for bike ride",
            confidence = 0.90f,
            timestamp = baseTime
        )

        val tx2 = Transaction(
            id = 21L,
            amount = 49.0,
            type = TransactionType.DEBIT,
            merchant = "Rapido",
            categoryId = 2L,
            sourceApp = "screenshot",
            rawText = "Paid 49 for bike ride",
            confidence = 0.92f,
            timestamp = baseTime + 60_000L
        )

        val groups = reconciler.groupDuplicates(listOf(tx1, tx2))
        assertEquals(1, groups.size)
        assertEquals(21L, groups.first().survivor.id, "Expected tx2 to be survivor due to higher confidence (0.92 vs 0.90)")
    }

    @Test
    fun testDuplicateReconciler_NonDuplicatesRemainIndependent() {
        val reconciler = DuplicateReconciler(FakeTransactionRepo(), FakeAccountRepo())

        val tx1 = Transaction(id = 1L, amount = 18.0, type = TransactionType.DEBIT, merchant = "BMTC", categoryId = 1L, sourceApp = "UPI", rawText = "", confidence = 0.9f, timestamp = 1000L)
        val tx2 = Transaction(id = 2L, amount = 49.0, type = TransactionType.DEBIT, merchant = "Rapido", categoryId = 1L, sourceApp = "UPI", rawText = "", confidence = 0.9f, timestamp = 2000L)
        val tx3 = Transaction(id = 3L, amount = 130.0, type = TransactionType.DEBIT, merchant = "SmartQ", categoryId = 1L, sourceApp = "UPI", rawText = "", confidence = 0.9f, timestamp = 3000L)

        val groups = reconciler.groupDuplicates(listOf(tx1, tx2, tx3))
        assertTrue(groups.isEmpty(), "Distinct transactions must not be grouped as duplicates")
    }

    @Test
    fun testDuplicateReconciler_MergeAndUndoRevertsAccountBalance() = runBlocking {
        val txRepo = FakeTransactionRepo()
        val accRepo = FakeAccountRepo()
        val reconciler = DuplicateReconciler(txRepo, accRepo)

        // 1. Setup account with ₹5000 initial balance
        accRepo.insert(
            Account(id = 1L, name = "Primary Bank", type = AccountType.BANK, balance = 4964.0, createdAt = 0L, updatedAt = 0L)
        )

        // 2. Insert two confirmed ₹18 duplicate transactions for account 1
        val tx1 = Transaction(
            id = 1L,
            amount = 18.0,
            type = TransactionType.DEBIT,
            merchant = "BMTC",
            categoryId = 1L,
            accountId = 1L,
            sourceApp = "Google Pay",
            rawText = "₹18 Paid to BMTC 28 September 2026, 8:02 pm",
            confidence = 0.99f,
            status = TransactionStatus.CONFIRMED,
            timestamp = 1727546520000L
        )

        val tx2 = Transaction(
            id = 2L,
            amount = 18.0,
            type = TransactionType.DEBIT,
            merchant = "BMTC",
            categoryId = 1L,
            accountId = 1L,
            sourceApp = "screenshot",
            rawText = "₹18 Paid to BMTC 28 September 2026, 8:02 pm",
            confidence = 0.85f,
            status = TransactionStatus.CONFIRMED,
            timestamp = 1727546520000L
        )

        txRepo.insert(tx1)
        txRepo.insert(tx2)

        // 3. Scan duplicate groups
        val groups = reconciler.findDuplicateGroups()
        assertEquals(1, groups.size)
        assertEquals(2, groups.first().totalCount)

        // 4. Execute merge
        val mergeSummary = reconciler.mergeDuplicateGroups(groups)
        assertEquals(1, mergeSummary.groupsMerged)
        assertEquals(1, mergeSummary.totalDuplicatesMerged)

        // Verify duplicate was marked merged
        val duplicateTx = txRepo.txs.find { it.id == 2L }
        assertNotNull(duplicateTx)
        assertTrue(duplicateTx.isMerged)
        assertEquals(1L, duplicateTx.mergedIntoId)

        // Verify account balance was adjusted back by +₹18 (4964 + 18 = 4982)
        val updatedAcc = accRepo.accounts.find { it.id == 1L }
        assertNotNull(updatedAcc)
        assertEquals(4982.0, updatedAcc.balance, 0.001)

        // 5. Test Undo Merge
        val undoneCount = reconciler.undoMergeForSurvivor(1L)
        assertEquals(1, undoneCount)

        val restoredTx = txRepo.txs.find { it.id == 2L }
        assertNotNull(restoredTx)
        assertFalse(restoredTx.isMerged)

        // Account balance reverted back by -₹18 (4982 - 18 = 4964)
        val revertedAcc = accRepo.accounts.find { it.id == 1L }
        assertNotNull(revertedAcc)
        assertEquals(4964.0, revertedAcc.balance, 0.001)
    }
}
