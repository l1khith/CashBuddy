// NO-NETWORK
package com.cashbuddy.data.repository

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.cashbuddy.core.CategoryEngine
import com.cashbuddy.db.AppDatabase
import com.cashbuddy.domain.model.BudgetPeriod
import com.cashbuddy.domain.model.CategoryType
import com.cashbuddy.domain.model.Transaction
import com.cashbuddy.domain.model.TransactionStatus
import com.cashbuddy.domain.model.TransactionType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CategoryRepositoryTest {

    private lateinit var database: AppDatabase
    private lateinit var categoryEngine: CategoryEngine
    private lateinit var repository: CategoryRepositoryImpl
    private lateinit var transactionRepository: TransactionRepositoryImpl

    @BeforeTest
    fun setup() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        AppDatabase.Schema.create(driver)
        database = AppDatabase(driver)
        categoryEngine = CategoryEngine()
        repository = CategoryRepositoryImpl(
            db = database,
            categoryEngine = categoryEngine
        )
        transactionRepository = TransactionRepositoryImpl(database)

        // Seed defaults
        runBlocking {
            repository.seedDefaults(1000L)
        }
    }

    @Test
    fun testDefaultCategoriesSeededSuccessfully() = runBlocking {
        val categories = repository.getAll().first()
        assertTrue(categories.isNotEmpty(), "Default categories should not be empty")
        assertEquals(15, categories.size, "Should have seeded exactly 15 default categories")

        val uncategorized = categories.find { it.name.equals("Uncategorized", ignoreCase = true) }
        assertNotNull(uncategorized, "Uncategorized category must exist")
        assertTrue(uncategorized.isDefault, "Uncategorized category must be marked as default")
    }

    @Test
    fun testInsertCustomCategory() = runBlocking {
        val customId = repository.insertCustom(
            name = "Pet Care",
            type = CategoryType.EXPENSE,
            icon = "shopping_bag",
            color = "#10B981"
        )
        assertTrue(customId > 0, "Custom ID should be positive")

        val retrieved = repository.getById(customId).first()
        assertNotNull(retrieved, "Retrieved category must not be null")
        assertEquals("Pet Care", retrieved.name)
        assertEquals(CategoryType.EXPENSE, retrieved.type)
        assertEquals("shopping_bag", retrieved.icon)
        assertEquals("#10B981", retrieved.color)
        assertFalse(retrieved.isDefault, "Custom category must have isDefault = false")
    }

    @Test
    fun testCaseInsensitiveDuplicateRejection() {
        runBlocking {
            // System category collision
            assertFailsWith<IllegalArgumentException> {
                repository.insertCustom(
                    name = "food & dining", // collision with 'Food & Dining'
                    type = CategoryType.EXPENSE,
                    icon = "restaurant",
                    color = "#FF0000"
                )
            }

            // Custom category collision
            repository.insertCustom("Crypto", CategoryType.EXPENSE, "trending_up", "#6366F1")
            assertFailsWith<IllegalArgumentException> {
                repository.insertCustom("crypto", CategoryType.EXPENSE, "trending_up", "#6366F1")
            }
        }
    }

    @Test
    fun testCannotDeleteSystemCategory() {
        runBlocking {
            val categories = repository.getAll().first()
            val systemCategory = categories.first { it.isDefault }

            assertFailsWith<IllegalStateException> {
                repository.deleteCustom(systemCategory.id)
            }

            // Verify still exists
            val stillExists = repository.getById(systemCategory.id).first()
            assertNotNull(stillExists)
        }
    }

    @Test
    fun testAtomicDeleteCascadeReassignsTransactionsAndRules() = runBlocking {
        // 1. Create custom category
        val customId = repository.insertCustom(
            name = "Gym & Fitness",
            type = CategoryType.EXPENSE,
            icon = "sports",
            color = "#F43F5E"
        )

        // 2. Insert transaction using custom category
        val txId = transactionRepository.insert(
            Transaction(
                amount = 2500.0,
                type = TransactionType.DEBIT,
                merchant = "CultFit",
                categoryId = customId,
                sourceApp = "notification",
                rawText = "Paid 2500 at CultFit",
                confidence = 0.95f,
                status = TransactionStatus.CONFIRMED,
                timestamp = 2000L
            )
        )

        // 3. Insert merchant rule pointing to custom category
        database.merchant_rulesQueries.insert(
            pattern = "cultfit",
            category_id = customId,
            is_regex = false,
            priority = 100L,
            created_at = 2000L
        )

        // 4. Insert budget for "Gym & Fitness"
        database.budgetQueries.insert(
            id = "budget_gym",
            category = "Gym & Fitness",
            amount = 3000.0,
            period = BudgetPeriod.MONTHLY.name,
            start_date = 2000L,
            is_active = 1L,
            last_alert_state = "ON_TRACK",
            last_alert_at = null,
            created_at = 2000L,
            updated_at = 2000L
        )

        // 5. Delete custom category
        repository.deleteCustom(customId)

        // 6. Verify custom category is deleted
        val deleted = repository.getById(customId).first()
        assertNull(deleted, "Category should be deleted")

        // 7. Verify transaction was reassigned to Uncategorized dynamically
        val reassignedTx = transactionRepository.getById(txId).first()
        assertNotNull(reassignedTx)
        val uncategorized = repository.getByName("Uncategorized")
        assertNotNull(uncategorized)
        assertEquals(uncategorized.id, reassignedTx.categoryId, "Transaction should be reassigned to dynamic Uncategorized ID")

        // 8. Verify merchant rule was reassigned to Uncategorized
        val rule = database.merchant_rulesQueries.findByPattern("cultfit").executeAsOneOrNull()
        assertNotNull(rule)
        assertEquals(uncategorized.id, rule.category_id, "Rule should be reassigned to Uncategorized ID")

        // 9. Verify budget was reassigned to "Uncategorized"
        val budget = database.budgetQueries.getById("budget_gym").executeAsOneOrNull()
        assertNotNull(budget)
        assertEquals("Uncategorized", budget.category, "Budget should be reassigned to Uncategorized")
    }
}
