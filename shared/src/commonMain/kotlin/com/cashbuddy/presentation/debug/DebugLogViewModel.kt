// NO-NETWORK
package com.cashbuddy.presentation.debug

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cashbuddy.debug.DebugConfig
import com.cashbuddy.domain.model.DebugLogEntry
import com.cashbuddy.domain.repository.DebugLogRepository
import com.cashbuddy.platform.FileExporter
import com.cashbuddy.platform.currentTimeMillis
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ActionFilter(val label: String) {
    ALL("All"),
    IGNORED("Ignored"),
    LOGGED("Logged"),
    ERRORS("Errors"),
    AUTO_LOG("Auto-Log"),
    LOG_AND_FLAG("Flagged"),
    ASK_USER("Ask User")
}

enum class TimeRange(val label: String, val durationMs: Long?) {
    ALL("All Time", null),
    LAST_HOUR("Last Hour", 3600_000L),
    TODAY("Today", 86400_000L),
    LAST_7_DAYS("Last 7 Days", 7 * 86400_000L)
}

data class DebugLogUiState(
    val entries: List<DebugLogEntry> = emptyList(),
    val filteredEntries: List<DebugLogEntry> = emptyList(),
    val actionFilter: ActionFilter = ActionFilter.ALL,
    val packageQuery: String = "",
    val timeRange: TimeRange = TimeRange.ALL,
    val isRecordingActive: Boolean = false,
    val recordingRemainingSeconds: Long = 0L,
    val totalCount: Long = 0L,
    val isLoading: Boolean = false,
    val expandedLogIds: Set<String> = emptySet()
)

