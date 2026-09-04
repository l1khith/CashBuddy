package com.cashbuddy.presentation.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cashbuddy.domain.model.Category
import com.cashbuddy.domain.model.Transaction
import com.cashbuddy.domain.model.TransactionType
import com.cashbuddy.domain.repository.CategoryRepository
import com.cashbuddy.domain.repository.TransactionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn

data class TransactionListUiState(
    val filteredTransactions: List<Transaction> = emptyList(),
    val categories: List<Category> = emptyList(),
    val searchQuery: String = "",
    val selectedType: TransactionType? = null,
    val selectedCategoryId: Long? = null,
    val totalCount: Int = 0,
    val isLoading: Boolean = true
)

class TransactionListViewModel(
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    private val _selectedType = MutableStateFlow<TransactionType?>(null)
    private val _selectedCategoryId = MutableStateFlow<Long?>(null)

    val uiState: StateFlow<TransactionListUiState> = combine(
        transactionRepository.getAll(),
        categoryRepository.getAll(),
        _searchQuery,
        _selectedType,
        _selectedCategoryId
    ) { allTxs, allCats, query, type, catId ->
        val trimmedQuery = query.trim()
        val hasQuery = trimmedQuery.isNotEmpty()

        val filtered = allTxs.filter { tx ->
            // 1. Fast equality filters first
            if (type != null && tx.type != type) return@filter false
            if (catId != null && tx.categoryId != catId) return@filter false

            // 2. Query filter with early exit
            if (!hasQuery) return@filter true

            // Match merchant first (95% of cases)
            if (tx.merchant.contains(trimmedQuery, ignoreCase = true)) return@filter true

            // Match category name
            if (tx.categoryName?.contains(trimmedQuery, ignoreCase = true) == true) return@filter true

            // Match user notes
            if (tx.notes?.contains(trimmedQuery, ignoreCase = true) == true) return@filter true

            // Fallback to raw text only if necessary
            tx.rawText.contains(trimmedQuery, ignoreCase = true)
        }

        TransactionListUiState(
            filteredTransactions = filtered,
            categories = allCats,
            searchQuery = query,
            selectedType = type,
            selectedCategoryId = catId,
            totalCount = allTxs.size,
            isLoading = false
        )
    }
    .flowOn(Dispatchers.Default) // Execute all heavy filtering off the main thread
    .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = TransactionListUiState(isLoading = true)
    )

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun onTypeSelected(type: TransactionType?) {
        _selectedType.value = type
    }

    fun onCategorySelected(categoryId: Long?) {
        _selectedCategoryId.value = categoryId
    }
}
