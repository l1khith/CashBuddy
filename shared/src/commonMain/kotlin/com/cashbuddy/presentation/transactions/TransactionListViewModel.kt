package com.cashbuddy.presentation.transactions

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cashbuddy.domain.model.Category
import com.cashbuddy.domain.model.DateRange
import com.cashbuddy.domain.model.DateRangeHelper
import com.cashbuddy.domain.model.TimePeriod
import com.cashbuddy.domain.model.Transaction
import com.cashbuddy.domain.model.TransactionStatus
import com.cashbuddy.domain.model.TransactionType
import com.cashbuddy.domain.repository.CategoryRepository
import com.cashbuddy.domain.repository.TransactionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn

@Immutable
data class TransactionListUiState(
    val filteredTransactions: List<Transaction> = emptyList(),
    val categories: List<Category> = emptyList(),
    val searchQuery: String = "",
    val selectedType: TransactionType? = null,
    val selectedCategoryId: Long? = null,
    val selectedPeriod: TimePeriod = TimePeriod.ALL_TIME,
    val customDateRange: DateRange? = null,
    val totalDebit: Double = 0.0,
    val totalCredit: Double = 0.0,
    val totalCount: Int = 0,
    val isLoading: Boolean = true
)

@OptIn(ExperimentalCoroutinesApi::class)
class TransactionListViewModel(
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    private val _selectedType = MutableStateFlow<TransactionType?>(null)
    private val _selectedCategoryId = MutableStateFlow<Long?>(null)
    private val _selectedPeriod = MutableStateFlow(TimePeriod.ALL_TIME)
    private val _customDateRange = MutableStateFlow<DateRange?>(null)

    private data class FilterCriteria(
        val query: String,
        val type: TransactionType?,
        val catId: Long?,
        val period: TimePeriod,
        val customRange: DateRange?
    )

    private val filterCriteriaFlow = combine(
        _searchQuery,
        _selectedType,
        _selectedCategoryId,
        _selectedPeriod,
        _customDateRange
    ) { query, type, catId, period, customRange ->
        FilterCriteria(query, type, catId, period, customRange)
    }.distinctUntilChanged()

    val uiState: StateFlow<TransactionListUiState> = filterCriteriaFlow
        .flatMapLatest { criteria ->
            val range = DateRangeHelper.calculateDateRange(
                criteria.period,
                criteria.customRange?.startTimestamp,
                criteria.customRange?.endTimestamp
            )
            val txsFlow = if (criteria.period == TimePeriod.ALL_TIME) {
                transactionRepository.getAll()
            } else {
                transactionRepository.getByDateRange(range.startTimestamp, range.endTimestamp)
            }
            combine(
                txsFlow.distinctUntilChanged(),
                categoryRepository.getAll().distinctUntilChanged()
            ) { allTxs, allCats ->
                val trimmedQuery = criteria.query.trim()
                val hasQuery = trimmedQuery.isNotEmpty()

                val filtered = allTxs.filter { tx ->
                    // 1. Fast equality filters first
                    if (criteria.type != null && tx.type != criteria.type) return@filter false
                    if (criteria.catId != null && tx.categoryId != criteria.catId) return@filter false

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

                // Single-pass computation for debit and credit totals (zero intermediary list allocations)
                var totalDebit = 0.0
                var totalCredit = 0.0
                for (tx in filtered) {
                    if (tx.status == TransactionStatus.CONFIRMED) {
                        if (tx.type == TransactionType.DEBIT) totalDebit += tx.amount
                        else if (tx.type == TransactionType.CREDIT) totalCredit += tx.amount
                    }
                }

                TransactionListUiState(
                    filteredTransactions = filtered,
                    categories = allCats,
                    searchQuery = criteria.query,
                    selectedType = criteria.type,
                    selectedCategoryId = criteria.catId,
                    selectedPeriod = criteria.period,
                    customDateRange = criteria.customRange,
                    totalDebit = totalDebit,
                    totalCredit = totalCredit,
                    totalCount = filtered.size,
                    isLoading = false
                )
            }
        }
        .flowOn(Dispatchers.Default) // Execute all heavy filtering & aggregation off the main thread
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

    fun onPeriodSelected(period: TimePeriod) {
        _selectedPeriod.value = period
        if (period != TimePeriod.CUSTOM) {
            _customDateRange.value = null
        }
    }

    fun onCustomDateRangeSelected(startMillis: Long, endMillis: Long) {
        val normalizedStart = minOf(startMillis, endMillis)
        val normalizedEnd = maxOf(startMillis, endMillis)
        _customDateRange.value = DateRange(normalizedStart, normalizedEnd)
        _selectedPeriod.value = TimePeriod.CUSTOM
    }

    fun clearFilters() {
        _searchQuery.value = ""
        _selectedType.value = null
        _selectedCategoryId.value = null
        _selectedPeriod.value = TimePeriod.ALL_TIME
        _customDateRange.value = null
    }
}
