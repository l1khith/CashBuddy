package com.cashbuddy.presentation.review

import com.cashbuddy.domain.model.Category
import com.cashbuddy.domain.model.Transaction

sealed interface ReviewIntent {
    data object LoadPending : ReviewIntent
    data class ConfirmTransaction(val transactionId: Long) : ReviewIntent
    data class RejectTransaction(val transactionId: Long) : ReviewIntent
    data class ModifyCategory(val transactionId: Long, val newCategoryId: Long) : ReviewIntent
    data class FilterByConfidence(val minConfidence: Float) : ReviewIntent
    data object ConfirmAllHighConfidence : ReviewIntent
}

data class ReviewState(
    val transactions: List<Transaction> = emptyList(),
    val allCategories: List<Category> = emptyList(),
    val isLoading: Boolean = true,
    val selectedConfidenceFilter: Float = 0.0f,
    val error: String? = null
) {
    val highConfidenceCount: Int
        get() = transactions.count { it.confidence >= 0.85f }
}

sealed interface ReviewEffect {
    data class ShowSnackbar(val message: String) : ReviewEffect
}
