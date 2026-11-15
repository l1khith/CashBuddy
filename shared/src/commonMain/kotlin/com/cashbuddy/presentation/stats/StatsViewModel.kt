package com.cashbuddy.presentation.stats

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cashbuddy.domain.model.Budget
import com.cashbuddy.domain.model.BudgetCategories
import com.cashbuddy.domain.model.BudgetPeriod
import com.cashbuddy.domain.model.Category
import com.cashbuddy.domain.model.DateRange
import com.cashbuddy.domain.model.DateRangeHelper
import com.cashbuddy.domain.model.TimePeriod
import com.cashbuddy.domain.model.Transaction
import com.cashbuddy.domain.model.TransactionStatus
import com.cashbuddy.domain.model.TransactionType
import com.cashbuddy.domain.repository.BudgetRepository
import com.cashbuddy.domain.repository.CategoryBreakdown
import com.cashbuddy.domain.repository.CategoryRepository
import com.cashbuddy.domain.repository.TransactionRepository
import com.cashbuddy.platform.currentTimeMillis
import com.cashbuddy.platform.ioDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.math.roundToInt

@Immutable
data class CategoryStatsItem(
    val breakdown: CategoryBreakdown,
    val categoryId: Long?,
    val comparisonPercent: Int?,
    val barFraction: Float,
    val percentageInt: Int,
    val committedSpend: Double = 0.0
)

@Immutable
data class StatsBudgetStatus(
    val budget: Budget,
    val spent: Double,
    val remaining: Double,
    val progress: Float,
    val percentageInt: Int,
    val isExceeded: Boolean
)

@Immutable
data class MerchantInsight(
    val merchant: String,
    val totalAmount: Double
)

@Immutable
data class DayInsight(
    val dateLabel: String,
    val totalAmount: Double
)

sealed interface StatsSheetType {
    data object TotalSpent : StatsSheetType
    data object TotalIncome : StatsSheetType
    data class Category(val categoryName: String, val categoryId: Long?) : StatsSheetType
}

@Immutable
data class StatsSheetState(
    val type: StatsSheetType,
    val title: String,
    val totalAmount: Double,
    val transactions: List<Transaction>,
    val categoryId: Long? = null,
    val txTypeFilter: TransactionType? = null
)

@Immutable
data class StatsUiState(
    val selectedPeriod: TimePeriod = TimePeriod.THIS_MONTH,
    val customDateRange: DateRange? = null,
    val startTimestamp: Long = 0L,
    val endTimestamp: Long = 0L,
    val dateRangeLabel: String = "",
    val categoryBreakdowns: List<CategoryStatsItem> = emptyList(),
    val totalSpent: Double = 0.0,
    val totalIncome: Double = 0.0,
    val actualSpent: Double = 0.0,
    val cardOutstanding: Double = 0.0,
    val lentOut: Double = 0.0,
    val budgetStatus: StatsBudgetStatus? = null,
    val topMerchant: MerchantInsight? = null,
    val highestSpendDay: DayInsight? = null,
    val activeSheet: StatsSheetState? = null,
    val isLoading: Boolean = true
)

