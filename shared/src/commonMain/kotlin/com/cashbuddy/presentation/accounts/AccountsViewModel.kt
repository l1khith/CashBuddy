package com.cashbuddy.presentation.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cashbuddy.domain.model.Account
import com.cashbuddy.domain.model.AccountType
import com.cashbuddy.domain.repository.AccountRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AccountsUiState(
    val accounts: List<Account> = emptyList(),
    val totalBalance: Double = 0.0,
    val isLoading: Boolean = true
)

class AccountsViewModel(
    private val accountRepository: AccountRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AccountsUiState())
    val uiState: StateFlow<AccountsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            accountRepository.getAll().collect { accountsList ->
                _uiState.value = AccountsUiState(
                    accounts = accountsList,
                    totalBalance = accountsList.sumOf { it.balance },
                    isLoading = false
                )
            }
        }
    }

    fun addAccount(name: String, type: AccountType, balance: Double, number: String?) {
        viewModelScope.launch {
            val now = com.cashbuddy.platform.currentTimeMillis()
            val account = Account(
                id = 0L,
                name = name,
                type = type,
                number = number,
                bank = name,
                balance = balance,
                currency = "INR",
                isActive = true,
                sortOrder = 0,
                createdAt = now,
                updatedAt = now
            )
            accountRepository.insert(account)
        }
    }

    fun deleteAccount(id: Long) {
        viewModelScope.launch {
            accountRepository.deleteById(id)
        }
    }
}
