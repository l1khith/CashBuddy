package com.cashbuddy.presentation.addtransaction

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cashbuddy.domain.model.Account
import com.cashbuddy.domain.model.Category
import com.cashbuddy.domain.model.Transaction
import com.cashbuddy.domain.model.TransactionStatus
import com.cashbuddy.domain.model.TransactionType
import com.cashbuddy.domain.repository.AccountRepository
import com.cashbuddy.domain.repository.CategoryRepository
import com.cashbuddy.domain.repository.MerchantRuleRepository
import com.cashbuddy.domain.repository.TransactionRepository
import com.cashbuddy.domain.usecase.ManualAddTransactionUseCase
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import androidx.compose.runtime.Immutable
import kotlinx.datetime.toLocalDateTime

@Immutable
data class AddTransactionUiState(
    val categories: List<Category> = emptyList(),
    val accounts: List<Account> = emptyList(),
    val recentMerchants: List<String> = emptyList(),
    val frequentAmounts: List<Double> = listOf(50.0, 100.0, 200.0, 500.0, 1000.0, 2000.0),
    val suggestedCategoryId: Long? = null,
    val defaultMerchant: String = "",
    val isSaving: Boolean = false,
    val isLoading: Boolean = true
)

typealias ManualEntryViewModel = AddTransactionViewModel

