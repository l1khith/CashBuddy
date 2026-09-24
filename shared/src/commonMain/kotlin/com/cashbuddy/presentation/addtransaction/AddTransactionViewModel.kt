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
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

data class AddTransactionUiState(
    val categories: List<Category> = emptyList(),
    val accounts: List<Account> = emptyList(),
    val recentMerchants: List<String> = emptyList(),
    val frequentAmounts: List<Double> = listOf(50.0, 100.0, 200.0, 500.0, 1000.0, 2000.0),
    val suggestedCategoryId: Long? = null,
    val isLoading: Boolean = true
)

typealias ManualEntryViewModel = AddTransactionViewModel

class AddTransactionViewModel(
    private val manualAddTransactionUseCase: ManualAddTransactionUseCase,
    private val categoryRepository: CategoryRepository,
    private val accountRepository: AccountRepository,
    private val transactionRepository: TransactionRepository,
    private val merchantRuleRepository: MerchantRuleRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddTransactionUiState())
    val uiState: StateFlow<AddTransactionUiState> = _uiState.asStateFlow()

    private val _savedEffect = MutableSharedFlow<Unit>()
    val savedEffect: SharedFlow<Unit> = _savedEffect.asSharedFlow()

    private var allTransactions: List<Transaction> = emptyList()

    init {
        viewModelScope.launch {
            combine(
                categoryRepository.getAll(),
                accountRepository.getAll(),
                transactionRepository.getAll()
            ) { cats, accs, txs ->
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

                // Initial time-based category default
                val defaultCategory = getTimeBasedDefaultCategory(cats)

                AddTransactionUiState(
                    categories = cats,
                    accounts = accs,
                    recentMerchants = recentMerchants,
                    frequentAmounts = frequentAmounts,
                    suggestedCategoryId = defaultCategory?.id ?: cats.firstOrNull()?.id,
                    isLoading = false
                )
            }.collect {
                _uiState.value = it
            }
        }
    }

    /**
     * Smart pre-fill: Predict category from user rules -> history -> time-based defaults.
     */
    fun onMerchantChanged(merchant: String): Long? {
        val cleanMerchant = merchant.trim()
        if (cleanMerchant.isBlank()) {
            return getTimeBasedDefaultCategory(_uiState.value.categories)?.id
        }

        val categories = _uiState.value.categories

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

        // 2. Keyword matching heuristic
        val lowerMerchant = cleanMerchant.lowercase()
        val keywordMatchedCat = categories.find { cat ->
            val catLower = cat.name.lowercase()
            when {
                lowerMerchant.contains("swiggy") || lowerMerchant.contains("zomato") || lowerMerchant.contains("chai") || lowerMerchant.contains("cafe") || lowerMerchant.contains("food") -> catLower.contains("food")
                lowerMerchant.contains("uber") || lowerMerchant.contains("ola") || lowerMerchant.contains("metro") || lowerMerchant.contains("petrol") || lowerMerchant.contains("fuel") -> catLower.contains("transport")
                lowerMerchant.contains("blinkit") || lowerMerchant.contains("zepto") || lowerMerchant.contains("instamart") || lowerMerchant.contains("dmart") || lowerMerchant.contains("grocer") -> catLower.contains("grocer")
                lowerMerchant.contains("amazon") || lowerMerchant.contains("flipkart") || lowerMerchant.contains("myntra") -> catLower.contains("shop")
                lowerMerchant.contains("airtel") || lowerMerchant.contains("jio") || lowerMerchant.contains("bescom") || lowerMerchant.contains("electricity") || lowerMerchant.contains("bill") -> catLower.contains("bill") || catLower.contains("util")
                lowerMerchant.contains("apollo") || lowerMerchant.contains("pharm") || lowerMerchant.contains("hospital") || lowerMerchant.contains("clinic") -> catLower.contains("health")
                else -> false
            }
        }
        if (keywordMatchedCat != null) {
            _uiState.value = _uiState.value.copy(suggestedCategoryId = keywordMatchedCat.id)
            return keywordMatchedCat.id
        }

        // 3. Fallback to time-based defaults
        val timeDefault = getTimeBasedDefaultCategory(categories)
        if (timeDefault != null) {
            _uiState.value = _uiState.value.copy(suggestedCategoryId = timeDefault.id)
            return timeDefault.id
        }

        return categories.firstOrNull()?.id
    }

    private fun getTimeBasedDefaultCategory(categories: List<Category>): Category? {
        val nowMillis = com.cashbuddy.platform.currentTimeMillis()
        val now = kotlinx.datetime.Instant.fromEpochMilliseconds(nowMillis)
            .toLocalDateTime(TimeZone.currentSystemDefault())
        val hour = now.hour

        val targetCategoryName = when (hour) {
            in 7..10 -> "Food & Dining"       // Breakfast
            in 12..14 -> "Food & Dining"      // Lunch
            in 17..19 -> "Transportation"     // Evening commute
            in 20..22 -> "Food & Dining"      // Dinner
            else -> "Shopping & Retail"
        }

        return categories.find { it.name.equals(targetCategoryName, ignoreCase = true) }
            ?: categories.find { it.name.contains("Food", ignoreCase = true) }
            ?: categories.firstOrNull()
    }

    fun saveTransaction(
        amount: Double,
        type: TransactionType,
        merchant: String,
        categoryId: Long,
        accountId: Long,
        notes: String?
    ) {
        viewModelScope.launch {
            val now = com.cashbuddy.platform.currentTimeMillis()
            val cleanMerchant = merchant.ifBlank { "Manual Entry" }

            val tx = Transaction(
                id = 0L,
                amount = amount,
                type = type,
                currency = "INR",
                merchant = cleanMerchant,
                categoryId = categoryId,
                accountId = accountId,
                sourceApp = "manual",
                rawText = "Manual transaction entry",
                confidence = 1.0f,
                status = TransactionStatus.CONFIRMED,
                notes = notes,
                timestamp = now,
                createdAt = now,
                updatedAt = now
            )

            // Learn rule for future auto-categorization
            if (cleanMerchant != "Manual Entry") {
                merchantRuleRepository.learnRule(cleanMerchant, categoryId)
            }

            manualAddTransactionUseCase(tx)
            _savedEffect.emit(Unit)
        }
    }
}
