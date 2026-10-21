package com.cashbuddy.presentation.transactions

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cashbuddy.domain.model.Category
import com.cashbuddy.domain.model.DateRange
import com.cashbuddy.domain.model.DateRangeHelper
import com.cashbuddy.domain.model.TimePeriod
import com.cashbuddy.domain.model.Transaction
import com.cashbuddy.domain.model.TransactionType
import com.cashbuddy.domain.repository.CategoryRepository
import com.cashbuddy.domain.repository.HistorySummary
import com.cashbuddy.domain.repository.TransactionPager
import com.cashbuddy.domain.repository.TransactionRepository
import com.cashbuddy.platform.ioDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@Immutable
data class TransactionListUiState(
    val filteredTransactions: List<Transaction> = emptyList(),
    val categories: List<Category> = emptyList(),
    val searchQuery: String = "",
    val selectedType: TransactionType? = null,
    val selectedCategoryId: Long? = null,
    val selectedPeriod: TimePeriod = TimePeriod.ALL_TIME,
    val customDateRange: DateRange? = null,
    val minAmount: Double? = null,
    val maxAmount: Double? = null,
    val totalDebit: Double = 0.0,
    val totalCredit: Double = 0.0,
    val totalCount: Int = 0,
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val isEndReached: Boolean = false,
    val editingTransaction: Transaction? = null
)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class TransactionListViewModel(
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository
) : ViewModel() {

    private val pager = TransactionPager(transactionRepository, pageSize = 50L)

    private val _searchQuery = MutableStateFlow("")
    private val _selectedType = MutableStateFlow<TransactionType?>(null)
    private val _selectedCategoryId = MutableStateFlow<Long?>(null)
    private val _selectedPeriod = MutableStateFlow(TimePeriod.ALL_TIME)
    private val _customDateRange = MutableStateFlow<DateRange?>(null)
    private val _minAmount = MutableStateFlow<Double?>(null)
    private val _maxAmount = MutableStateFlow<Double?>(null)
    private val _editingTransaction = MutableStateFlow<Transaction?>(null)

    private val _transactions = MutableStateFlow<List<Transaction>>(emptyList())
    private val _categories = MutableStateFlow<List<Category>>(emptyList())
    private val _summary = MutableStateFlow(HistorySummary(0.0, 0.0, 0L))
    private val _isLoading = MutableStateFlow(true)
    private val _isLoadingMore = MutableStateFlow(false)

    private data class FilterCriteria(
        val query: String,
        val type: TransactionType?,
        val catId: Long?,
        val period: TimePeriod,
        val customRange: DateRange?,
        val minAmt: Double?,
        val maxAmt: Double?
    )

    private val filterCriteriaFlow = combine(
        _searchQuery.debounce(300),
        _selectedType,
        _selectedCategoryId,
        _selectedPeriod,
        _customDateRange,
        _minAmount,
        _maxAmount
    ) { args: Array<Any?> ->
        FilterCriteria(
            query = args[0] as String,
            type = args[1] as TransactionType?,
            catId = args[2] as Long?,
            period = args[3] as TimePeriod,
            customRange = args[4] as DateRange?,
            minAmt = args[5] as Double?,
            maxAmt = args[6] as Double?
        )
    }.distinctUntilChanged()

    init {
        viewModelScope.launch(ioDispatcher) {
            categoryRepository.getAll().distinctUntilChanged().collect { cats ->
                _categories.value = cats
            }
        }

        viewModelScope.launch(ioDispatcher) {
            filterCriteriaFlow.collectLatest { criteria ->
                _isLoading.value = true
                pager.reset()

                val range = DateRangeHelper.calculateDateRange(
                    criteria.period,
                    criteria.customRange?.startTimestamp,
                    criteria.customRange?.endTimestamp
                )
                val startTime = if (criteria.period == TimePeriod.ALL_TIME) null else range.startTimestamp
                val endTime = if (criteria.period == TimePeriod.ALL_TIME) null else range.endTimestamp

                val summary = transactionRepository.getHistorySummary(
                    searchQuery = criteria.query.trim().ifEmpty { null },
                    type = criteria.type?.name,
                    categoryId = criteria.catId,
                    startTime = startTime,
                    endTime = endTime,
                    minAmount = criteria.minAmt,
                    maxAmount = criteria.maxAmt
                )
                _summary.value = summary

                val firstPage = pager.loadNextPage(
                    searchQuery = criteria.query.trim().ifEmpty { null },
                    type = criteria.type?.name,
                    categoryId = criteria.catId,
                    startTime = startTime,
                    endTime = endTime,
                    minAmount = criteria.minAmt,
                    maxAmount = criteria.maxAmt
                )
                _transactions.value = firstPage
                _isLoading.value = false
            }
        }
    }

    fun loadNextPage() {
        if (pager.isEndReached || _isLoading.value || _isLoadingMore.value) return
        viewModelScope.launch(ioDispatcher) {
            _isLoadingMore.value = true
            val criteria = FilterCriteria(
                _searchQuery.value,
                _selectedType.value,
                _selectedCategoryId.value,
                _selectedPeriod.value,
                _customDateRange.value,
                _minAmount.value,
                _maxAmount.value
            )
            val range = DateRangeHelper.calculateDateRange(
                criteria.period,
                criteria.customRange?.startTimestamp,
                criteria.customRange?.endTimestamp
            )
            val startTime = if (criteria.period == TimePeriod.ALL_TIME) null else range.startTimestamp
            val endTime = if (criteria.period == TimePeriod.ALL_TIME) null else range.endTimestamp

            val nextPage = pager.loadNextPage(
                searchQuery = criteria.query.trim().ifEmpty { null },
                type = criteria.type?.name,
                categoryId = criteria.catId,
                startTime = startTime,
                endTime = endTime,
                minAmount = criteria.minAmt,
                maxAmount = criteria.maxAmt
            )
            if (nextPage.isNotEmpty()) {
                _transactions.value = _transactions.value + nextPage
            }
            _isLoadingMore.value = false
        }
    }

    val uiState: StateFlow<TransactionListUiState> = combine(
        _transactions,
        _categories,
        _searchQuery,
        _selectedType,
        _selectedCategoryId,
        _selectedPeriod,
        _customDateRange,
        _minAmount,
        _maxAmount,
        _summary,
        _isLoading,
        _isLoadingMore,
        _editingTransaction
    ) { args: Array<Any?> ->
        @Suppress("UNCHECKED_CAST")
        val txs = args[0] as List<Transaction>
        @Suppress("UNCHECKED_CAST")
        val cats = args[1] as List<Category>
        val query = args[2] as String
        val type = args[3] as TransactionType?
        val catId = args[4] as Long?
        val period = args[5] as TimePeriod
        val customRange = args[6] as DateRange?
        val minAmt = args[7] as Double?
        val maxAmt = args[8] as Double?
        val summary = args[9] as HistorySummary
        val loading = args[10] as Boolean
        val loadingMore = args[11] as Boolean
        val editingTx = args[12] as Transaction?

        TransactionListUiState(
            filteredTransactions = txs,
            categories = cats,
            searchQuery = query,
            selectedType = type,
            selectedCategoryId = catId,
            selectedPeriod = period,
            customDateRange = customRange,
            minAmount = minAmt,
            maxAmount = maxAmt,
            totalDebit = summary.totalDebit,
            totalCredit = summary.totalCredit,
            totalCount = summary.totalCount.toInt(),
            isLoading = loading,
            isLoadingMore = loadingMore,
            isEndReached = pager.isEndReached,
            editingTransaction = editingTx
        )
    }.stateIn(
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

    fun onAmountRangeSelected(min: Double?, max: Double?) {
        _minAmount.value = min
        _maxAmount.value = max
    }

    fun onStartEditTransaction(tx: Transaction) {
        _editingTransaction.value = tx
    }

    fun onDismissEditTransaction() {
        _editingTransaction.value = null
    }

    fun updateTransaction(
        id: Long,
        amount: Double,
        type: TransactionType,
        merchant: String,
        categoryId: Long
    ) {
        viewModelScope.launch(ioDispatcher) {
            val existing = _transactions.value.find { it.id == id } 
                ?: _editingTransaction.value?.takeIf { it.id == id }
            if (existing != null) {
                val updated = existing.copy(
                    amount = amount,
                    type = type,
                    merchant = merchant.trim().ifEmpty { existing.merchant },
                    categoryId = categoryId,
                    status = com.cashbuddy.domain.model.TransactionStatus.CONFIRMED,
                    updatedAt = com.cashbuddy.platform.currentTimeMillis()
                )
                transactionRepository.update(updated)
                _editingTransaction.value = null

                // Optimistically update list
                _transactions.value = _transactions.value.map {
                    if (it.id == id) updated else it
                }
            }
        }
    }

    fun clearFilters() {
        _searchQuery.value = ""
        _selectedType.value = null
        _selectedCategoryId.value = null
        _selectedPeriod.value = TimePeriod.ALL_TIME
        _customDateRange.value = null
        _minAmount.value = null
        _maxAmount.value = null
    }
}