class AddTransactionViewModel(
    private val manualAddTransactionUseCase: ManualAddTransactionUseCase,
    private val categoryRepository: CategoryRepository,
    private val accountRepository: AccountRepository,
    private val transactionRepository: TransactionRepository,
    private val merchantRuleRepository: MerchantRuleRepository,
    private val categoryEngine: com.cashbuddy.core.CategoryEngine? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddTransactionUiState())
    val uiState: StateFlow<AddTransactionUiState> = _uiState.asStateFlow()

    private val _prefilledUtr = MutableStateFlow<String?>(null)
    val prefilledUtr: StateFlow<String?> = _prefilledUtr.asStateFlow()

    fun setPrefilledUtr(utr: String?) {
        _prefilledUtr.value = utr
    }

    private val _savedEffect = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val savedEffect: SharedFlow<Unit> = _savedEffect.asSharedFlow()

    private var allTransactions: List<Transaction> = emptyList()

    init {
        viewModelScope.launch {
            val nowMillis = com.cashbuddy.platform.currentTimeMillis()
            val thirtyDaysAgo = nowMillis - (30L * 24 * 60 * 60 * 1000)
            
            combine(
                categoryRepository.getAll(),
                accountRepository.getAll(),
                transactionRepository.getRecent(limit = 100),
                transactionRepository.getByDateRange(thirtyDaysAgo, nowMillis)
            ) { cats, accs, txs, monthTxs ->
                allTransactions = txs

                // Extract recent unique merchants
                val recentMerchants = txs
                    .map { it.merchant.trim() }
                    .filter { it.isNotBlank() && !it.equals("Manual Entry", ignoreCase = true) }
                    .distinct()
                    .take(8)

                // Extract top frequent amounts
                val frequentAmounts = if (txs.isNotEmpty()) {
                    val amountCounts = txs.groupingBy { it.amount }.eachCount()
                    amountCounts.entries
                        .sortedByDescending { it.value }
                        .map { it.key }
                        .take(6)
                } else {
                    listOf(50.0, 100.0, 200.0, 500.0, 1000.0, 2000.0)
                }

                // Anticipatory prediction:
                // 1. Current time of day & day of week
                val currentDateTime = kotlinx.datetime.Instant.fromEpochMilliseconds(nowMillis)
                    .toLocalDateTime(TimeZone.currentSystemDefault())
                val currentHour = currentDateTime.hour

                // 2. Filter transactions in last 30 days within ±2 hour window of current time
                val timeWindowTxs = monthTxs.filter { tx ->
                    val txClean = tx.merchant.trim()
                    if (txClean.isBlank() || txClean.equals("Manual Entry", ignoreCase = true)) return@filter false
                    val txDateTime = kotlinx.datetime.Instant.fromEpochMilliseconds(tx.timestamp)
                        .toLocalDateTime(TimeZone.currentSystemDefault())
                    val diff = kotlin.math.abs(txDateTime.hour - currentHour)
                    diff <= 2 || diff >= 22 // Wrap around 24 hours
                }

                // Top 3 merchants within window
                val topMerchants = timeWindowTxs
                    .groupingBy { it.merchant.trim() }
                    .eachCount()
                    .entries
                    .sortedByDescending { it.value }
                    .take(3)
                    .map { it.key }

                val topMerchant = topMerchants.firstOrNull() ?: ""

                // 3. Most frequent category for these merchants or top merchant
                val candidateTxs = if (topMerchant.isNotBlank()) {
                    timeWindowTxs.filter { it.merchant.trim().equals(topMerchant, ignoreCase = true) }
                } else {
                    timeWindowTxs
                }

                val mostFrequentCatId = candidateTxs
                    .groupingBy { it.categoryId }
                    .eachCount()
                    .maxByOrNull { it.value }
                    ?.key

                val suggestedCategory = if (mostFrequentCatId != null && cats.any { it.id == mostFrequentCatId }) {
                    mostFrequentCatId
                } else {
                    cats.firstOrNull()?.id
                }

                AddTransactionUiState(
                    categories = cats,
                    accounts = accs,
                    recentMerchants = recentMerchants,
                    frequentAmounts = frequentAmounts,
                    suggestedCategoryId = suggestedCategory,
                    defaultMerchant = topMerchant,
                    isLoading = false
                )
            }.collect {
                _uiState.value = it
            }
        }
    }

    /**
     * Smart pre-fill: Predict category from user rules -> history -> CategoryEngine.
     * No hardcoded keyword lists. Learns from user's own transaction data.
     */
    fun onMerchantChanged(merchant: String): Long? {
        val cleanMerchant = merchant.trim()
        val categories = _uiState.value.categories
        if (cleanMerchant.isBlank()) {
            // Default to user's most frequently used category, or first available
            val defaultCatId = getMostFrequentCategoryId(categories)
            _uiState.value = _uiState.value.copy(suggestedCategoryId = defaultCatId)
            return defaultCatId
        }

        // 1. Check local DB history: Find most frequent category previously used for this merchant
        val matchingTx = allTransactions.filter { it.merchant.equals(cleanMerchant, ignoreCase = true) }
        if (matchingTx.isNotEmpty()) {
            val mostFrequentCatId = matchingTx
                .groupingBy { it.categoryId }
                .eachCount()
                .maxByOrNull { it.value }
                ?.key
            if (mostFrequentCatId != null && categories.any { it.id == mostFrequentCatId }) {
                _uiState.value = _uiState.value.copy(suggestedCategoryId = mostFrequentCatId)
                return mostFrequentCatId
            }
        }

        // 2. Delegate to CategoryEngine (handles user rules, VPA grammar, learned tokens)
        val engine = categoryEngine ?: com.cashbuddy.core.CategoryEngine()
        val engineMatch = engine.getCategory(rawMerchant = cleanMerchant)
        if (engineMatch.category != "Unknown" && engineMatch.confidence >= 0.80f) {
            val matchedCat = categories.find { cat ->
                val catLower = cat.name.lowercase()
                val target = engineMatch.category.lowercase()
                catLower == target || catLower.contains(target.take(4)) || target.contains(catLower.take(4))
            }
            if (matchedCat != null) {
                _uiState.value = _uiState.value.copy(suggestedCategoryId = matchedCat.id)
                return matchedCat.id
            }
        }

        // 3. Fallback: user's most frequently used category, or first available
        val fallbackCatId = getMostFrequentCategoryId(categories)
        _uiState.value = _uiState.value.copy(suggestedCategoryId = fallbackCatId)
        return fallbackCatId
    }

    /**
     * Returns the category ID the user uses most frequently,
     * based on their own transaction history. No hardcoded assumptions.
     */
    private fun getMostFrequentCategoryId(categories: List<Category>): Long? {
        if (allTransactions.isEmpty()) return categories.firstOrNull()?.id

        val mostUsedCatId = allTransactions
            .groupingBy { it.categoryId }
            .eachCount()
            .maxByOrNull { it.value }
            ?.key

        return if (mostUsedCatId != null && categories.any { it.id == mostUsedCatId }) {
            mostUsedCatId
        } else {
            categories.firstOrNull()?.id
        }
    }

    private var isSaving = false

    fun saveTransaction(
        amount: Double,
        type: TransactionType,
        merchant: String,
        categoryId: Long,
        accountId: Long,
        notes: String?
    ) {
        if (isSaving) return
        isSaving = true
        _uiState.value = _uiState.value.copy(isSaving = true)
        viewModelScope.launch {
            try {
                val now = com.cashbuddy.platform.currentTimeMillis()
                val cleanMerchant = merchant.ifBlank { "Manual Entry" }
                val utr = _prefilledUtr.value
                val effectiveNotes = when {
                    !utr.isNullOrBlank() && (notes.isNullOrBlank() || !notes.contains(utr)) ->
                        if (notes.isNullOrBlank()) "UPI Ref: $utr" else "$notes (UPI Ref: $utr)"
                    else -> notes
                }
                val effectiveRaw = when {
                    !utr.isNullOrBlank() -> "Manual transaction entry (UTR: $utr)"
                    !effectiveNotes.isNullOrBlank() -> "Manual transaction entry ($effectiveNotes)"
                    else -> "Manual transaction entry"
                }

                val tx = Transaction(
                    id = 0L,
                    amount = amount,
                    type = type,
                    currency = "INR",
                    merchant = cleanMerchant,
                    categoryId = categoryId,
                    accountId = accountId,
                    sourceApp = "manual",
                    rawText = effectiveRaw,
                    confidence = 1.0f,
                    status = TransactionStatus.CONFIRMED,
                    notes = effectiveNotes,
                    timestamp = now,
                    createdAt = now,
                    updatedAt = now,
                    utr = utr
                )

                // Learn rule for future auto-categorization
                if (cleanMerchant != "Manual Entry") {
                    merchantRuleRepository.learnRule(cleanMerchant, categoryId)
                }

                manualAddTransactionUseCase(tx)
                com.cashbuddy.platform.notifyTransactionsUpdated()
                _savedEffect.emit(Unit)
            } finally {
                isSaving = false
                _uiState.value = _uiState.value.copy(isSaving = false)
            }
        }
    }

    fun prefillTransaction(
        id: Long,
        onLoaded: (amount: Double, type: TransactionType, merchant: String, categoryId: Long, accountId: Long, notes: String?) -> Unit
    ) {
        viewModelScope.launch {
            transactionRepository.getById(id).firstOrNull()?.let { tx ->
                onLoaded(
                    tx.amount,
                    tx.type,
                    tx.merchant,
                    tx.categoryId,
                    tx.accountId ?: 1L,
                    tx.notes
                )
            }
        }
    }
}
