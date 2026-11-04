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

    @Test
    fun testBalanceTriggersOnStatusTransitionsAndReversals() = runBlocking {
        // Create an account with 10,000 opening balance
        database.accountsQueries.insert(
            name = "Test Bank",
            type = "BANK",
            number = "1234",
            bank = "HDFC",
            balance = 10_000.0,
            currency = "INR",
            is_active = true,
            sort_order = 1L,
            created_at = 1000L,
            updated_at = 1000L
        )
        val accountId = database.accountsQueries.lastInsertRowId().executeAsOne()

        // 1. Insert CONFIRMED debit of 1,000 -> balance becomes 9,000
        val txId = repository.insert(
            Transaction(
                amount = 1000.0,
                type = TransactionType.DEBIT,
                merchant = "Store",
                categoryId = 1L,
                accountId = accountId,
                sourceApp = "SMS",
                rawText = "text",
                confidence = 0.95f,
                status = TransactionStatus.CONFIRMED,
                timestamp = 1000L
            )
        )
        var acc = database.accountsQueries.getById(accountId).executeAsOne()
        assertEquals(9000.0, acc.balance)

        // 2. Anomaly flags transaction: demote from CONFIRMED -> PENDING
        // Trigger revert_account_balance_on_unconfirm_debit restores 1,000 -> balance 10,000
        repository.updateStatus(txId, TransactionStatus.PENDING)
        acc = database.accountsQueries.getById(accountId).executeAsOne()
        assertEquals(10_000.0, acc.balance)

        // 3. User reviews and approves: PENDING -> CONFIRMED
        // Trigger update_account_balance_on_confirm_debit deducts 1,000 -> balance 9,000
        repository.updateStatus(txId, TransactionStatus.CONFIRMED)
        acc = database.accountsQueries.getById(accountId).executeAsOne()
        assertEquals(9000.0, acc.balance)

        // 4. Delete confirmed transaction -> balance restored to 10,000
        repository.deleteById(txId)
        acc = database.accountsQueries.getById(accountId).executeAsOne()
        assertEquals(10_000.0, acc.balance)
    }

    @Test
    fun testPhase4RealWorldMathPaymentMethodsCardsAndLoans() = runBlocking {
        val start = 1_000L
        val end = 10_000L

        // Scenario 3: Creating a standard "UPI" expense DOES increment actualSpent
        val upiTxId = repository.insert(
            Transaction(
                amount = 250.0,
                type = TransactionType.DEBIT,
                merchant = "Grocery Store",
                categoryId = 1L,
                sourceApp = "UPI",
                rawText = "Paid 250",
                confidence = 0.95f,
                status = TransactionStatus.CONFIRMED,
                timestamp = 2_000L,
                paymentMethod = "UPI",
                isLoanGiven = false
            )
        )

        assertEquals(250.0, repository.sumActualSpent(start, end))
        assertEquals(0.0, repository.sumCardOutstanding(start, end))
        assertEquals(0.0, repository.sumLentOut(start, end))

        // Scenario 1: Creating an Expense with "Card" payment method does NOT increment actualSpent
        // Scenario 4: "Card" transactions correctly sum into cardOutstanding
        val cardTxId = repository.insert(
            Transaction(
                amount = 5_000.0,
                type = TransactionType.DEBIT,
                merchant = "Electronics Store",
                categoryId = 2L,
                sourceApp = "SMS",
                rawText = "Spent 5000 on Card",
                confidence = 0.95f,
                status = TransactionStatus.CONFIRMED,
                timestamp = 3_000L,
                paymentMethod = "CARD",
                isLoanGiven = false
            )
        )

        // actualSpent is still 250.0! (Card is excluded)
        assertEquals(250.0, repository.sumActualSpent(start, end))
        // cardOutstanding is 5000.0
        assertEquals(5_000.0, repository.sumCardOutstanding(start, end))
        assertEquals(0.0, repository.sumLentOut(start, end))

        // Scenario 2: Creating an Expense with "UPI" and checking "is_loan_given" does NOT increment actualSpent
        // Scenario 5: "Loan" transactions correctly sum into lentOut
        val loanTxId = repository.insert(
            Transaction(
                amount = 1_500.0,
                type = TransactionType.DEBIT,
                merchant = "Friend Ramesh",
                categoryId = 1L,
                sourceApp = "UPI",
                rawText = "Sent 1500",
                confidence = 0.95f,
                status = TransactionStatus.CONFIRMED,
                timestamp = 4_000L,
                paymentMethod = "UPI",
                isLoanGiven = true
            )
        )

        // actualSpent is still 250.0! (Loan is excluded)
        assertEquals(250.0, repository.sumActualSpent(start, end))
        assertEquals(5_000.0, repository.sumCardOutstanding(start, end))
        assertEquals(1_500.0, repository.sumLentOut(start, end))

        // Also test Review inbox mutations:
        // Update payment method of upiTx to CARD -> actualSpent decreases from 250 to 0, cardOutstanding increases by 250
        repository.updatePaymentMethod(upiTxId, "CARD")
        assertEquals(0.0, repository.sumActualSpent(start, end))
        assertEquals(5_250.0, repository.sumCardOutstanding(start, end))

        // Toggle loan status of loanTx from loan (true) to false -> actualSpent increases by 1500, lentOut becomes 0
        repository.updateLoanStatus(loanTxId, false)
        assertEquals(1_500.0, repository.sumActualSpent(start, end))
        assertEquals(0.0, repository.sumLentOut(start, end))

        // Mixed-case writer robustness: the Add/Review chips store title-case "Card"
        // ("UPI" is the only all-caps default, from the pipeline). The month math must
        // treat "Card", "CARD", and "card" identically (COLLATE NOCASE). A regression
        // here is silent: Card spends re-enter Actual Spent and cardOutstanding misses.
        val titleCaseCardId = repository.insert(
            Transaction(
                amount = 300.0,
                type = TransactionType.DEBIT,
                merchant = "Mixed Case Store",
                categoryId = 3L,
                sourceApp = "SMS",
                rawText = "Spent 300",
                confidence = 0.95f,
                status = TransactionStatus.CONFIRMED,
                timestamp = 5_000L,
                paymentMethod = "Card",
                isLoanGiven = false
            )
        )
        // Expected (writer-agnostic math): actual=1500 (Card excluded), cardOutstanding=5000+250+300=5550
        // Buggy case-sensitive math would give: actual=1800 ("Card" != 'CARD'), cardOutstanding=5250
        assertEquals(1_500.0, repository.sumActualSpent(start, end))
        assertEquals(5_550.0, repository.sumCardOutstanding(start, end))

        // And the Review path's card filter round-trips: flipping it back to UPI restores it to Actual Spent
        repository.updatePaymentMethod(titleCaseCardId, "UPI")
        assertEquals(1_800.0, repository.sumActualSpent(start, end))
        assertEquals(5_250.0, repository.sumCardOutstanding(start, end))
    }
}
