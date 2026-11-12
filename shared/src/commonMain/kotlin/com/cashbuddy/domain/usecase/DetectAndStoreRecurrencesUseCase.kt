package com.cashbuddy.domain.usecase

import com.cashbuddy.core.analytics.RecurrenceDetector
import com.cashbuddy.domain.model.RecurringPattern
import com.cashbuddy.domain.repository.RecurringPatternRepository
import com.cashbuddy.domain.repository.TransactionRepository
import com.cashbuddy.platform.currentTimeMillis
import kotlinx.coroutines.flow.firstOrNull

class DetectAndStoreRecurrencesUseCase(
    private val transactionRepository: TransactionRepository,
    private val recurringPatternRepository: RecurringPatternRepository
) {
    suspend operator fun invoke(now: Long = currentTimeMillis()): List<RecurringPattern> {
        val thirtyDaysAgo = now - RecurrenceDetector.WINDOW_MS
        val transactions = transactionRepository.getByDateRange(thirtyDaysAgo, now).firstOrNull() ?: emptyList()
        val patterns = RecurrenceDetector.detect(transactions, now)
        if (patterns.isNotEmpty()) {
            recurringPatternRepository.upsertAll(patterns)
        }
        return patterns
    }
}
