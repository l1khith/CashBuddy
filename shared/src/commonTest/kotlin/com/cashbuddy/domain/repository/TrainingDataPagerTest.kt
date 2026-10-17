// NO-NETWORK
package com.cashbuddy.domain.repository

import com.cashbuddy.domain.model.RawTrainingData
import com.cashbuddy.domain.model.TrainingStats
import com.cashbuddy.domain.model.UserCorrection
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TrainingDataPagerTest {

    private fun createRaw(id: Long, timestamp: Long): RawTrainingData {
        return RawTrainingData(
            id = id,
            rawText = "Raw notification $id",
            source = "notification",
            sourceApp = "com.bank.app",
            extractedAmount = 100.0,
            extractedType = "DEBIT",
            extractedMerchant = "Merchant $id",
            timestamp = timestamp
        )
    }

    private fun createCorrection(id: Long, timestamp: Long): UserCorrection {
        return UserCorrection(
            id = id,
            merchant = "Merchant $id",
            sourceApp = "com.bank.app",
            rawText = "Text $id",
            oldCategory = "Uncategorized",
            newCategory = "Food",
            timestamp = timestamp
        )
    }

    private class FakeTrainingDataRepo(
        private val allRaw: List<RawTrainingData>,
        private val allCorrections: List<UserCorrection>
    ) : TrainingDataRepository {
        override suspend fun recordRawNotification(
            rawText: String,
            source: String,
            sourceApp: String?,
            extractedAmount: Double?,
            extractedType: String?,
            extractedMerchant: String?,
            timestamp: Long
        ): Long = 1L

        override suspend fun recordCorrection(
            merchant: String,
            sourceApp: String?,
            rawText: String?,
            oldCategory: String?,
            newCategory: String,
            timestamp: Long
        ): Long = 1L

        override fun getAllRaw(): Flow<List<RawTrainingData>> = emptyFlow()
        override fun getAllCorrections(): Flow<List<UserCorrection>> = emptyFlow()
        override fun getStats(): Flow<TrainingStats> = emptyFlow()
        override suspend fun exportTrainingDataJsonl(): String = ""
        override suspend fun clearTrainingData() {}

        override suspend fun getRawPage(cursorTimestamp: Long?, cursorId: Long?, limit: Long): List<RawTrainingData> {
            return allRaw
                .filter { r ->
                    if (cursorTimestamp != null && cursorId != null) {
                        if (r.timestamp > cursorTimestamp) return@filter false
                        if (r.timestamp == cursorTimestamp && r.id >= cursorId) return@filter false
                    }
                    true
                }
                .sortedWith(compareByDescending<RawTrainingData> { it.timestamp }.thenByDescending { it.id })
                .take(limit.toInt())
        }

        override suspend fun getCorrectionsPage(cursorTimestamp: Long?, cursorId: Long?, limit: Long): List<UserCorrection> {
            return allCorrections
                .filter { c ->
                    if (cursorTimestamp != null && cursorId != null) {
                        if (c.timestamp > cursorTimestamp) return@filter false
                        if (c.timestamp == cursorTimestamp && c.id >= cursorId) return@filter false
                    }
                    true
                }
                .sortedWith(compareByDescending<UserCorrection> { it.timestamp }.thenByDescending { it.id })
                .take(limit.toInt())
        }
    }

    @Test
    fun testRawTrainingDataKeysetPagination() = runBlocking {
        val rawList = (1..12).map { id -> createRaw(id.toLong(), timestamp = id * 1000L) }
        val repo = FakeTrainingDataRepo(rawList, emptyList())
        val pager = RawTrainingDataPager(repo, pageSize = 5L)

        val page1 = pager.loadNextPage()
        assertEquals(5, page1.size)
        assertEquals(12L, page1.first().id)
        assertEquals(8L, page1.last().id)
        assertFalse(pager.isEndReached)

        val page2 = pager.loadNextPage()
        assertEquals(5, page2.size)
        assertEquals(7L, page2.first().id)
        assertEquals(3L, page2.last().id)
        assertFalse(pager.isEndReached)

        val page3 = pager.loadNextPage()
        assertEquals(2, page3.size)
        assertEquals(2L, page3.first().id)
        assertEquals(1L, page3.last().id)
        assertTrue(pager.isEndReached)

        pager.reset()
        assertFalse(pager.isEndReached)
        val resetPage = pager.loadNextPage()
        assertEquals(5, resetPage.size)
        assertEquals(12L, resetPage.first().id)
    }

    @Test
    fun testUserCorrectionKeysetPagination() = runBlocking {
        val corrections = (1..7).map { id -> createCorrection(id.toLong(), timestamp = id * 1000L) }
        val repo = FakeTrainingDataRepo(emptyList(), corrections)
        val pager = UserCorrectionPager(repo, pageSize = 5L)

        val page1 = pager.loadNextPage()
        assertEquals(5, page1.size)
        assertEquals(7L, page1.first().id)
        assertEquals(3L, page1.last().id)
        assertFalse(pager.isEndReached)

        val page2 = pager.loadNextPage()
        assertEquals(2, page2.size)
        assertEquals(2L, page2.first().id)
        assertEquals(1L, page2.last().id)
        assertTrue(pager.isEndReached)
    }
}