@OptIn(ExperimentalCoroutinesApi::class)
class StatsViewModel(
    private val transactionRepository: TransactionRepository,
    private val budgetRepository: BudgetRepository,
    private val categoryRepository: CategoryRepository,
    private val recurringPatternRepository: com.cashbuddy.domain.repository.RecurringPatternRepository? = null
) : ViewModel() {

    private val _selectedPeriod = MutableStateFlow(TimePeriod.THIS_MONTH)
    private val _customDateRange = MutableStateFlow<DateRange?>(null)
    private val _activeSheetType = MutableStateFlow<StatsSheetType?>(null)

    private val _uiState = MutableStateFlow(StatsUiState())
    val uiState: StateFlow<StatsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch(ioDispatcher) {
            val periodAndRange = combine(_selectedPeriod, _customDateRange) { period, range -> period to range }
            val metaFlow = combine(
                budgetRepository.getAll(),
                categoryRepository.getAll(),
                recurringPatternRepository?.getAll() ?: kotlinx.coroutines.flow.flowOf(emptyList())
            ) { budgets, categories, patterns -> Triple(budgets, categories, patterns) }

            combine(
                periodAndRange,
                metaFlow,
                transactionRepository.transactionUpdates.onStart { emit(Unit) }
            ) { (period, customRange), (budgets, categories, patterns), _ ->
                PeriodQueryContext(period, customRange, budgets, categories, patterns)
            }.flatMapLatest { ctx ->
                val now = currentTimeMillis()
                val range = DateRangeHelper.calculateDateRange(
                    period = ctx.period,
                    customStart = ctx.customRange?.startTimestamp,
                    customEnd = ctx.customRange?.endTimestamp,
                    nowMillis = now
                )
                val prevRange = calculatePreviousRange(ctx.period, range, now)

                combine(
                    transactionRepository.getByDateRange(range.startTimestamp, range.endTimestamp),
                    transactionRepository.getCategoryBreakdown(range.startTimestamp, range.endTimestamp),
                    _activeSheetType
                ) { transactions, breakdowns, activeSheetType ->
                    val validTxs = transactions.filter {
                        it.status != TransactionStatus.REJECTED && !it.isMerged
                    }
                    val debitTxs = validTxs.filter { it.type == TransactionType.DEBIT }
                    val creditTxs = validTxs.filter { it.type == TransactionType.CREDIT }

                    val totalIncome = creditTxs.sumOf { it.amount }
                    val totalDebit = debitTxs.sumOf { it.amount }

                    val (prevBreakdowns, actualSpent, cardOutstanding, lentOut) = coroutineScope {
                        val prev = async {
                            transactionRepository.getCategoryBreakdown(
                                prevRange.startTimestamp,
                                prevRange.endTimestamp
                            ).first()
                        }
                        val actual = async {
                            transactionRepository.sumActualSpent(range.startTimestamp, range.endTimestamp)
                        }
                        val card = async {
                            transactionRepository.sumCardOutstanding(range.startTimestamp, range.endTimestamp)
                        }
                        val lent = async {
                            transactionRepository.sumLentOut(range.startTimestamp, range.endTimestamp)
                        }
                        Quad(prev.await(), actual.await(), card.await(), lent.await())
                    }

                    val prevByCategory = prevBreakdowns.associateBy { it.categoryName }
                    val categoryIdByName = ctx.categories.associateBy({ it.name }, { it.id })

                    val totalSpendingForBreakdowns = breakdowns.sumOf { it.totalAmount }.coerceAtLeast(1.0)
                    val maxCategorySpent = breakdowns.maxOfOrNull { it.totalAmount }?.coerceAtLeast(1.0) ?: 1.0
                    val recurringMerchants = ctx.recurringPatterns.map { it.merchantNormalized.lowercase().trim() }.toSet()
                    val categoryItems = breakdowns.map { cat ->
                        val prevAmount = prevByCategory[cat.categoryName]?.totalAmount ?: 0.0
                        val comparisonPercent = if (prevAmount > 0.0) {
                            (((cat.totalAmount - prevAmount) / prevAmount) * 100.0).roundToInt()
                        } else {
                            null
                        }
                        val textFraction = (cat.totalAmount / totalSpendingForBreakdowns).toFloat().coerceIn(0f, 1f)
                        val textPctInt = (textFraction * 100).toInt()
                        val barFraction = (cat.totalAmount / maxCategorySpent).toFloat().coerceIn(0f, 1f)

                        // Calculate committedSpend = sum of amounts where merchant matches recurring_pattern
                        val catTxs = debitTxs.filter { tx ->
                            val catName = tx.categoryName ?: "Uncategorized"
                            catName.equals(cat.categoryName, ignoreCase = true)
                        }
                        val committedSpend = catTxs.filter { tx ->
                            recurringMerchants.contains(tx.merchant.lowercase().trim())
                        }.sumOf { it.amount }

                        CategoryStatsItem(
                            breakdown = cat,
                            categoryId = categoryIdByName[cat.categoryName],
                            comparisonPercent = comparisonPercent,
                            barFraction = barFraction,
                            percentageInt = textPctInt,
                            committedSpend = committedSpend
                        )
                    }

                    val matchingBudgetPeriod = when (ctx.period) {
                        TimePeriod.THIS_WEEK -> BudgetPeriod.WEEKLY
                        TimePeriod.THIS_MONTH -> BudgetPeriod.MONTHLY
                        TimePeriod.THIS_YEAR -> BudgetPeriod.YEARLY
                        else -> null
                    }
                    val activeBudget = if (matchingBudgetPeriod != null) {
                        ctx.budgets.firstOrNull {
                            it.isActive &&
                                it.category == BudgetCategories.GLOBAL &&
                                it.period == matchingBudgetPeriod
                        }
                    } else {
                        null
                    }

                    val budgetStatus = activeBudget?.let { budget ->
                        val spentForBudget = actualSpent.coerceAtLeast(0.0)
                        val progress = if (budget.amount > 0.0) {
                            (spentForBudget / budget.amount).toFloat().coerceIn(0f, 1f)
                        } else {
                            0f
                        }
                        val pctInt = if (budget.amount > 0.0) {
                            ((spentForBudget / budget.amount) * 100.0).roundToInt()
                        } else {
                            0
                        }
                        val remaining = budget.amount - spentForBudget
                        StatsBudgetStatus(
                            budget = budget,
                            spent = spentForBudget,
                            remaining = remaining,
                            progress = progress,
                            percentageInt = pctInt,
                            isExceeded = spentForBudget > budget.amount
                        )
                    }

                    val topMerchant = debitTxs
                        .filter { it.merchant.isNotBlank() }
                        .groupBy { it.merchant.trim() }
                        .mapValues { (_, list) -> list.sumOf { it.amount } }
                        .maxByOrNull { it.value }
                        ?.let { MerchantInsight(merchant = it.key, totalAmount = it.value) }

                    val tz = TimeZone.currentSystemDefault()
                    val highestSpendDay = debitTxs
                        .groupBy { tx ->
                            Instant.fromEpochMilliseconds(tx.timestamp).toLocalDateTime(tz).date
                        }
                        .mapValues { (_, list) -> list.sumOf { it.amount } }
                        .maxByOrNull { it.value }
                        ?.let { (date, amount) ->
                            DayInsight(
                                dateLabel = formatMonthDay(date),
                                totalAmount = amount
                            )
                        }

                    val displaySpent = if (actualSpent > 0.0 || debitTxs.isEmpty()) actualSpent else totalDebit

                    val activeSheet = when (activeSheetType) {
                        null -> null
                        StatsSheetType.TotalSpent -> StatsSheetState(
                            type = activeSheetType,
                            title = "Total Spent",
                            totalAmount = displaySpent,
                            transactions = debitTxs.sortedByDescending { it.timestamp },
                            categoryId = null,
                            txTypeFilter = TransactionType.DEBIT
                        )
                        StatsSheetType.TotalIncome -> StatsSheetState(
                            type = activeSheetType,
                            title = "Total Income",
                            totalAmount = totalIncome,
                            transactions = creditTxs.sortedByDescending { it.timestamp },
                            categoryId = null,
                            txTypeFilter = TransactionType.CREDIT
                        )
                        is StatsSheetType.Category -> {
                            val catTxs = debitTxs.filter {
                                (it.categoryName ?: "General") == activeSheetType.categoryName
                            }.sortedByDescending { it.timestamp }
                            val catTotal = breakdowns
                                .firstOrNull { it.categoryName == activeSheetType.categoryName }
                                ?.totalAmount ?: catTxs.sumOf { it.amount }
                            StatsSheetState(
                                type = activeSheetType,
                                title = activeSheetType.categoryName,
                                totalAmount = catTotal,
                                transactions = catTxs,
                                categoryId = activeSheetType.categoryId ?: categoryIdByName[activeSheetType.categoryName],
                                txTypeFilter = TransactionType.DEBIT
                            )
                        }
                    }

                    StatsUiState(
                        selectedPeriod = ctx.period,
                        customDateRange = ctx.customRange,
                        startTimestamp = range.startTimestamp,
                        endTimestamp = range.endTimestamp,
                        dateRangeLabel = formatDateRangeLabel(range.startTimestamp, range.endTimestamp),
                        categoryBreakdowns = categoryItems,
                        totalSpent = displaySpent,
                        totalIncome = totalIncome,
                        actualSpent = actualSpent,
                        cardOutstanding = cardOutstanding,
                        lentOut = lentOut,
                        budgetStatus = budgetStatus,
                        topMerchant = topMerchant,
                        highestSpendDay = highestSpendDay,
                        activeSheet = activeSheet,
                        isLoading = false
                    )
                }
            }.collect { state ->
                _uiState.value = state
            }
        }
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

    fun openSheet(type: StatsSheetType) {
        _activeSheetType.value = type
    }

    fun closeSheet() {
        _activeSheetType.value = null
    }

    private fun calculatePreviousRange(
        period: TimePeriod,
        currentRange: DateRange,
        nowMillis: Long
    ): DateRange {
        val tz = TimeZone.currentSystemDefault()
        val today = Instant.fromEpochMilliseconds(nowMillis).toLocalDateTime(tz).date

        return when (period) {
            TimePeriod.THIS_WEEK -> {
                val daysSinceMonday = today.dayOfWeek.ordinal
                val startOfThisWeek = today.minus(DatePeriod(days = daysSinceMonday))
                val startOfPrevWeek = startOfThisWeek.minus(DatePeriod(days = 7))
                val endOfPrevWeek = startOfThisWeek.minus(DatePeriod(days = 1))
                DateRange(
                    startTimestamp = LocalDateTime(startOfPrevWeek, LocalTime(0, 0, 0, 0)).toInstant(tz).toEpochMilliseconds(),
                    endTimestamp = LocalDateTime(endOfPrevWeek, LocalTime(23, 59, 59, 999_000_000)).toInstant(tz).toEpochMilliseconds()
                )
            }
            TimePeriod.THIS_MONTH -> {
                val startOfThisMonth = LocalDate(today.year, today.monthNumber, 1)
                val startOfPrevMonth = startOfThisMonth.minus(DatePeriod(months = 1))
                val startMs = LocalDateTime(startOfPrevMonth, LocalTime(0, 0, 0, 0)).toInstant(tz).toEpochMilliseconds()
                val endMs = LocalDateTime(startOfThisMonth, LocalTime(0, 0, 0, 0)).toInstant(tz).toEpochMilliseconds() - 1L
                DateRange(startMs, endMs)
            }
            TimePeriod.THIS_YEAR -> {
                val startOfThisYear = LocalDate(today.year, 1, 1)
                val startOfPrevYear = LocalDate(today.year - 1, 1, 1)
                val startMs = LocalDateTime(startOfPrevYear, LocalTime(0, 0, 0, 0)).toInstant(tz).toEpochMilliseconds()
                val endMs = LocalDateTime(startOfThisYear, LocalTime(0, 0, 0, 0)).toInstant(tz).toEpochMilliseconds() - 1L
                DateRange(startMs, endMs)
            }
            TimePeriod.CUSTOM -> {
                val duration = (currentRange.endTimestamp - currentRange.startTimestamp).coerceAtLeast(1L)
                val prevEnd = currentRange.startTimestamp - 1L
                val prevStart = (prevEnd - duration).coerceAtLeast(0L)
                DateRange(prevStart, prevEnd)
            }
            TimePeriod.TODAY -> {
                val yesterday = today.minus(DatePeriod(days = 1))
                DateRange(
                    startTimestamp = LocalDateTime(yesterday, LocalTime(0, 0, 0, 0)).toInstant(tz).toEpochMilliseconds(),
                    endTimestamp = LocalDateTime(yesterday, LocalTime(23, 59, 59, 999_000_000)).toInstant(tz).toEpochMilliseconds()
                )
            }
            TimePeriod.ALL_TIME -> DateRange(0L, 0L)
        }
    }

    private fun formatDateRangeLabel(startMillis: Long, endMillis: Long): String {
        if (startMillis <= 0L && endMillis == Long.MAX_VALUE) return "All Time"
        val tz = TimeZone.currentSystemDefault()
        val startDate = Instant.fromEpochMilliseconds(startMillis).toLocalDateTime(tz).date
        val endDate = Instant.fromEpochMilliseconds(endMillis).toLocalDateTime(tz).date
        return if (startDate.year == endDate.year) {
            "${formatMonthDay(startDate)} - ${formatMonthDay(endDate)}"
        } else {
            "${formatMonthDay(startDate)} ${startDate.year} - ${formatMonthDay(endDate)} ${endDate.year}"
        }
    }

    private fun formatMonthDay(date: LocalDate): String {
        val monthShort = date.month.name.take(3).lowercase().replaceFirstChar { it.uppercase() }
        return "$monthShort ${date.dayOfMonth}"
    }

    private data class PeriodQueryContext(
        val period: TimePeriod,
        val customRange: DateRange?,
        val budgets: List<Budget>,
        val categories: List<Category>,
        val recurringPatterns: List<com.cashbuddy.domain.model.RecurringPattern> = emptyList()
    )

    private data class Quad<A, B, C, D>(
        val first: A,
        val second: B,
        val third: C,
        val fourth: D
    )
}
