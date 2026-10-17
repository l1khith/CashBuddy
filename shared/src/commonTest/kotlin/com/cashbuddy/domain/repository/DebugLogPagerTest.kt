// NO-NETWORK
package com.cashbuddy.domain.repository

import com.cashbuddy.domain.model.DebugLogEntry
import com.cashbuddy.domain.model.DebugLogFilter
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DebugLogPagerTest {

    private fun createLog(id: String, timestamp: Long): DebugLogEntry {
        return DebugLogEntry(
            id = id,
            timestamp = timestamp,
            sourceType = "NOTIFICATION",
            packageName = "com.test.app",
            senderId = null,
            rawTitle = "Alert",
            rawText = "Spent 100",
            rawTextHash = null,
            detectedSource = "TEST",
            evidenceJson = null,
            pTransaction = 0.95,
            contributionsJson = null,
            fieldConfidencesJson = null,
            policyAction = "AUTO_LOG",
            pipelineOutcome = "LOGGED",
            resultingTxId = null,
            mergeTargetId = null,
            errorMessage = null
        )
    }

    private class FakeDebugLogRepo(private val allLogs: List<DebugLogEntry>) : DebugLogRepository {
        override suspend fun insert(entry: DebugLogEntry) {}
        override suspend fun updateClassification(id: String, detectedSource: String?, evidenceJson: String?, pTransaction: Double?, contributionsJson: String?, fieldConfidencesJson: String?) {}
        override suspend fun updatePolicy(id: String, policyAction: String?) {}
        override suspend fun updateOutcome(id: String, policyAction: String?, pipelineOutcome: String?, resultingTxId: String?, mergeTargetId: String?, errorMessage: String?) {}
        override suspend fun updateRawText(id: String, rawText: String) {}
        override suspend fun getById(id: String): DebugLogEntry? = allLogs.find { it.id == id }
        override suspend fun countWithFilter(filter: DebugLogFilter): Long = allLogs.size.toLong()
        override suspend fun distinctPackages(): List<String> = emptyList()
        override suspend fun query(filter: DebugLogFilter): List<DebugLogEntry> = allLogs
        override suspend fun recent(limit: Int): List<DebugLogEntry> = allLogs.take(limit)
        override suspend fun clearAll() {}
        override suspend fun count(): Long = allLogs.size.toLong()

        override suspend fun filterLogsPage(
            filter: DebugLogFilter,
            cursorTimestamp: Long?,
            cursorId: String?,
            limit: Long
        ): List<DebugLogEntry> {
            return allLogs
                .filter { log ->
                    if (cursorTimestamp != null && cursorId != null) {
                        if (log.timestamp > cursorTimestamp) return@filter false
                        if (log.timestamp == cursorTimestamp && log.id >= cursorId) return@filter false
                    }
                    true
                }
                .sortedWith(compareByDescending<DebugLogEntry> { it.timestamp }.thenByDescending { it.id })
                .take(limit.toInt())
        }
    }

    @Test
    fun testDebugLogKeysetPagination() = runBlocking {
        val items = (1..12).map { id -> createLog("id_$id", timestamp = id * 1000L) }
        val repo = FakeDebugLogRepo(items)
        val pager = DebugLogPager(repo, pageSize = 5L)

        // Page 1: 5 items
        val page1 = pager.loadNextPage()
        assertEquals(5, page1.size)
        assertEquals("id_12", page1.first().id)
        assertEquals("id_8", page1.last().id)
        assertFalse(pager.isEndReached)
        assertEquals(8000L, pager.cursorTimestamp)
        assertEquals("id_8", pager.cursorId)

        // Page 2: 5 items
        val page2 = pager.loadNextPage()
        assertEquals(5, page2.size)
        assertEquals("id_7", page2.first().id)
        assertEquals("id_3", page2.last().id)
        assertFalse(pager.isEndReached)

        // Page 3: 2 items -> end reached
        val page3 = pager.loadNextPage()
        assertEquals(2, page3.size)
        assertEquals("id_2", page3.first().id)
        assertEquals("id_1", page3.last().id)
        assertTrue(pager.isEndReached)

        // Page 4: empty
        val page4 = pager.loadNextPage()
        assertTrue(page4.isEmpty())

        // Reset
        pager.reset()
        assertFalse(pager.isEndReached)
        val page1Reset = pager.loadNextPage()
        assertEquals(5, page1Reset.size)
        assertEquals("id_12", page1Reset.first().id)
    }
}
