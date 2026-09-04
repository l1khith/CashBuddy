package com.cashbuddy.presentation.review

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cashbuddy.domain.repository.CategoryRepository
import com.cashbuddy.domain.repository.TransactionRepository
import com.cashbuddy.domain.usecase.ConfirmTransactionUseCase
import com.cashbuddy.domain.usecase.ModifyTransactionUseCase
import com.cashbuddy.domain.usecase.RejectTransactionUseCase
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class ReviewViewModel(
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository,
    private val confirmTransactionUseCase: ConfirmTransactionUseCase,
    private val rejectTransactionUseCase: RejectTransactionUseCase,
    private val modifyTransactionUseCase: ModifyTransactionUseCase,
    private val reducer: ReviewReducer = ReviewReducer()
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReviewState())
    val uiState: StateFlow<ReviewState> = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<ReviewEffect>()
    val effects: SharedFlow<ReviewEffect> = _effects.asSharedFlow()

    init {
        processIntent(ReviewIntent.LoadPending)
    }

    fun processIntent(intent: ReviewIntent) {
        when (intent) {
            is ReviewIntent.LoadPending -> loadPending()
            is ReviewIntent.ConfirmTransaction -> confirmTransaction(intent.transactionId)
            is ReviewIntent.RejectTransaction -> rejectTransaction(intent.transactionId)
            is ReviewIntent.ModifyCategory -> modifyCategory(intent.transactionId, intent.newCategoryId)
            is ReviewIntent.FilterByConfidence -> filterConfidence(intent.minConfidence)
            is ReviewIntent.ConfirmAllHighConfidence -> confirmAllHighConfidence()
        }
    }

    private fun loadPending() {
        viewModelScope.launch {
            combine(
                transactionRepository.getPending(),
                categoryRepository.getAll()
            ) { pendingTxs, categories ->
                ReviewResult.Loaded(pendingTxs, categories)
            }.collect { result ->
                _uiState.value = reducer.reduce(_uiState.value, result)
            }
        }
    }

    private fun confirmTransaction(transactionId: Long) {
        viewModelScope.launch {
            confirmTransactionUseCase(transactionId)
            _uiState.value = reducer.reduce(_uiState.value, ReviewResult.TransactionConfirmed(transactionId))
            _effects.emit(ReviewEffect.ShowSnackbar("Transaction confirmed"))
        }
    }

    private fun rejectTransaction(transactionId: Long) {
        viewModelScope.launch {
            rejectTransactionUseCase(transactionId)
            _uiState.value = reducer.reduce(_uiState.value, ReviewResult.TransactionRejected(transactionId))
            _effects.emit(ReviewEffect.ShowSnackbar("Transaction discarded"))
        }
    }

    private fun modifyCategory(transactionId: Long, newCategoryId: Long) {
        viewModelScope.launch {
            val tx = _uiState.value.transactions.find { it.id == transactionId } ?: return@launch
            modifyTransactionUseCase(tx.copy(categoryId = newCategoryId))
            _uiState.value = reducer.reduce(_uiState.value, ReviewResult.CategoryModified(transactionId, newCategoryId))
        }
    }

    private fun filterConfidence(minConfidence: Float) {
        _uiState.value = reducer.reduce(_uiState.value, ReviewResult.ConfidenceFilterChanged(minConfidence))
    }

    private fun confirmAllHighConfidence() {
        viewModelScope.launch {
            val highConfidence = _uiState.value.transactions.filter { it.confidence >= 0.85f }
            highConfidence.forEach { tx ->
                confirmTransactionUseCase(tx.id)
            }
            _uiState.value = reducer.reduce(_uiState.value, ReviewResult.AllHighConfidenceConfirmed)
            _effects.emit(ReviewEffect.ShowSnackbar("Confirmed ${highConfidence.size} high-confidence transactions"))
        }
    }
}
