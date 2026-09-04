package com.cashbuddy.presentation.review

import com.cashbuddy.domain.model.Category
import com.cashbuddy.domain.model.Transaction

sealed interface ReviewResult {
    data class Loaded(val transactions: List<Transaction>, val categories: List<Category>) : ReviewResult
    data class TransactionConfirmed(val transactionId: Long) : ReviewResult
    data class TransactionRejected(val transactionId: Long) : ReviewResult
    data class CategoryModified(val transactionId: Long, val newCategoryId: Long) : ReviewResult
    data class ConfidenceFilterChanged(val minConfidence: Float) : ReviewResult
    data object AllHighConfidenceConfirmed : ReviewResult
    data class Error(val message: String) : ReviewResult
}

class ReviewReducer {
    fun reduce(currentState: ReviewState, result: ReviewResult): ReviewState {
        return when (result) {
            is ReviewResult.Loaded -> currentState.copy(
                transactions = result.transactions,
                allCategories = result.categories,
                isLoading = false,
                error = null
            )
            is ReviewResult.TransactionConfirmed -> currentState.copy(
                transactions = currentState.transactions.filterNot { it.id == result.transactionId }
            )
            is ReviewResult.TransactionRejected -> currentState.copy(
                transactions = currentState.transactions.filterNot { it.id == result.transactionId }
            )
            is ReviewResult.CategoryModified -> currentState.copy(
                transactions = currentState.transactions.map { tx ->
                    if (tx.id == result.transactionId) tx.copy(categoryId = result.newCategoryId) else tx
                }
            )
            is ReviewResult.ConfidenceFilterChanged -> currentState.copy(
                selectedConfidenceFilter = result.minConfidence
            )
            is ReviewResult.AllHighConfidenceConfirmed -> currentState.copy(
                transactions = currentState.transactions.filterNot { it.confidence >= 0.85f }
            )
            is ReviewResult.Error -> currentState.copy(
                isLoading = false,
                error = result.message
            )
        }
    }
}
