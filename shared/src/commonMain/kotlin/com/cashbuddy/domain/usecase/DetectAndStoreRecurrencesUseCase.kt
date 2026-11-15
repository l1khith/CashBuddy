package com.cashbuddy.domain.usecase

import com.cashbuddy.core.analytics.RecurrenceDetector
import com.cashbuddy.domain.model.RecurringPattern
import com.cashbuddy.domain.repository.RecurringPatternRepository
import com.cashbuddy.domain.repository.TransactionRepository
import com.cashbuddy.platform.currentTimeMillis
import com.cashbuddy.platform.ioDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext

class DetectAndStoreRecurrencesUseCase(
    private val transactionRepository: TransactionRepository,
    private val recurringPatternRepository: RecurringPatternRepository
) {
    suspend operator fun invoke(now: Long = currentTimeMillis()): List<RecurringPattern> =
        withContext(ioDispatcher) {
            val windowStart = now - RecurrenceDetector.WINDOW_MS
            val transactions = transactionRepository.getByDateRange(windowStart, now).firstOrNull() ?: emptyList()
            val patterns = withContext(Dispatchers.Default) {
                RecurrenceDetector.detect(transactions, now)
            }
            if (patterns.isNotEmpty()) {
                recurringPatternRepository.upsertAll(patterns)
            }
            patterns
        }
}
