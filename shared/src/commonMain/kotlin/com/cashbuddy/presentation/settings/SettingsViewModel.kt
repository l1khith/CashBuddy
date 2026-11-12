package com.cashbuddy.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cashbuddy.domain.repository.AccountRepository
import com.cashbuddy.domain.repository.SettingsRepository
import com.cashbuddy.domain.repository.TransactionRepository
import com.cashbuddy.domain.usecase.ExportDataUseCase
import com.cashbuddy.platform.FileExporter
import com.cashbuddy.platform.currentTimeMillis
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import com.cashbuddy.domain.repository.TrainingDataRepository
import com.cashbuddy.domain.model.TrainingStats
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import cashbuddy.shared.generated.resources.Res
import cashbuddy.shared.generated.resources.recurrence_patterns_refreshed

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
    val isMerging: Boolean = false,
    val isOcrAvailable: Boolean = false,
    val ignoredPackages: List<String> = emptyList(),
    val isImportingCsv: Boolean = false,
    val smartNotificationsEnabled: Boolean = true,
    val customAppName: String = com.cashbuddy.domain.model.UserSettings.DEFAULT_APP_NAME,
    val customAppSubtitle: String = com.cashbuddy.domain.model.UserSettings.DEFAULT_APP_SUBTITLE,
    val showAppLogo: Boolean = true,
    val navOrder: String = com.cashbuddy.domain.model.UserSettings.DEFAULT_NAV_ORDER,
    val navOrderList: List<String> = listOf("home", "stats", "history"),
    val showHealthInTopBar: Boolean = false,
    val isNotificationAccessGranted: Boolean = false,
    val lastSyncTimestamp: Long = 0L,
    val dailyWrapUpHour: Int = com.cashbuddy.domain.model.UserSettings.DEFAULT_WRAP_UP_HOUR,
    val dailyWrapUpMinute: Int = com.cashbuddy.domain.model.UserSettings.DEFAULT_WRAP_UP_MINUTE
)

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val transactionRepository: TransactionRepository,
    private val accountRepository: AccountRepository,
    private val exportDataUseCase: ExportDataUseCase,
    private val trainingDataRepository: TrainingDataRepository,
    private val fileExporter: FileExporter? = null,
    private val debugConfig: com.cashbuddy.debug.DebugConfig? = null,
    private val duplicateReconciler: com.cashbuddy.core.DuplicateReconciler? = null,
    private val ocrEngine: com.cashbuddy.ocr.OcrEngine? = null,
    private val importDataUseCase: com.cashbuddy.domain.usecase.ImportDataUseCase? = null,
    // C10: learned tokens are derived from training data — clearing one clears the other,
    // otherwise clearAllData leaves the classifier's learned vocabulary inconsistent.
    private val learnedTokenRepository: com.cashbuddy.domain.repository.LearnedTokenRepository? = null,
    // Audit fix P1-C: wipe all data in ONE driver-level transaction instead of four
    // separate repository transactions from the ViewModel.
    private val dataResetStore: com.cashbuddy.domain.repository.DataResetStore? = null,
    private val diagnosticsProvider: com.cashbuddy.platform.PlatformDiagnosticsProvider? = null,
    private val detectAndStoreRecurrencesUseCase: com.cashbuddy.domain.usecase.DetectAndStoreRecurrencesUseCase? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState(isOcrAvailable = ocrEngine?.isAvailable ?: false))
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private val _messageEffect = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val messageEffect: SharedFlow<String> = _messageEffect.asSharedFlow()

    private var versionTapCount = 0

    // Instant UI draft overrides so typing and dragging never stutter waiting for SQLite writes
    private val _pendingAppName = MutableStateFlow<String?>(null)
    private val _pendingAppSubtitle = MutableStateFlow<String?>(null)
    private val _pendingNavOrder = MutableStateFlow<List<String>?>(null)

    @OptIn(FlowPreview::class)
    private fun startCustomizationPersistence() {
        viewModelScope.launch {
            _pendingAppName.filterNotNull().debounce(300).collectLatest { name ->
                settingsRepository.setCustomAppName(name)
                if (_pendingAppName.value == name) {
                    _pendingAppName.value = null
                }
            }
        }
        viewModelScope.launch {
            _pendingAppSubtitle.filterNotNull().debounce(300).collectLatest { subtitle ->
                settingsRepository.setCustomAppSubtitle(subtitle)
                if (_pendingAppSubtitle.value == subtitle) {
                    _pendingAppSubtitle.value = null
                }
            }
        }
    }

    init {
        startCustomizationPersistence()

        viewModelScope.launch {
            combine(
                settingsRepository.getSettings(),
                transactionRepository.getCount(),
                accountRepository.getCount(),
                trainingDataRepository.getStats(),
                settingsRepository.getDebugLogEnabled(),
                settingsRepository.getIgnoredPackages(),
                _pendingAppName,
                _pendingAppSubtitle,
                _pendingNavOrder
            ) { args: Array<Any?> ->
                val settings = args[0] as com.cashbuddy.domain.model.UserSettings
                val txCount = args[1] as Long
                val accCount = args[2] as Long
                val stats = args[3] as TrainingStats
                val debugLogEnabled = args[4] as Boolean
                @Suppress("UNCHECKED_CAST")
                val ignoredPkgs = args[5] as List<String>
                val pendingName = args[6] as String?
                val pendingSubtitle = args[7] as String?
                @Suppress("UNCHECKED_CAST")
                val pendingOrder = args[8] as List<String>?

                if (debugConfig != null) {
                    debugConfig.cachedDeveloperMode = debugLogEnabled
                }
                val activeOrder = pendingOrder ?: parseNavOrder(settings.navOrder)
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
                    isLoading = false,
                    isOcrAvailable = ocrEngine?.isAvailable ?: false,
                    ignoredPackages = ignoredPkgs,
                    smartNotificationsEnabled = settings.smartNotificationsEnabled,
                    customAppName = pendingName ?: settings.customAppName,
                    customAppSubtitle = pendingSubtitle ?: settings.customAppSubtitle,
                    showAppLogo = settings.showAppLogo,
                    navOrder = activeOrder.joinToString(","),
                    navOrderList = activeOrder,
                    showHealthInTopBar = settings.showHealthInTopBar,
                    isNotificationAccessGranted = diagnosticsProvider?.checkDiagnostics(settings.lastSyncTimestamp)?.isNotificationAccessGranted ?: false,
                    lastSyncTimestamp = settings.lastSyncTimestamp,
                    dailyWrapUpHour = settings.dailyWrapUpHour,
                    dailyWrapUpMinute = settings.dailyWrapUpMinute
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

    fun setSmartNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setSmartNotificationsEnabled(enabled)
        }
    }

    fun forceRefreshRecurrencePatterns() {
        viewModelScope.launch {
            try {
                val patterns = detectAndStoreRecurrencesUseCase?.invoke() ?: emptyList()
                val msg = org.jetbrains.compose.resources.getString(Res.string.recurrence_patterns_refreshed, patterns.size)
                _messageEffect.emit(msg)
            } catch (e: Throwable) {
                _messageEffect.emit("Failed to update patterns: ${e.message}")
            }
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

    fun setCustomAppName(name: String) {
        _pendingAppName.value = name.take(com.cashbuddy.domain.model.UserSettings.MAX_APP_NAME_LENGTH)
    }

    fun setCustomAppSubtitle(subtitle: String) {
        _pendingAppSubtitle.value = subtitle.take(com.cashbuddy.domain.model.UserSettings.MAX_APP_SUBTITLE_LENGTH)
    }

    fun setShowAppLogo(show: Boolean) {
        viewModelScope.launch {
            settingsRepository.setShowAppLogo(show)
        }
    }

    fun setNavOrder(order: String) {
        viewModelScope.launch {
            settingsRepository.setNavOrder(order)
        }
    }

    fun moveNavItem(fromIndex: Int, toIndex: Int) {
        val current = (_pendingNavOrder.value ?: _uiState.value.navOrderList).toMutableList()
        if (fromIndex !in current.indices || toIndex !in current.indices || fromIndex == toIndex) return
        val moved = current.removeAt(fromIndex)
        current.add(toIndex, moved)
        _pendingNavOrder.value = current
    }

    fun commitNavOrder(newOrder: List<String>) {
        val serialized = newOrder.joinToString(",")
        _pendingNavOrder.value = newOrder
        viewModelScope.launch {
            settingsRepository.setNavOrder(serialized)
            if (_pendingNavOrder.value == newOrder) {
                _pendingNavOrder.value = null
            }
        }
    }

    fun resetAppCustomization() {
        val defaultOrder = parseNavOrder(com.cashbuddy.domain.model.UserSettings.DEFAULT_NAV_ORDER)
        _pendingAppName.value = com.cashbuddy.domain.model.UserSettings.DEFAULT_APP_NAME
        _pendingAppSubtitle.value = com.cashbuddy.domain.model.UserSettings.DEFAULT_APP_SUBTITLE
        _pendingNavOrder.value = defaultOrder
        viewModelScope.launch {
            settingsRepository.setCustomAppName(com.cashbuddy.domain.model.UserSettings.DEFAULT_APP_NAME)
            settingsRepository.setCustomAppSubtitle(com.cashbuddy.domain.model.UserSettings.DEFAULT_APP_SUBTITLE)
            settingsRepository.setShowAppLogo(true)
            settingsRepository.setNavOrder(com.cashbuddy.domain.model.UserSettings.DEFAULT_NAV_ORDER)
            _pendingAppName.value = null
            _pendingAppSubtitle.value = null
            _pendingNavOrder.value = null
            _messageEffect.emit("App customization reset to defaults")
        }
    }

    private fun parseNavOrder(raw: String): List<String> {
        val defaults = listOf("home", "stats", "history")
        val parsed = raw.split(",")
            .map { it.trim().lowercase() }
            .filter { it in defaults }
            .distinct()
        return (parsed + defaults).distinct()
    }

    fun exportCsvData(onCsvReady: (String) -> Unit = {}) {
        viewModelScope.launch(com.cashbuddy.platform.ioDispatcher) {
            if (fileExporter != null) {
                val fileName = "cashbuddy_ledger_${currentTimeMillis()}.csv"
                val result = fileExporter.exportFileStream(fileName, "text/csv") { write ->
                    exportDataUseCase.streamCsv(write)
                }
                result.fold(
                    onSuccess = { path ->
                        _messageEffect.emit(path)
                    },
                    onFailure = { err ->
                        _messageEffect.emit("Export failed: ${err.message}")
                    }
                )
            } else {
                val csv = exportDataUseCase.exportCsv()
                onCsvReady(csv)
                _messageEffect.emit("Data exported successfully")
            }
        }
    }

    fun exportTrainingDataset(onJsonlReady: (String) -> Unit = {}) {
        viewModelScope.launch(com.cashbuddy.platform.ioDispatcher) {
            if (fileExporter != null) {
                val fileName = "cashbuddy_training_${currentTimeMillis()}.jsonl"
                val result = fileExporter.exportFileStream(fileName, "application/jsonl") { write ->
                    trainingDataRepository.streamTrainingDataJsonl(write)
                }
                result.fold(
                    onSuccess = { path ->
                        _messageEffect.emit("Training dataset exported: $path")
                    },
                    onFailure = { err ->
                        _messageEffect.emit("Training export failed: ${err.message}")
                    }
                )
            } else {
                val jsonl = trainingDataRepository.exportTrainingDataJsonl()
                onJsonlReady(jsonl)
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
            // Audit fix P1-C: the whole wipe commits atomically via the store when
            // available; the legacy multi-repository path remains as a fallback for
            // construction sites that don't pass the store.
            val store = dataResetStore
            if (store != null) {
                store.clearAllData()
            } else {
                transactionRepository.deleteAll()
                trainingDataRepository.clearTrainingData()
                learnedTokenRepository?.clear()
                val accounts = accountRepository.getAll().first()
                for (acc in accounts) {
                    accountRepository.updateBalance(acc.id, 0.0)
                }
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

    fun addIgnoredPackage(packageName: String) {
        val clean = packageName.trim()
        if (clean.isBlank()) return
        viewModelScope.launch {
            settingsRepository.addIgnoredPackage(clean)
            _messageEffect.emit("Added $clean to Ignore List")
        }
    }

    fun removeIgnoredPackage(packageName: String) {
        val clean = packageName.trim()
        if (clean.isBlank()) return
        viewModelScope.launch {
            settingsRepository.removeIgnoredPackage(clean)
            _messageEffect.emit("Removed $clean from Ignore List")
        }
    }

    fun setShowHealthInTopBar(show: Boolean) {
        viewModelScope.launch {
            settingsRepository.setShowHealthInTopBar(show)
        }
    }

    fun setDailyWrapUpTime(hour: Int, minute: Int) {
        viewModelScope.launch {
            settingsRepository.setDailyWrapUpTime(hour, minute)
        }
    }

    fun refreshDiagnostics() {
        val lastSync = _uiState.value.lastSyncTimestamp
        val diagnostics = diagnosticsProvider?.checkDiagnostics(lastSync)
        if (diagnostics != null) {
            _uiState.value = _uiState.value.copy(
                isNotificationAccessGranted = diagnostics.isNotificationAccessGranted
            )
        }
    }

    fun openNotificationListenerSettings() {
        diagnosticsProvider?.openNotificationListenerSettings()
    }

    fun importCsvData(csvContent: String) {
        val importer = importDataUseCase ?: return
        viewModelScope.launch(com.cashbuddy.platform.ioDispatcher) {
            _uiState.value = _uiState.value.copy(isImportingCsv = true)
            try {
                val result = importer.importCsv(csvContent)
                _messageEffect.emit("Imported ${result.successCount} transactions (${result.failedCount} failed)")
            } catch (e: Exception) {
                _messageEffect.emit("Import error: ${e.message}")
            } finally {
                _uiState.value = _uiState.value.copy(isImportingCsv = false)
            }
        }
    }
}
