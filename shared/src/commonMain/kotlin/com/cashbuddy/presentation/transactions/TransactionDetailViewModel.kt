package com.cashbuddy.presentation.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cashbuddy.domain.model.Category
import com.cashbuddy.domain.model.Transaction
import com.cashbuddy.domain.repository.CategoryRepository
import com.cashbuddy.domain.repository.MerchantRuleRepository
import com.cashbuddy.domain.repository.TrainingDataRepository
import com.cashbuddy.domain.repository.TransactionRepository
import com.cashbuddy.platform.currentTimeMillis
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class TransactionDetailUiState(
    val transaction: Transaction? = null,
    val categories: List<Category> = emptyList(),
    val isLoading: Boolean = true
)

class TransactionDetailViewModel(
    private val transactionId: Long,
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository,
    private val trainingDataRepository: TrainingDataRepository,
    private val merchantRuleRepository: MerchantRuleRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(TransactionDetailUiState())
    val uiState: StateFlow<TransactionDetailUiState> = _uiState.asStateFlow()

    private val _navBackEffect = MutableSharedFlow<Unit>()
    val navBackEffect: SharedFlow<Unit> = _navBackEffect.asSharedFlow()

    init {
        viewModelScope.launch {
            combine(
                transactionRepository.getById(transactionId),
                categoryRepository.getAll()
            ) { tx, cats ->
                TransactionDetailUiState(
                    transaction = tx,
                    categories = cats,
                    isLoading = false
                )
            }.collect { newState ->
                _uiState.value = newState
            }
        }
    }

    fun onCategoryChanged(newCategoryId: Long) {
        viewModelScope.launch {
            val tx = _uiState.value.transaction ?: return@launch
            val oldCategoryName = tx.categoryName
            val newCategory = _uiState.value.categories.find { it.id == newCategoryId }
            val updated = tx.copy(
                categoryId = newCategoryId,
                categoryName = newCategory?.name,
                categoryColor = newCategory?.color
            )
            transactionRepository.update(updated)
            _uiState.value = _uiState.value.copy(transaction = updated)

            val targetLabel = newCategory?.name ?: return@launch
            if (!targetLabel.equals(oldCategoryName, ignoreCase = true)) {
                trainingDataRepository.recordCorrection(
                    merchant = tx.merchant,
                    sourceApp = tx.sourceApp,
                    rawText = tx.rawText,
                    oldCategory = oldCategoryName,
                    newCategory = targetLabel,
                    timestamp = currentTimeMillis()
                )
                merchantRuleRepository.learnRule(
                    merchant = tx.merchant,
                    categoryId = newCategoryId,
                    priority = 100L
                )
            }
        }
    }

    fun onDeleteClicked() {
        viewModelScope.launch {
            transactionRepository.deleteById(transactionId)
            _navBackEffect.emit(Unit)
        }
    }
}
