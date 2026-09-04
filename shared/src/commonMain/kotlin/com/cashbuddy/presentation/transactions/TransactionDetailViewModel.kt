package com.cashbuddy.presentation.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cashbuddy.domain.model.Category
import com.cashbuddy.domain.model.Transaction
import com.cashbuddy.domain.repository.CategoryRepository
import com.cashbuddy.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

data class TransactionDetailUiState(
    val transaction: Transaction? = null,
    val categories: List<Category> = emptyList(),
    val isLoading: Boolean = true
)

class TransactionDetailViewModel(
    private val transactionId: Long,
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(TransactionDetailUiState())
    val uiState: StateFlow<TransactionDetailUiState> = _uiState.asStateFlow()

    private val _navBackEffect = MutableSharedFlow<Unit>()
    val navBackEffect: SharedFlow<Unit> = _navBackEffect.asSharedFlow()

    init {
        viewModelScope.launch {
            val tx = transactionRepository.getById(transactionId).firstOrNull()
            val cats = categoryRepository.getAll().firstOrNull() ?: emptyList()
            _uiState.value = TransactionDetailUiState(
                transaction = tx,
                categories = cats,
                isLoading = false
            )
        }
    }

    fun onCategoryChanged(newCategoryId: Long) {
        viewModelScope.launch {
            val tx = _uiState.value.transaction ?: return@launch
            val updated = tx.copy(categoryId = newCategoryId)
            transactionRepository.update(updated)
            _uiState.value = _uiState.value.copy(transaction = updated)
        }
    }

    fun onDeleteClicked() {
        viewModelScope.launch {
            transactionRepository.deleteById(transactionId)
            _navBackEffect.emit(Unit)
        }
    }
}
