package com.cashbuddy.domain

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.cashbuddy.core.prob.TokenEvidenceExtractor
import com.cashbuddy.data.repository.LearnedTokenRepositoryImpl
import com.cashbuddy.db.AppDatabase
import kotlinx.coroutines.runBlocking
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LearnedTokenSafeguardsTest {

    private lateinit var database: AppDatabase
    private lateinit var repository: LearnedTokenRepositoryImpl
    private lateinit var extractor: TokenEvidenceExtractor

    @BeforeTest
    fun setup() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        AppDatabase.Schema.create(driver)
        database = AppDatabase(driver)
        repository = LearnedTokenRepositoryImpl(database)
        extractor = TokenEvidenceExtractor(repository)
    }

    @Test
    fun testOneObservationToken_IsIgnoredByExtractor() = runBlocking {
        // Record 1 observation (rare/noise word)
        repository.record(isTransaction = true, tokens = setOf("crypto123"))

        val evidence = extractor.extract("payment to crypto123 successful")
        // < 5 observations must contribute 0.0 log-odds
        assertEquals(0.0, evidence.tokenLogOdds)
        assertEquals(0, evidence.tokenCount)
        assertNull(evidence.strongestToken)
    }

    @Test
    fun testFiveObservationsToken_IsRecognizedAndEvaluated() = runBlocking {
        // Record 5 observations to meet the >=5 significance gate
        repeat(5) {
            repository.record(isTransaction = true, tokens = setOf("swiggy"))
        }

        val evidence = extractor.extract("order at swiggy completed")
        assertTrue(evidence.tokenCount >= 1)
        assertEquals("swiggy", evidence.strongestToken)
        assertTrue(evidence.tokenLogOdds > 0.0)
    }

    @Test
    fun testPurgeExcessTokens_DropsRarestAndLeavesMaxCount() = runBlocking {
        val now = 1000L
        val queries = database.learned_tokenQueries

        // Seed 10 distinct tokens
        for (i in 1..10) {
            val token = "token_$i"
            val count = i.toLong() // token_1 has 1 observation, token_10 has 10
            queries.incrementTransaction(token, now, now)
            if (count > 1) {
                for (c in 2..count) {
                    queries.incrementTransaction(token, now, now)
                }
            }
        }

        assertEquals(10L, queries.getCount().executeAsOne())

        // Purge to max 4 tokens (should drop the 6 rarest: token_1 to token_6)
        val purgedCount = repository.purgeExcessTokens(maxCount = 4L)
        assertEquals(6, purgedCount)
        assertEquals(4L, queries.getCount().executeAsOne())

        // The remaining tokens must be token_7, token_8, token_9, token_10
        val remaining = queries.getAll().executeAsList().map { it.token }.toSet()
        assertEquals(setOf("token_7", "token_8", "token_9", "token_10"), remaining)
    }
}
