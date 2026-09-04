package com.cashbuddy.presentation.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cashbuddy.domain.model.Category
import com.cashbuddy.domain.model.Transaction
import com.cashbuddy.domain.model.TransactionType
import com.cashbuddy.domain.repository.CategoryRepository
import com.cashbuddy.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class TransactionListUiState(
    val transactions: List<Transaction> = emptyList(),
    val filteredTransactions: List<Transaction> = emptyList(),
    val categories: List<Category> = emptyList(),
    val searchQuery: String = "",
    val selectedType: TransactionType? = null,
    val selectedCategoryId: Long? = null,
    val isLoading: Boolean = true
)

class TransactionListViewModel(
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(TransactionListUiState())
    val uiState: StateFlow<TransactionListUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                transactionRepository.getAll(),
                categoryRepository.getAll()
            ) { allTxs, allCats ->
                _uiState.value = _uiState.value.copy(
                    transactions = allTxs,
                    categories = allCats,
                    isLoading = false
                )
                applyFilters()
            }.collect {}
        }
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
        applyFilters()
    }

    fun onTypeSelected(type: TransactionType?) {
        _uiState.value = _uiState.value.copy(selectedType = type)
        applyFilters()
    }

    fun onCategorySelected(categoryId: Long?) {
        _uiState.value = _uiState.value.copy(selectedCategoryId = categoryId)
        applyFilters()
    }

    private fun applyFilters() {
        val state = _uiState.value
        val filtered = state.transactions.filter { tx ->
            val matchesQuery = state.searchQuery.isBlank() ||
                tx.merchant.contains(state.searchQuery, ignoreCase = true) ||
                tx.rawText.contains(state.searchQuery, ignoreCase = true)

            val matchesType = state.selectedType == null || tx.type == state.selectedType
            val matchesCategory = state.selectedCategoryId == null || tx.categoryId == state.selectedCategoryId

            matchesQuery && matchesType && matchesCategory
        }
        _uiState.value = state.copy(filteredTransactions = filtered)
    }
}
