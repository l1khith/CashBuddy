// NO-NETWORK
package com.cashbuddy.data.repository

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.cashbuddy.db.AppDatabase
import com.cashbuddy.domain.repository.MergeOperation
import com.cashbuddy.domain.repository.UndoMergeOperation
import kotlinx.coroutines.runBlocking
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Verifies [SqlDelightMergeStore] against a real (in-memory) SQLite database:
 * merge/undo batches must land atomically — every side effect (transactions.is_merged,
 * merge_log row, accounts.balance delta) present after success, none after a failure.
 */
class SqlDelightMergeStoreTest {

    private lateinit var database: AppDatabase
    private lateinit var store: SqlDelightMergeStore

    @BeforeTest
    fun setup() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        AppDatabase.Schema.create(driver)
        database = AppDatabase(driver)
        database.categoriesQueries.insert("Food", "EXPENSE", "restaurant", "#FF0000", true, 1L, null, 1000L)
        // Primary account, balance ₹5000. Confirmed DEBIT inserts below will
        // deduct via the schema's insert triggers (5000 - 18 = 4982).
        database.accountsQueries.insert(
            name = "Primary Bank", type = "BANK", number = null, bank = null,
            balance = 5000.0, currency = "INR", is_active = true, sort_order = 1L,
            created_at = 0L, updated_at = 0L
        )
        store = SqlDelightMergeStore(database)
    }

    private fun insertConfirmedDebit(id: Long, timestamp: Long = 1000L) {
        database.transactionsQueries.insert(
            amount = 18.0,
            type = "DEBIT",
            currency = "INR",
            merchant = "BMTC",
            category_id = 1L,
            account_id = 1L,
            source_app = "test",
            raw_text = "₹18 Paid to BMTC",
            confidence = 0.9,
            status = "CONFIRMED",
            notes = null,
            timestamp = timestamp,
            created_at = timestamp,
            updated_at = timestamp,
            is_merged = 0L,
            merged_into_id = null,
            payment_method = "UPI",
            is_loan_given = 0L
        )
        assertEquals(id, database.transactionsQueries.lastInsertRowId().executeAsOne())
    }

    private fun accountBalance(): Double =
        database.accountsQueries.getById(1L).executeAsOne().balance

    private fun mergedFlags(id: Long): Pair<Long, Long?> {
        val row = database.transactionsQueries.getById(id).executeAsOne()
        return row.is_merged to row.merged_into_id
    }

    private fun mergeLogCount(): Long =
        database.transactionsQueries.getMergeLogCount().executeAsOne()

    @Test
    fun testApplyMerges_CommitsMarkLogAndBalanceInOneBatch() = runBlocking {
        insertConfirmedDebit(id = 1L)
        insertConfirmedDebit(id = 2L)
        // Insert trigger deducted both confirmed debits: 5000 - 18 - 18 = 4964
        assertEquals(4964.0, accountBalance(), 0.001)

        store.applyMerges(
            operations = listOf(
                MergeOperation(duplicateId = 2L, survivorId = 1L, balanceAccountId = 1L, balanceDelta = 18.0)
            ),
            mergedAt = 5000L
        )

        val (isMerged, mergedInto) = mergedFlags(2L)
        assertEquals(1L, isMerged)
        assertEquals(1L, mergedInto)
        assertEquals(1L, mergeLogCount())
        // Balance reverted back to 4982 (4964 + 18)
        assertEquals(4982.0, accountBalance(), 0.001)
    }

    @Test
    fun testApplyMerges_NonConfirmedDuplicateSkipsBalanceRevert() = runBlocking {
        insertConfirmedDebit(id = 1L)
        insertConfirmedDebit(id = 2L)
        store.applyMerges(
            operations = listOf(
                // PENDING duplicate: no balance write
                MergeOperation(duplicateId = 2L, survivorId = 1L, balanceAccountId = null, balanceDelta = 0.0)
            ),
            mergedAt = 5000L
        )
        assertEquals(1L, mergeLogCount())
        assertEquals(4964.0, accountBalance(), 0.001)
    }

    @Test
    fun testUndoMerges_RestoresFlagsBalanceAndClearsLog() = runBlocking {
        insertConfirmedDebit(id = 1L)
        insertConfirmedDebit(id = 2L)
        store.applyMerges(
            operations = listOf(
                MergeOperation(duplicateId = 2L, survivorId = 1L, balanceAccountId = 1L, balanceDelta = 18.0)
            ),
            mergedAt = 5000L
        )

        store.undoMerges(
            survivorIds = listOf(1L),
            operations = listOf(
                UndoMergeOperation(duplicateId = 2L, balanceAccountId = 1L, balanceDelta = -18.0)
            ),
            updatedAt = 6000L
        )

        val (isMerged, mergedInto) = mergedFlags(2L)
        assertEquals(0L, isMerged)
        assertEquals(null, mergedInto)
        assertEquals(0L, mergeLogCount())
        // Undo re-applies the duplicate's impact: 4982 - 18 = 4964
        assertEquals(4964.0, accountBalance(), 0.001)
    }

    /**
     * Guard for the atomicity premise the store relies on: SQLDelight transactions on the
     * platform driver must roll back the whole batch when any statement block throws.
     * If this ever fails, the all-or-nothing guarantee of [SqlDelightMergeStore] is void.
     */
    @Test
    fun testSqlDelightTransaction_RollsBackWholeBatchOnFailure() {
        insertConfirmedDebit(id = 1L)
        insertConfirmedDebit(id = 2L)

        assertFailsWith<IllegalStateException> {
            database.transaction {
                database.transactionsQueries.markMerged(merged_into_id = 1L, updated_at = 5000L, id = 2L)
                database.transactionsQueries.insertMergeLog(survivor_id = 1L, merged_id = 2L, merged_at = 5000L)
                database.accountsQueries.applyBalanceDelta(delta = 18.0, updatedAt = 5000L, id = 1L)
                throw IllegalStateException("simulated crash mid-batch")
            }
        }

        val (isMerged, _) = mergedFlags(2L)
        assertEquals(0L, isMerged, "markMerged must be rolled back")
        assertEquals(0L, mergeLogCount(), "merge_log must be rolled back")
        assertEquals(4964.0, accountBalance(), 0.001, "balance delta must be rolled back")
        assertTrue(true)
    }
}
