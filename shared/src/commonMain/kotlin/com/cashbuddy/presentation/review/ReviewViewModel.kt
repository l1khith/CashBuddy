package com.cashbuddy.presentation.review

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cashbuddy.core.prob.Tokenizer
import com.cashbuddy.domain.model.TransactionStatus
import com.cashbuddy.domain.model.TransactionType
import com.cashbuddy.domain.repository.CategoryRepository
import com.cashbuddy.domain.repository.LearnedTokenRepository
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
    private val reducer: ReviewReducer = ReviewReducer(),
    private val learnedTokenRepository: LearnedTokenRepository? = null,
    private val settingsRepository: com.cashbuddy.domain.repository.SettingsRepository? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReviewState())
    val uiState: StateFlow<ReviewState> = _uiState.asStateFlow()

    fun ignorePackage(packageName: String) {
        val repo = settingsRepository ?: return
        viewModelScope.launch {
            repo.addIgnoredPackage(packageName)
            _effects.emit(ReviewEffect.ShowSnackbar("Added $packageName to Ignore List"))
        }
    }

    private val _effects = MutableSharedFlow<ReviewEffect>(extraBufferCapacity = 1)
    val effects: SharedFlow<ReviewEffect> = _effects.asSharedFlow()

    private val discardCounts = mutableMapOf<String, Int>()

    fun clearDiscardCount(packageName: String) {
        discardCounts.remove(packageName)
    }

    fun onTransactionDiscarded(packageName: String): Boolean {
        if (packageName.isBlank() || packageName.equals("manual", ignoreCase = true) || packageName.equals("csv_import", ignoreCase = true)) {
            return false
        }
        val count = (discardCounts[packageName] ?: 0) + 1
        discardCounts[packageName] = count
        return count >= 3
    }

    fun updatePaymentMethod(txId: Long, method: String) {
        viewModelScope.launch {
            val tx = _uiState.value.transactions.find { it.id == txId } ?: return@launch
            val updatedTx = tx.copy(paymentMethod = method)
            transactionRepository.updatePaymentMethod(txId, method)
            notifyAndSync(ReviewResult.TransactionUpdated(updatedTx))
        }
    }

    fun toggleLoanStatus(txId: Long, isLoan: Boolean) {
        viewModelScope.launch {
            val tx = _uiState.value.transactions.find { it.id == txId } ?: return@launch
            val updatedTx = tx.copy(isLoanGiven = isLoan)
            transactionRepository.updateLoanStatus(txId, isLoan)
            notifyAndSync(ReviewResult.TransactionUpdated(updatedTx))
        }
    }

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
            is ReviewIntent.UpdatePendingTransaction -> updatePendingTransaction(
                intent.transactionId, intent.amount, intent.type, intent.merchant, intent.categoryId
            )
            is ReviewIntent.UpdateAndConfirmTransaction -> updateAndConfirmTransaction(
                intent.transactionId, intent.amount, intent.type, intent.merchant, intent.categoryId
            )
        }
    }

    /**
     * Single mutation-exit path: broadcasts the data change (platform listeners -> widgets
     * and stats refresh) and folds the domain result into UI state. Every DB mutation in
     * this ViewModel must end here so no path can forget the notify (the bug that bit
     * updateAndConfirmTransaction). filterConfidence is UI-only -> notify = false.
     */
    private fun notifyAndSync(result: ReviewResult, notify: Boolean = true) {
        if (notify) {
            com.cashbuddy.platform.notifyTransactionsUpdated()
        }
        _uiState.value = reducer.reduce(_uiState.value, result)
    }

    private fun loadPending() {
        viewModelScope.launch {
            combine(
                transactionRepository.getPending(),
                categoryRepository.getAll()
            ) { pendingTxs, categories ->
                ReviewResult.Loaded(pendingTxs, categories)
            }.collect { result ->
                notifyAndSync(result, notify = false)
            }
        }
    }

    private fun confirmTransaction(transactionId: Long) {
        viewModelScope.launch {
            confirmTransactionUseCase(transactionId)
            notifyAndSync(ReviewResult.TransactionConfirmed(transactionId))
            _effects.emit(ReviewEffect.ShowSnackbar("Transaction confirmed"))
        }
    }

    private fun rejectTransaction(transactionId: Long) {
        viewModelScope.launch {
            rejectTransactionUseCase(transactionId)
            notifyAndSync(ReviewResult.TransactionRejected(transactionId))
            _effects.emit(ReviewEffect.ShowSnackbar("Transaction discarded"))
        }
    }

    private fun modifyCategory(transactionId: Long, newCategoryId: Long) {
        viewModelScope.launch {
            val tx = _uiState.value.transactions.find { it.id == transactionId } ?: return@launch
            val oldCategoryName = tx.categoryName
            val newCategory = _uiState.value.allCategories.find { it.id == newCategoryId }
            val updatedTx = tx.copy(
                categoryId = newCategoryId,
                categoryName = newCategory?.name,
                categoryColor = newCategory?.color
            )
            modifyTransactionUseCase(
                transaction = updatedTx,
                oldCategoryName = oldCategoryName,
                newCategoryName = newCategory?.name
            )
            notifyAndSync(ReviewResult.CategoryModified(transactionId, newCategoryId))
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
            notifyAndSync(ReviewResult.AllHighConfidenceConfirmed)
            _effects.emit(ReviewEffect.ShowSnackbar("Confirmed ${highConfidence.size} high-confidence transactions"))
        }
    }

    private fun updatePendingTransaction(
        transactionId: Long,
        amount: Double,
        type: TransactionType,
        merchant: String,
        categoryId: Long
    ) = applyModification(transactionId, amount, type, merchant, categoryId, TransactionStatus.PENDING)

    private fun updateAndConfirmTransaction(
        transactionId: Long,
        amount: Double,
        type: TransactionType,
        merchant: String,
        categoryId: Long
    ) = applyModification(transactionId, amount, type, merchant, categoryId, TransactionStatus.CONFIRMED)

    /** Shared body of the Review edit dialog's two flows: edit fields, keep pending, or auto-confirm. */
    private fun applyModification(
        transactionId: Long,
        amount: Double,
        type: TransactionType,
        merchant: String,
        categoryId: Long,
        newStatus: TransactionStatus
    ) {
        viewModelScope.launch {
            val tx = _uiState.value.transactions.find { it.id == transactionId } ?: return@launch
            val oldCategoryName = tx.categoryName
            val newCategory = _uiState.value.allCategories.find { it.id == categoryId }
            val updatedTx = tx.copy(
                amount = amount,
                type = type,
                merchant = merchant.trim().ifBlank { "Unknown Merchant" },
                categoryId = categoryId,
                categoryName = newCategory?.name,
                categoryColor = newCategory?.color,
                status = newStatus
            )
            modifyTransactionUseCase(
                transaction = updatedTx,
                oldCategoryName = oldCategoryName,
                newCategoryName = newCategory?.name
            )
            val confirmed = newStatus == TransactionStatus.CONFIRMED
            notifyAndSync(
                if (confirmed) ReviewResult.TransactionConfirmed(transactionId)
                else ReviewResult.TransactionUpdated(updatedTx)
            )
            _effects.emit(
                if (confirmed) ReviewEffect.ShowSnackbar("Transaction updated and confirmed")
                else ReviewEffect.ShowSnackbar("Pending transaction updated")
            )
        }
    }
}
