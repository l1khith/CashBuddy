package com.cashbuddy.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cashbuddy.domain.repository.AccountRepository
import com.cashbuddy.domain.repository.SettingsRepository
import com.cashbuddy.domain.repository.TransactionRepository
import com.cashbuddy.domain.usecase.ExportDataUseCase
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class SettingsUiState(
    val notificationEnabled: Boolean = true,
    val autoConfirmThreshold: Double = 10000.0,
    val autoConfirmMinConfidence: Float = 0.85f,
    val biometricLockEnabled: Boolean = false,
    val totalTransactionsCount: Int = 0,
    val totalAccountsCount: Int = 0,
    val isLoading: Boolean = true
)

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val transactionRepository: TransactionRepository,
    private val accountRepository: AccountRepository,
    private val exportDataUseCase: ExportDataUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private val _messageEffect = MutableSharedFlow<String>()
    val messageEffect: SharedFlow<String> = _messageEffect.asSharedFlow()

    init {
        viewModelScope.launch {
            combine(
                settingsRepository.getSettings(),
                transactionRepository.getAll(),
                accountRepository.getAll()
            ) { settings, txs, accounts ->
                SettingsUiState(
                    notificationEnabled = settings.notificationsEnabled,
                    autoConfirmThreshold = settings.autoConfirmThreshold,
                    autoConfirmMinConfidence = settings.minConfidenceThreshold,
                    biometricLockEnabled = settings.biometricEnabled,
                    totalTransactionsCount = txs.size,
                    totalAccountsCount = accounts.size,
                    isLoading = false
                )
            }.collect {
                _uiState.value = it
            }
        }
    }

    fun setNotificationEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setNotificationEnabled(enabled)
        }
    }

    fun setAutoConfirmThreshold(threshold: Double) {
        viewModelScope.launch {
            settingsRepository.setAutoConfirmThreshold(threshold)
        }
    }

    fun setAutoConfirmMinConfidence(confidence: Float) {
        viewModelScope.launch {
            settingsRepository.setMinConfidenceThreshold(confidence)
        }
    }

    fun setBiometricLockEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setBiometricEnabled(enabled)
        }
    }

    fun exportCsvData(onCsvReady: (String) -> Unit) {
        viewModelScope.launch {
            val csv = exportDataUseCase.exportCsv()
            onCsvReady(csv)
            _messageEffect.emit("Data exported successfully")
        }
    }

    fun clearAllData() {
        viewModelScope.launch {
            val allTxs = _uiState.value.totalTransactionsCount
            transactionRepository.getAll().collect { list ->
                list.forEach { tx ->
                    transactionRepository.deleteById(tx.id)
                }
            }
            _messageEffect.emit("All data deleted ($allTxs transactions)")
        }
    }
}
