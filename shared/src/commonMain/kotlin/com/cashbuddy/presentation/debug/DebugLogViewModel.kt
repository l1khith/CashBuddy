// NO-NETWORK
package com.cashbuddy.presentation.debug

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cashbuddy.debug.DebugConfig
import com.cashbuddy.domain.model.DebugLogEntry
import com.cashbuddy.domain.model.DebugLogFilter
import com.cashbuddy.domain.repository.DebugLogPager
import com.cashbuddy.domain.repository.DebugLogRepository
import com.cashbuddy.platform.FileExporter
import com.cashbuddy.platform.currentTimeMillis
import com.cashbuddy.platform.ioDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
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
    val isLoadingMore: Boolean = false,
    val isEndReached: Boolean = false,
    val expandedLogIds: Set<String> = emptySet()
)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class DebugLogViewModel(
    private val repository: DebugLogRepository,
    val debugConfig: DebugConfig,
    private val fileExporter: FileExporter? = null
) : ViewModel() {

    private val pager = DebugLogPager(repository, pageSize = 50L)

    private val _uiState = MutableStateFlow(DebugLogUiState(isLoading = true))
    val uiState: StateFlow<DebugLogUiState> = _uiState.asStateFlow()

    private val _messageEffect = MutableSharedFlow<String>()
    val messageEffect: SharedFlow<String> = _messageEffect.asSharedFlow()

    private val _packageQuery = MutableStateFlow("")
    private val _actionFilter = MutableStateFlow(ActionFilter.ALL)
    private val _timeRange = MutableStateFlow(TimeRange.ALL)

    private var timerJob: Job? = null

    private data class FilterCriteria(
        val query: String,
        val action: ActionFilter,
        val range: TimeRange
    )

    private val filterCriteriaFlow = combine(
        _packageQuery.debounce(300),
        _actionFilter,
        _timeRange
    ) { query, action, range ->
        FilterCriteria(query, action, range)
    }.distinctUntilChanged()

    init {
        startRecordingTimer()
        viewModelScope.launch(ioDispatcher) {
            filterCriteriaFlow.collectLatest { criteria ->
                applyFilterAndReload(criteria)
            }
        }
    }

    private fun buildFilter(
        action: ActionFilter,
        query: String,
        range: TimeRange
    ): DebugLogFilter {
        val now = currentTimeMillis()
        val startTime = range.durationMs?.let { now - it }
        val pkg = query.trim().ifEmpty { null }

        val policyAction = when (action) {
            ActionFilter.AUTO_LOG -> "AUTO_LOG"
            ActionFilter.LOG_AND_FLAG -> "LOG_AND_FLAG"
            ActionFilter.ASK_USER -> "ASK_USER"
            ActionFilter.IGNORED -> "IGNORE"
            else -> null
        }

        val outcome = when (action) {
            ActionFilter.LOGGED -> "LOGGED"
            ActionFilter.ERRORS -> "ERROR"
            else -> null
        }

        return DebugLogFilter(
            policyAction = policyAction,
            outcome = outcome,
            packageName = pkg,
            startTime = startTime
        )
    }

    private suspend fun applyFilterAndReload(criteria: FilterCriteria) {
        _uiState.update { it.copy(isLoading = true) }
        pager.reset()
        val filter = buildFilter(criteria.action, criteria.query, criteria.range)
        try {
            val count = repository.count()
            val initial = pager.loadNextPage(filter = filter, limit = 50L)
            _uiState.update {
                it.copy(
                    entries = initial,
                    filteredEntries = initial,
                    totalCount = count,
                    isLoading = false,
                    isEndReached = pager.isEndReached
                )
            }
        } catch (e: Throwable) {
            _uiState.update { it.copy(isLoading = false) }
            _messageEffect.emit("Failed to load logs: ${e.message}")
        }
    }

    fun loadLogs() {
        viewModelScope.launch(ioDispatcher) {
            applyFilterAndReload(FilterCriteria(_packageQuery.value, _actionFilter.value, _timeRange.value))
        }
    }

    fun loadNextPage() {
        val state = _uiState.value
        if (state.isLoading || state.isLoadingMore || state.isEndReached) return

        val filter = buildFilter(state.actionFilter, state.packageQuery, state.timeRange)
        viewModelScope.launch(ioDispatcher) {
            _uiState.update { it.copy(isLoadingMore = true) }
            try {
                val next = pager.loadNextPage(filter = filter, limit = 50L)
                _uiState.update { current ->
                    val combined = current.entries + next
                    current.copy(
                        entries = combined,
                        filteredEntries = combined,
                        isLoadingMore = false,
                        isEndReached = pager.isEndReached
                    )
                }
            } catch (e: Throwable) {
                _uiState.update { it.copy(isLoadingMore = false) }
                _messageEffect.emit("Failed to load more logs: ${e.message}")
            }
        }
    }

    fun setActionFilter(filter: ActionFilter) {
        _actionFilter.value = filter
        _uiState.update { it.copy(actionFilter = filter) }
    }

    fun setPackageQuery(query: String) {
        _packageQuery.value = query
        _uiState.update { it.copy(packageQuery = query) }
    }

    fun setTimeRange(range: TimeRange) {
        _timeRange.value = range
        _uiState.update { it.copy(timeRange = range) }
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
        viewModelScope.launch(ioDispatcher) {
            try {
                repository.clearAll()
                pager.reset()
                _uiState.update {
                    it.copy(
                        entries = emptyList(),
                        filteredEntries = emptyList(),
                        totalCount = 0L,
                        isEndReached = true,
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
        viewModelScope.launch(ioDispatcher) {
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
