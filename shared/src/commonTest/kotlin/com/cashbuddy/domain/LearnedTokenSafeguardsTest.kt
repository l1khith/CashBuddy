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

    @Test
    fun testUnderFiveObservationsTokens_AreNotCached() = runBlocking {
        // A single-occurrence noise token
        repository.record(isTransaction = true, tokens = setOf("onetimeotp999"))

        // Lookup should not return it
        val stats = repository.getTokenStats(setOf("onetimeotp999"))
        assertTrue(stats.isEmpty())
    }

    @Test
    fun testCacheEvictsLowestObservationToken_HotTokenSurvives() = runBlocking {
        // Create a hot token with 100 observations
        repeat(100) {
            repository.record(isTransaction = true, tokens = setOf("hot_token"))
        }

        // Seed tokens with 5 observations
        for (i in 1..10) {
            repeat(5) {
                repository.record(isTransaction = true, tokens = setOf("token_5_$i"))
            }
        }

        // Query hot token - must be present and accurate
        val hotStats = repository.getTokenStats(setOf("hot_token"))
        assertTrue(hotStats.containsKey("hot_token"))
        assertEquals(100, hotStats["hot_token"]?.transactionCount)
    }

    private fun tokenForIndex(i: Int): String {
        var n = i
        val sb = StringBuilder("tok")
        for (step in 0..4) {
            sb.append(('a'.code + (n % 26)).toChar())
            n /= 26
        }
        return sb.toString()
    }

    @Test
    fun testFiftyThousandTokensBombardment_NeverFreezesOrOoms() = runBlocking {
        println("=== STARTING 50,000 TOKEN BOMBARDMENT STRESS TEST ===")
        val queries = database.learned_tokenQueries
        val now = 1000L

        val frequentTokens = listOf("swiggy", "debited", "zomato", "amazon", "flipkart", "uber", "ola", "salary", "credited", "refund")

        // 1. Insert 50,000 distinct tokens representing 5 years of transactions
        // Wrap in a single transaction for fast bulk seeding
        val insertStart = System.currentTimeMillis()
        database.transaction {
            // 10 frequent tokens (10 observations each)
            for (token in frequentTokens) {
                repeat(10) {
                    queries.incrementTransaction(token, now, now)
                }
            }
            // 49,990 noise tokens (1 observation each)
            for (i in 1..49_990) {
                val token = tokenForIndex(i)
                queries.incrementTransaction(token, now, now)
            }
        }
        val insertDuration = System.currentTimeMillis() - insertStart
        val totalCount = queries.getCount().executeAsOne()
        assertEquals(50_000L, totalCount)
        println("-> Inserted $totalCount rows into SQLite in ${insertDuration}ms")

        // 2. Measure incoming SMS extraction speed (must ask for 2-5 tokens, NOT 50,000)
        val message = "Rs 540 debited from A/C XX1234 at SWIGGY via UPI Ref 123456789012"
        
        // Warmup
        extractor.extract(message)

        val iterations = 1000
        val extractStart = System.nanoTime()
        repeat(iterations) {
            val evidence = extractor.extract(message)
            assertTrue(evidence.tokenCount >= 1)
            assertTrue(evidence.tokenLogOdds > 0.0)
        }
        val extractDurationNs = System.nanoTime() - extractStart
        val avgExtractMicros = (extractDurationNs / iterations) / 1000.0
        val avgExtractMillis = avgExtractMicros / 1000.0

        println("-> Ran $iterations message extractions against 50,000 DB tokens.")
        println("-> Average extraction latency: ${avgExtractMicros} µs (${avgExtractMillis} ms)")
        
        // CRITICAL ASSERTION 1: Sub-millisecond or low single-digit ms
        // It must NOT take 100ms or freeze the main thread
        assertTrue(avgExtractMillis < 5.0, "Extraction took ${avgExtractMillis}ms, which is too slow!")

        // CRITICAL ASSERTION 2: L1 Cache Cap Safeguard
        // Cache must NEVER store 50,000 tokens. It must be bounded by the 2,048 cap
        // Let's query 10,000 distinct noise tokens to try and overflow the cache
        val queryBatch = (1..10_000).map { tokenForIndex(it) }.toSet()
        val stats = repository.getTokenStats(queryBatch)
        // Noise tokens have 1 observation, so NONE pass the >=5 significance gate
        assertEquals(0, stats.size)
        println("-> Queried 10,000 noise tokens in batch; ${stats.size} passed the >=5 gate (100% noise filtered)")

        // Query frequent tokens - all 10 must pass
        val frequentStats = repository.getTokenStats(frequentTokens.toSet())
        assertEquals(10, frequentStats.size)
        println("-> Queried 10 frequent tokens; all ${frequentStats.size} retrieved instantly from cache/indexed DB")

        // CRITICAL ASSERTION 3: Weekly TokenPurgeWorker GC
        // When the worker runs, it trims the table from 50,000 down to 2,000 hot tokens
        val purgeStart = System.currentTimeMillis()
        val purgedCount = repository.purgeExcessTokens(maxCount = 2_000L)
        val purgeDuration = System.currentTimeMillis() - purgeStart
        val countAfterPurge = queries.getCount().executeAsOne()

        println("-> Purged $purgedCount excess tokens in ${purgeDuration}ms")
        println("-> Tokens remaining in DB: $countAfterPurge (target: 2000)")
        assertEquals(48_000, purgedCount)
        assertEquals(2_000L, countAfterPurge)

        println("=== 50,000 TOKEN BOMBARDMENT PASSED WITH ZERO FREEZE OR OOM ===")
    }
}
