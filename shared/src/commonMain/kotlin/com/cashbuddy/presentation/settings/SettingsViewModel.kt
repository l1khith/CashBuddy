package com.cashbuddy.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cashbuddy.domain.repository.AccountRepository
import com.cashbuddy.domain.repository.SettingsRepository
import com.cashbuddy.domain.repository.TransactionRepository
import com.cashbuddy.domain.usecase.ExportDataUseCase
import com.cashbuddy.platform.FileExporter
import com.cashbuddy.platform.currentTimeMillis
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import com.cashbuddy.domain.repository.TrainingDataRepository
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Immutable
data class SettingsUiState(
    val notificationEnabled: Boolean = true,
    val autoConfirmThreshold: Double = 10000.0,
    val biometricLockEnabled: Boolean = false,
    val totalTransactionsCount: Int = 0,
    val totalAccountsCount: Int = 0,
    val rawTrainingSamplesCount: Long = 0,
    val userCorrectionsCount: Long = 0,
    val isDeveloperMode: Boolean = false,
    val isDebugLogEnabled: Boolean = false,
    val isDebugLogVisible: Boolean = true,
    val preferredCurrency: String = "INR",
    val isLoading: Boolean = true,
    val isScanningDuplicates: Boolean = false,
    val duplicateGroups: List<com.cashbuddy.core.DuplicateReconciler.DuplicateGroup> = emptyList(),
    val hasRecentMerges: Boolean = false,
    val isMerging: Boolean = false
)

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val transactionRepository: TransactionRepository,
    private val accountRepository: AccountRepository,
    private val exportDataUseCase: ExportDataUseCase,
    private val trainingDataRepository: TrainingDataRepository,
    private val fileExporter: FileExporter? = null,
    private val debugConfig: com.cashbuddy.debug.DebugConfig? = null,
    private val duplicateReconciler: com.cashbuddy.core.DuplicateReconciler? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private val _messageEffect = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val messageEffect: SharedFlow<String> = _messageEffect.asSharedFlow()

    private var versionTapCount = 0

    init {
        viewModelScope.launch {
            combine(
                settingsRepository.getSettings(),
                transactionRepository.getCount(),
                accountRepository.getCount(),
                trainingDataRepository.getStats(),
                settingsRepository.getDebugLogEnabled()
            ) { settings, txCount, accCount, stats, debugLogEnabled ->
                if (debugConfig != null) {
                    debugConfig.cachedDeveloperMode = debugLogEnabled
                }
                SettingsUiState(
                    notificationEnabled = settings.notificationsEnabled,
                    autoConfirmThreshold = settings.autoConfirmThreshold,
                    biometricLockEnabled = settings.biometricEnabled,
                    totalTransactionsCount = txCount.toInt(),
                    totalAccountsCount = accCount.toInt(),
                    rawTrainingSamplesCount = stats.rawCount,
                    userCorrectionsCount = stats.correctionsCount,
                    isDeveloperMode = debugLogEnabled,
                    isDebugLogEnabled = debugLogEnabled,
                    isDebugLogVisible = true,
                    preferredCurrency = settings.preferredCurrency,
                    isLoading = false
                )
            }.collect {
                _uiState.value = it
            }
        }
    }

    fun onVersionClicked() {
        versionTapCount++
        if (versionTapCount >= 7) {
            versionTapCount = 0
            val newMode = !_uiState.value.isDebugLogEnabled
            setDebugLogEnabled(newMode)
        }
    }

    fun setDebugLogEnabled(enabled: Boolean) {
        viewModelScope.launch {
            if (debugConfig != null) {
                debugConfig.updateDeveloperMode(enabled)
            } else {
                settingsRepository.setDebugLogEnabled(enabled)
            }
            _messageEffect.emit(
                if (enabled) "Debug logging enabled • Capturing parser logs"
                else "Debug logging disabled • Zero logs recorded"
            )
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

    fun setPreferredCurrency(currencyCode: String) {
        viewModelScope.launch {
            settingsRepository.setPreferredCurrency(currencyCode)
        }
    }

    fun setBiometricLockEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setBiometricEnabled(enabled)
        }
    }

    fun exportCsvData(onCsvReady: (String) -> Unit = {}) {
        viewModelScope.launch {
            val csv = exportDataUseCase.exportCsv()
            onCsvReady(csv)
            if (fileExporter != null) {
                val fileName = "cashbuddy_ledger_${currentTimeMillis()}.csv"
                val result = fileExporter.exportCsvFile(fileName, csv)
                result.fold(
                    onSuccess = { path ->
                        _messageEffect.emit(path)
                    },
                    onFailure = { err ->
                        _messageEffect.emit("Export failed: ${err.message}")
                    }
                )
            } else {
                _messageEffect.emit("Data exported successfully")
            }
        }
    }

    fun exportTrainingDataset(onJsonlReady: (String) -> Unit = {}) {
        viewModelScope.launch {
            val jsonl = trainingDataRepository.exportTrainingDataJsonl()
            onJsonlReady(jsonl)
            if (fileExporter != null) {
                val fileName = "cashbuddy_training_${currentTimeMillis()}.jsonl"
                val result = fileExporter.exportCsvFile(fileName, jsonl)
                result.fold(
                    onSuccess = { path ->
                        _messageEffect.emit("Training dataset exported: $path")
                    },
                    onFailure = { err ->
                        _messageEffect.emit("Training export failed: ${err.message}")
                    }
                )
            } else {
                _messageEffect.emit("Training dataset generated (${_uiState.value.rawTrainingSamplesCount} raw, ${_uiState.value.userCorrectionsCount} corrections)")
            }
        }
    }

    fun clearTrainingData() {
        viewModelScope.launch {
            trainingDataRepository.clearTrainingData()
            _messageEffect.emit("Training data & corrections wiped")
        }
    }

    fun clearAllData() {
        viewModelScope.launch {
            val allTxs = _uiState.value.totalTransactionsCount
            val list = transactionRepository.getAll().first()
            list.forEach { tx ->
                transactionRepository.deleteById(tx.id)
            }
            _messageEffect.emit("All data deleted ($allTxs transactions)")
        }
    }

    fun scanDuplicates(onComplete: (Int) -> Unit = {}) {
        val reconciler = duplicateReconciler ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isScanningDuplicates = true)
            val groups = reconciler.findDuplicateGroups()
            val mergeCount = transactionRepository.getMergeLogCount()
            _uiState.value = _uiState.value.copy(
                isScanningDuplicates = false,
                duplicateGroups = groups,
                hasRecentMerges = mergeCount > 0L
            )
            onComplete(groups.size)
        }
    }

    fun mergeDuplicates(groups: List<com.cashbuddy.core.DuplicateReconciler.DuplicateGroup>, onComplete: (Int) -> Unit = {}) {
        val reconciler = duplicateReconciler ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isMerging = true)
            val summary = reconciler.mergeDuplicateGroups(groups)
            val remaining = reconciler.findDuplicateGroups()
            val mergeCount = transactionRepository.getMergeLogCount()
            _uiState.value = _uiState.value.copy(
                isMerging = false,
                duplicateGroups = remaining,
                hasRecentMerges = mergeCount > 0L
            )
            _messageEffect.emit("Merged ${summary.totalDuplicatesMerged} duplicates across ${summary.groupsMerged} groups")
            onComplete(summary.totalDuplicatesMerged)
        }
    }

    fun undoLastMerge(onComplete: (Int) -> Unit = {}) {
        val reconciler = duplicateReconciler ?: return
        viewModelScope.launch {
            val undone = reconciler.undoLastMerge()
            val remaining = reconciler.findDuplicateGroups()
            val mergeCount = transactionRepository.getMergeLogCount()
            _uiState.value = _uiState.value.copy(
                duplicateGroups = remaining,
                hasRecentMerges = mergeCount > 0L
            )
            if (undone > 0) {
                _messageEffect.emit("Undone: restored $undone merged transactions")
            } else {
                _messageEffect.emit("No recent merges to undo")
            }
            onComplete(undone)
        }
    }

    fun exportDuplicatePreviewCsv(): String {
        val groups = _uiState.value.duplicateGroups
        val sb = StringBuilder()
        sb.append("Group,Role,TransactionId,Amount,Merchant,Confidence,Status,Timestamp,SourceApp,RawText\n")
        groups.forEachIndexed { groupIndex, group ->
            val gNum = groupIndex + 1
            val survivor = group.survivor
            sb.append("$gNum,SURVIVOR,${survivor.id},${survivor.amount},\"${survivor.merchant}\",${survivor.confidence},${survivor.status},${survivor.timestamp},\"${survivor.sourceApp}\",\"${survivor.rawText.replace("\"", "\"\"")}\"\n")
            group.duplicates.forEach { dup ->
                sb.append("$gNum,DUPLICATE_TO_MERGE,${dup.id},${dup.amount},\"${dup.merchant}\",${dup.confidence},${dup.status},${dup.timestamp},\"${dup.sourceApp}\",\"${dup.rawText.replace("\"", "\"\"")}\"\n")
            }
        }
        return sb.toString()
    }
}
