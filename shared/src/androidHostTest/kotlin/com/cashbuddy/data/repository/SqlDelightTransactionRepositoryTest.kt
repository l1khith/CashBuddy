// NO-NETWORK
package com.cashbuddy.data.repository

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.cashbuddy.db.AppDatabase
import com.cashbuddy.domain.model.Transaction
import com.cashbuddy.domain.model.TransactionStatus
import com.cashbuddy.domain.model.TransactionType
import kotlinx.coroutines.runBlocking
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Unit tests verifying [TransactionRepositoryImpl] queries against an in-memory SQLDelight database.
 */
class SqlDelightTransactionRepositoryTest {

    private lateinit var database: AppDatabase
    private lateinit var repository: TransactionRepositoryImpl

    @BeforeTest
    fun setup() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        AppDatabase.Schema.create(driver)
        database = AppDatabase(driver)
        database.categoriesQueries.insert("Food", "EXPENSE", "restaurant", "#FF0000", true, 1L, null, 1000L)
        database.categoriesQueries.insert("Travel", "EXPENSE", "car", "#0000FF", true, 2L, null, 1000L)
        database.categoriesQueries.insert("Salary", "INCOME", "salary", "#00FF00", true, 3L, null, 1000L)
        repository = TransactionRepositoryImpl(database)
    }

    @Test
    fun testSumAllComputesOnlyDebitWithinRangeAndExcludesMerged() = runBlocking {
        val start = 10_000L
        val end = 20_000L

        // 1. Valid DEBIT inside range (amount: 150.0)
        repository.insert(
            Transaction(
                amount = 150.0,
                type = TransactionType.DEBIT,
                merchant = "Merchant A",
                categoryId = 1L,
                sourceApp = "SMS",
                rawText = "text 1",
                confidence = 0.95f,
                status = TransactionStatus.CONFIRMED,
                timestamp = 15_000L
            )
        )

        // 2. Valid DEBIT inside range (amount: 250.0)
        repository.insert(
            Transaction(
                amount = 250.0,
                type = TransactionType.DEBIT,
                merchant = "Merchant B",
                categoryId = 2L,
                sourceApp = "SMS",
                rawText = "text 2",
                confidence = 0.95f,
                status = TransactionStatus.CONFIRMED,
                timestamp = 12_000L
            )
        )

        // 3. CREDIT inside range (amount: 500.0) -> Should NOT be counted in DEBIT sumAll
        repository.insert(
            Transaction(
                amount = 500.0,
                type = TransactionType.CREDIT,
                merchant = "Employer",
                categoryId = 3L,
                sourceApp = "SMS",
                rawText = "salary",
                confidence = 0.99f,
                status = TransactionStatus.CONFIRMED,
                timestamp = 15_000L
            )
        )

        // 4. DEBIT before range (amount: 300.0) -> Should NOT be counted
        repository.insert(
            Transaction(
                amount = 300.0,
                type = TransactionType.DEBIT,
                merchant = "Past Store",
                categoryId = 1L,
                sourceApp = "SMS",
                rawText = "old",
                confidence = 0.95f,
                status = TransactionStatus.CONFIRMED,
                timestamp = 5_000L
            )
        )

        // 5. DEBIT after range (amount: 400.0) -> Should NOT be counted
        repository.insert(
            Transaction(
                amount = 400.0,
                type = TransactionType.DEBIT,
                merchant = "Future Store",
                categoryId = 1L,
                sourceApp = "SMS",
                rawText = "future",
                confidence = 0.95f,
                status = TransactionStatus.CONFIRMED,
                timestamp = 25_000L
            )
        )

        // 6. DEBIT inside range but merged (isMerged = true) -> Should NOT be counted
        val mergedId = repository.insert(
            Transaction(
                amount = 100.0,
                type = TransactionType.DEBIT,
                merchant = "Duplicate Store",
                categoryId = 1L,
                sourceApp = "SMS",
                rawText = "duplicate",
                confidence = 0.95f,
                status = TransactionStatus.CONFIRMED,
                timestamp = 14_000L
            )
        )
        repository.markMerged(mergedId, survivorId = 1L)

        // Sum should only be 150.0 + 250.0 = 400.0
        val total = repository.sumAll(start, end, TransactionType.DEBIT)
        assertEquals(400.0, total)
    }

    @Test
    fun testSumAllReturnsZeroWhenEmpty() = runBlocking {
        val total = repository.sumAll(1_000L, 5_000L, TransactionType.DEBIT)
        assertEquals(0.0, total)
    }
}
