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
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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
    private val _isEndReached = MutableStateFlow(false)
    private val pagerMutex = Mutex()

    private data class FilterCriteria(
        val query: String,
        val type: TransactionType?,
        val catId: Long?,
        val period: TimePeriod,
        val customRange: DateRange?,
        val minAmt: Double?,
        val maxAmt: Double?
    )

    private data class ResolvedQueryArgs(
        val searchQuery: String?,
        val type: String?,
        val categoryId: Long?,
        val startTime: Long?,
        val endTime: Long?,
        val minAmount: Double?,
        val maxAmount: Double?
    )

    private fun currentCriteria() = FilterCriteria(
        query = _searchQuery.value,
        type = _selectedType.value,
        catId = _selectedCategoryId.value,
        period = _selectedPeriod.value,
        customRange = _customDateRange.value,
        minAmt = _minAmount.value,
        maxAmt = _maxAmount.value
    )

    private fun resolveQueryArgs(criteria: FilterCriteria): ResolvedQueryArgs {
        val range = DateRangeHelper.calculateDateRange(
            criteria.period,
            criteria.customRange?.startTimestamp,
            criteria.customRange?.endTimestamp
        )
        val startTime = if (criteria.period == TimePeriod.ALL_TIME) null else range.startTimestamp
        val endTime = if (criteria.period == TimePeriod.ALL_TIME) null else range.endTimestamp
        return ResolvedQueryArgs(
            searchQuery = criteria.query.trim().ifEmpty { null },
            type = criteria.type?.name,
            categoryId = criteria.catId,
            startTime = startTime,
            endTime = endTime,
            minAmount = criteria.minAmt,
            maxAmount = criteria.maxAmt
        )
    }

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

        // Reactive SQLDelight summary stream scoped to the active filter criteria
        viewModelScope.launch(ioDispatcher) {
            filterCriteriaFlow
                .flatMapLatest { criteria ->
                    val args = resolveQueryArgs(criteria)
                    transactionRepository.getHistorySummaryFlow(
                        searchQuery = args.searchQuery,
                        type = args.type,
                        categoryId = args.categoryId,
                        startTime = args.startTime,
                        endTime = args.endTime,
                        minAmount = args.minAmount,
                        maxAmount = args.maxAmount
                    )
                }
                .collect { summary ->
                    _summary.value = summary
                }
        }

        // Reactive paginated list: resets on filter changes and reloads the visible window
        // whenever underlying transactions change (delete, edit, insert, merge, or ingest).
        viewModelScope.launch(ioDispatcher) {
            filterCriteriaFlow
                .flatMapLatest { criteria ->
                    var isInitialForCriteria = true
                    kotlinx.coroutines.flow.flow {
                        transactionRepository.transactionUpdates
                            .onStart { emit(Unit) }
                            .conflate()
                            .collect {
                                emit(criteria to isInitialForCriteria)
                                isInitialForCriteria = false
                            }
                    }
                }
                .collectLatest { (criteria, isInitial) ->
                    val args = resolveQueryArgs(criteria)
                    pagerMutex.withLock {
                        if (isInitial) {
                            _isLoading.value = true
                            pager.reset()
                            val firstPage = pager.loadNextPage(
                                searchQuery = args.searchQuery,
                                type = args.type,
                                categoryId = args.categoryId,
                                startTime = args.startTime,
                                endTime = args.endTime,
                                minAmount = args.minAmount,
                                maxAmount = args.maxAmount
                            )
                            _transactions.value = firstPage
                            _isEndReached.value = pager.isEndReached
                            _isLoading.value = false
                        } else {
                            val currentCount = maxOf(pager.pageSize.toInt(), _transactions.value.size)
                            val reloaded = pager.reload(
                                searchQuery = args.searchQuery,
                                type = args.type,
                                categoryId = args.categoryId,
                                startTime = args.startTime,
                                endTime = args.endTime,
                                minAmount = args.minAmount,
                                maxAmount = args.maxAmount,
                                currentCount = currentCount
                            )
                            _transactions.value = reloaded
                            _isEndReached.value = pager.isEndReached
                        }
                    }
                }
        }
    }

    fun loadNextPage() {
        if (pager.isEndReached || _isLoading.value || _isLoadingMore.value) return
        viewModelScope.launch(ioDispatcher) {
            pagerMutex.withLock {
                if (pager.isEndReached || _isLoading.value || _isLoadingMore.value) return@withLock
                _isLoadingMore.value = true
                try {
                    val args = resolveQueryArgs(currentCriteria())
                    val nextPage = pager.loadNextPage(
                        searchQuery = args.searchQuery,
                        type = args.type,
                        categoryId = args.categoryId,
                        startTime = args.startTime,
                        endTime = args.endTime,
                        minAmount = args.minAmount,
                        maxAmount = args.maxAmount
                    )
                    if (nextPage.isNotEmpty()) {
                        _transactions.value = _transactions.value + nextPage
                    }
                    _isEndReached.value = pager.isEndReached
                } finally {
                    _isLoadingMore.value = false
                }
            }
        }
    }

    fun refresh() {
        viewModelScope.launch(ioDispatcher) {
            reloadCurrent()
        }
    }

    private suspend fun reloadCurrent() {
        val criteria = currentCriteria()
        val args = resolveQueryArgs(criteria)
        pagerMutex.withLock {
            val currentCount = maxOf(pager.pageSize.toInt(), _transactions.value.size)
            val reloaded = pager.reload(
                searchQuery = args.searchQuery,
                type = args.type,
                categoryId = args.categoryId,
                startTime = args.startTime,
                endTime = args.endTime,
                minAmount = args.minAmount,
                maxAmount = args.maxAmount,
                currentCount = currentCount
            )
            _transactions.value = reloaded
            _isEndReached.value = pager.isEndReached
        }
        _summary.value = transactionRepository.getHistorySummary(
            searchQuery = args.searchQuery,
            type = args.type,
            categoryId = args.categoryId,
            startTime = args.startTime,
            endTime = args.endTime,
            minAmount = args.minAmount,
            maxAmount = args.maxAmount
        )
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
        _isEndReached,
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
        val endReached = args[12] as Boolean
        val editingTx = args[13] as Transaction?

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
            isEndReached = endReached,
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
                com.cashbuddy.platform.notifyTransactionsUpdated()
                _editingTransaction.value = null
            }
        }
    }

    fun deleteTransaction(id: Long) {
        viewModelScope.launch(ioDispatcher) {
            transactionRepository.deleteById(id)
            com.cashbuddy.platform.notifyTransactionsUpdated()
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

    fun applyInitialFilters(
        categoryId: Long?,
        typeName: String?,
        periodName: String?,
        startMillis: Long?,
        endMillis: Long?
    ) {
        _searchQuery.value = ""
        _minAmount.value = null
        _maxAmount.value = null
        _selectedCategoryId.value = categoryId
        _selectedType.value = typeName?.let { runCatching { TransactionType.valueOf(it) }.getOrNull() }
        val period = periodName?.let { runCatching { TimePeriod.valueOf(it) }.getOrNull() } ?: TimePeriod.ALL_TIME
        if (period == TimePeriod.CUSTOM && startMillis != null && endMillis != null) {
            val normalizedStart = minOf(startMillis, endMillis)
            val normalizedEnd = maxOf(startMillis, endMillis)
            _customDateRange.value = DateRange(normalizedStart, normalizedEnd)
            _selectedPeriod.value = TimePeriod.CUSTOM
        } else {
            _customDateRange.value = null
            _selectedPeriod.value = period
        }
    }
}