class DebugLogViewModel(
    private val repository: DebugLogRepository,
    val debugConfig: DebugConfig,
    private val fileExporter: FileExporter? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(DebugLogUiState())
    val uiState: StateFlow<DebugLogUiState> = _uiState.asStateFlow()

    private val _messageEffect = MutableSharedFlow<String>()
    val messageEffect: SharedFlow<String> = _messageEffect.asSharedFlow()

    private var timerJob: Job? = null

    init {
        loadLogs()
        startRecordingTimer()
    }

    fun loadLogs() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val all = repository.recent(500)
                val count = repository.count()
                _uiState.update { current ->
                    current.copy(
                        entries = all,
                        totalCount = count,
                        isLoading = false
                    )
                }
                applyFilters()
            } catch (e: Throwable) {
                _uiState.update { it.copy(isLoading = false) }
                _messageEffect.emit("Failed to load logs: ${e.message}")
            }
        }
    }

    fun setActionFilter(filter: ActionFilter) {
        _uiState.update { it.copy(actionFilter = filter) }
        applyFilters()
    }

    fun setPackageQuery(query: String) {
        _uiState.update { it.copy(packageQuery = query) }
        applyFilters()
    }

    fun setTimeRange(range: TimeRange) {
        _uiState.update { it.copy(timeRange = range) }
        applyFilters()
    }

    fun toggleExpand(id: String) {
        _uiState.update { current ->
            val set = current.expandedLogIds.toMutableSet()
            if (set.contains(id)) set.remove(id) else set.add(id)
            current.copy(expandedLogIds = set)
        }
    }

    fun toggleRecording() {
        if (debugConfig.isRecordingActive()) {
            debugConfig.stopRecording()
            _uiState.update {
                it.copy(
                    isRecordingActive = false,
                    recordingRemainingSeconds = 0L
                )
            }
            viewModelScope.launch { _messageEffect.emit("Recording stopped") }
        } else {
            debugConfig.startRecording()
            _uiState.update {
                it.copy(
                    isRecordingActive = true,
                    recordingRemainingSeconds = debugConfig.getRecordingRemainingSeconds()
                )
            }
            viewModelScope.launch { _messageEffect.emit("Recording started (15 min auto-expiry)") }
            startRecordingTimer()
        }
    }

    fun clearLogs() {
        viewModelScope.launch {
            try {
                repository.clearAll()
                _uiState.update {
                    it.copy(
                        entries = emptyList(),
                        filteredEntries = emptyList(),
                        totalCount = 0L,
                        expandedLogIds = emptySet()
                    )
                }
                _messageEffect.emit("All debug logs deleted")
            } catch (e: Throwable) {
                _messageEffect.emit("Failed to clear logs: ${e.message}")
            }
        }
    }

    fun exportCsv(onCsvReady: (String) -> Unit = {}) {
        viewModelScope.launch {
            val csv = generateCsv(_uiState.value.filteredEntries)
            onCsvReady(csv)
            if (fileExporter != null) {
                val fileName = "cashbuddy_debug_logs_${currentTimeMillis()}.csv"
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
                _messageEffect.emit("CSV exported (${_uiState.value.filteredEntries.size} rows)")
            }
        }
    }

    fun getCopyAllText(): String {
        return generateCsv(_uiState.value.filteredEntries)
    }

    private fun applyFilters() {
        val current = _uiState.value
        val now = currentTimeMillis()
        val filtered = current.entries.filter { entry ->
            // Action filter
            val matchesAction = when (current.actionFilter) {
                ActionFilter.ALL -> true
                ActionFilter.IGNORED -> entry.policyAction.equals("IGNORE", ignoreCase = true) ||
                                       entry.pipelineOutcome.equals("IGNORED", ignoreCase = true)
                ActionFilter.LOGGED -> entry.pipelineOutcome.equals("LOGGED", ignoreCase = true) ||
                                      entry.pipelineOutcome.equals("CONFIRMED", ignoreCase = true)
                ActionFilter.ERRORS -> !entry.errorMessage.isNullOrBlank() ||
                                      entry.pipelineOutcome.equals("ERROR", ignoreCase = true)
                ActionFilter.AUTO_LOG -> entry.policyAction.equals("AUTO_LOG", ignoreCase = true)
                ActionFilter.LOG_AND_FLAG -> entry.policyAction.equals("LOG_AND_FLAG", ignoreCase = true)
                ActionFilter.ASK_USER -> entry.policyAction.equals("ASK_USER", ignoreCase = true)
            }

            // Package query filter
            val matchesPackage = if (current.packageQuery.isBlank()) {
                true
            } else {
                val q = current.packageQuery.lowercase()
                (entry.packageName?.lowercase()?.contains(q) == true) ||
                (entry.senderId?.lowercase()?.contains(q) == true) ||
                (entry.rawText.lowercase().contains(q))
            }

            // Time range filter
            val matchesTime = if (current.timeRange.durationMs == null) {
                true
            } else {
                entry.timestamp >= (now - current.timeRange.durationMs)
            }

            matchesAction && matchesPackage && matchesTime
        }

        _uiState.update { it.copy(filteredEntries = filtered) }
    }

    private fun startRecordingTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (true) {
                val isActive = debugConfig.isRecordingActive()
                val remaining = debugConfig.getRecordingRemainingSeconds()
                _uiState.update {
                    it.copy(
                        isRecordingActive = isActive,
                        recordingRemainingSeconds = remaining
                    )
                }
                if (!isActive) break
                delay(1000L)
            }
        }
    }

    companion object {
        fun generateCsv(entries: List<DebugLogEntry>): String {
            val sb = StringBuilder()
            sb.append("timestamp,source_type,package_name,policy_action,p_transaction,raw_text,outcome\n")
            for (e in entries) {
                val escapedText = "\"" + e.rawText.replace("\"", "\"\"").replace("\n", " ").replace("\r", "") + "\""
                val pkg = "\"" + (e.packageName ?: e.senderId ?: "") + "\""
                val policy = e.policyAction ?: ""
                val p = e.pTransaction?.let { ((it * 10000).toLong() / 10000.0).toString() } ?: ""
                val outcome = e.pipelineOutcome ?: ""
                sb.append("${e.timestamp},${e.sourceType},$pkg,$policy,$p,$escapedText,$outcome\n")
            }
            return sb.toString()
        }
    }
}
