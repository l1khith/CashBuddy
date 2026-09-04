package com.cashbuddy.domain.usecase

import com.cashbuddy.domain.model.Transaction
import com.cashbuddy.domain.repository.CategoryBreakdown
import com.cashbuddy.domain.repository.MonthlySummary
import com.cashbuddy.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class CalculateBalanceUseCase(private val repository: TransactionRepository) {
    operator fun invoke(): Flow<Double> = repository.getBalance()
}

class GetRecentTransactionsUseCase(private val repository: TransactionRepository) {
    operator fun invoke(limit: Int = 10): Flow<List<Transaction>> =
        repository.getAll().map { list -> list.take(limit) }
}

class GetUnreviewedCountUseCase(private val repository: TransactionRepository) {
    operator fun invoke(): Flow<Int> =
        repository.getPending().map { it.size }
}

class GetMonthlySummaryUseCase(private val repository: TransactionRepository) {
    operator fun invoke(): Flow<List<MonthlySummary>> = repository.getMonthlySummary()
}

class GenerateCategoryBreakdownUseCase(private val repository: TransactionRepository) {
    operator fun invoke(startDate: Long, endDate: Long): Flow<List<CategoryBreakdown>> =
        repository.getCategoryBreakdown(startDate, endDate)
}
