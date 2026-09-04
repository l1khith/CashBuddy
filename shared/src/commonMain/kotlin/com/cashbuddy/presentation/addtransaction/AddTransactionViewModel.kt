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
import com.cashbuddy.domain.usecase.ManualAddTransactionUseCase
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class AddTransactionUiState(
    val categories: List<Category> = emptyList(),
    val accounts: List<Account> = emptyList(),
    val isLoading: Boolean = true
)

class AddTransactionViewModel(
    private val manualAddTransactionUseCase: ManualAddTransactionUseCase,
    private val categoryRepository: CategoryRepository,
    private val accountRepository: AccountRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddTransactionUiState())
    val uiState: StateFlow<AddTransactionUiState> = _uiState.asStateFlow()

    private val _savedEffect = MutableSharedFlow<Unit>()
    val savedEffect: SharedFlow<Unit> = _savedEffect.asSharedFlow()

    init {
        viewModelScope.launch {
            combine(
                categoryRepository.getAll(),
                accountRepository.getAll()
            ) { cats, accs ->
                AddTransactionUiState(
                    categories = cats,
                    accounts = accs,
                    isLoading = false
                )
            }.collect {
                _uiState.value = it
            }
        }
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
            val tx = Transaction(
                id = 0L,
                amount = amount,
                type = type,
                currency = "INR",
                merchant = merchant.ifBlank { "Manual Entry" },
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
            manualAddTransactionUseCase(tx)
            _savedEffect.emit(Unit)
        }
    }
}
