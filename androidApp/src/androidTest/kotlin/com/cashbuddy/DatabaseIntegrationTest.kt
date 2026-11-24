package com.cashbuddy

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cashbuddy.core.CategoryEngine
import com.cashbuddy.core.prob.TokenEvidenceExtractor
import com.cashbuddy.domain.model.CategoryType
import com.cashbuddy.domain.model.Transaction
import com.cashbuddy.domain.model.TransactionStatus
import com.cashbuddy.domain.model.TransactionType
import com.cashbuddy.domain.repository.AccountRepository
import com.cashbuddy.domain.repository.CategoryRepository
import com.cashbuddy.domain.repository.LearnedTokenRepository
import com.cashbuddy.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

@RunWith(AndroidJUnit4::class)
class DatabaseIntegrationTest : KoinComponent {

    private lateinit var categoryRepository: CategoryRepository
    private lateinit var accountRepository: AccountRepository
    private lateinit var transactionRepository: TransactionRepository
    private lateinit var learnedTokenRepository: LearnedTokenRepository
    private lateinit var categoryEngine: CategoryEngine

    @Before
    fun setup() = runBlocking {
        categoryRepository = get()
        accountRepository = get()
        transactionRepository = get()
        learnedTokenRepository = get()
        categoryEngine = get()

        // Seed initial default categories and accounts
        val now = System.currentTimeMillis()
        categoryRepository.seedDefaults(now)
        accountRepository.seedDefaults(now)
    }

    @Test
    fun test_Database_DeleteTransaction_Cascade(): Unit = runBlocking {
        // 1. Insert a custom Category "Pet Care"
        val petCareId = categoryRepository.insertCustom(
            name = "Pet Care",
            type = CategoryType.EXPENSE,
            icon = "pets",
            color = "#FF8800"
        )
        assertTrue("Category Pet Care was not inserted", petCareId > 0)

        // 2. Insert a Transaction linked to "Pet Care"
        val now = System.currentTimeMillis()
        val tx = Transaction(
            id = 0L,
            amount = 750.0,
            type = TransactionType.DEBIT,
            currency = "INR",
            merchant = "Fluffy Pet Store",
            categoryId = petCareId,
            accountId = null,
            sourceApp = "manual",
            rawText = "Paid 750 at Fluffy Pet Store",
            confidence = 1.0f,
            status = TransactionStatus.CONFIRMED,
            notes = "Dog food",
            timestamp = now,
            createdAt = now,
            updatedAt = now
        )
        val txId = transactionRepository.insert(tx)
        assertTrue("Transaction was not inserted", txId > 0)

        // 3. Delete the Category
        categoryRepository.deleteCustom(petCareId)

        // Verify the Category is gone
        val deletedCategory = categoryRepository.getById(petCareId).firstOrNull()
        assertNull("Category should have been deleted", deletedCategory)

        // 4. Verify the Transaction's category falls back to "Uncategorized" and app does not crash
        val updatedTx = transactionRepository.getById(txId).firstOrNull()
        assertNotNull("Transaction must still exist after category deletion", updatedTx)

        val fallbackCategory = categoryRepository.getById(updatedTx!!.categoryId).firstOrNull()
        assertNotNull("Fallback category must exist", fallbackCategory)
        assertEquals(
            "Transaction category must fallback to Uncategorized",
            "Uncategorized",
            fallbackCategory!!.name
        )
    }

    @Test
    fun test_Database_LearnedToken_CacheUpdate(): Unit = runBlocking {
        val extractor = TokenEvidenceExtractor(learnedTokenRepository)

        // 1. Insert token "zomato" with 5 non-transaction observations
        repeat(5) {
            learnedTokenRepository.record(isTransaction = false, tokens = setOf("zomato"))
        }

        val stats = learnedTokenRepository.getTokenStats(setOf("zomato"))
        assertTrue("Token stats for zomato not found", stats.containsKey("zomato"))
        assertEquals(5, stats["zomato"]!!.nonTransactionCount)

        // 2. Call TokenEvidenceExtractor and CategoryEngine to evaluate message with "zomato"
        val message = "Great food discounts on zomato today"
        val evidence = extractor.extract(message)

        // Verify low probability (negative log-odds, LR < 1.0)
        assertTrue("Learned token should be recognized in text", evidence.tokenCount >= 1)
        assertEquals("zomato", evidence.strongestToken)
        assertTrue("Non-transaction observations must yield negative log-odds", evidence.tokenLogOdds < 0.0)
        assertTrue("Likelihood ratio for non-transaction token must be < 1.0", evidence.strongestLR < 1.0)

        // CategoryEngine evaluation
        val categoryMatch = categoryEngine.getCategory("zomato")
        assertEquals(
            "CategoryEngine must assign low/zero confidence to unmapped promo token",
            0.0f,
            categoryMatch.confidence,
            0.01f
        )
        assertEquals("Unknown", categoryMatch.category)
    }
}
