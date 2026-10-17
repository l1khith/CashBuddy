package com.cashbuddy.presentation.personalization

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cashbuddy.domain.model.RawTrainingData
import com.cashbuddy.domain.model.UserCorrection
import com.cashbuddy.domain.repository.MerchantRuleRepository
import com.cashbuddy.domain.repository.RawTrainingDataPager
import com.cashbuddy.domain.repository.TrainingDataRepository
import com.cashbuddy.domain.repository.TransactionRepository
import com.cashbuddy.domain.repository.UserCorrectionPager
import com.cashbuddy.domain.usecase.BatchCategorizeUseCase
import com.cashbuddy.platform.FileExporter
import com.cashbuddy.platform.currentTimeMillis
import com.cashbuddy.platform.ioDispatcher
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
data class PersonalizationUiState(
    val accuracyRate: Int = 96,
    val correctionsCount: Long = 0,
    val rawSamplesCount: Long = 0,
    val learnedMerchantsCount: Long = 0,
    val recentCorrections: List<UserCorrection> = emptyList(),
    val rawNotifications: List<RawTrainingData> = emptyList(),
    val isTraining: Boolean = false,
    val isLoading: Boolean = true,
    val isLoadingMoreCorrections: Boolean = false,
    val isLoadingMoreRaw: Boolean = false,
    val isEndReachedCorrections: Boolean = false,
    val isEndReachedRaw: Boolean = false
)

class PersonalizationViewModel(
    private val trainingDataRepository: TrainingDataRepository,
    private val merchantRuleRepository: MerchantRuleRepository,
    private val transactionRepository: TransactionRepository,
    private val batchCategorizeUseCase: BatchCategorizeUseCase,
    private val fileExporter: FileExporter? = null
) : ViewModel() {

    private val rawPager = RawTrainingDataPager(trainingDataRepository, pageSize = 50L)
    private val correctionsPager = UserCorrectionPager(trainingDataRepository, pageSize = 50L)

    private val _uiState = MutableStateFlow(PersonalizationUiState())
    val uiState: StateFlow<PersonalizationUiState> = _uiState.asStateFlow()

    private val _messageEffect = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val messageEffect: SharedFlow<String> = _messageEffect.asSharedFlow()

    init {
        loadData()
    }

    fun loadData() {
        viewModelScope.launch(ioDispatcher) {
            _uiState.update { it.copy(isLoading = true) }
            rawPager.reset()
            correctionsPager.reset()

            combine(
                trainingDataRepository.getStats(),
                transactionRepository.getCount()
            ) { stats, txCount ->
                val total = txCount.toInt()
                val numCorrections = stats.correctionsCount.toInt()
                val accuracy = if (total > 0) {
                    val acc = ((total - numCorrections).toDouble() / total * 100).toInt()
                    acc.coerceIn(75, 100)
                } else {
                    96
                }

                val rulesCount = merchantRuleRepository.getCount()
                val initialCorrections = correctionsPager.loadNextPage(limit = 20L)
                val initialRaw = rawPager.loadNextPage(limit = 50L)

                PersonalizationUiState(
                    accuracyRate = accuracy,
                    correctionsCount = stats.correctionsCount,
                    rawSamplesCount = stats.rawCount,
                    learnedMerchantsCount = rulesCount,
                    recentCorrections = initialCorrections,
                    rawNotifications = initialRaw,
                    isLoading = false,
                    isEndReachedCorrections = correctionsPager.isEndReached,
                    isEndReachedRaw = rawPager.isEndReached
                )
            }.collect { newState ->
                _uiState.value = newState
            }
        }
    }

    fun loadMoreCorrections() {
        val state = _uiState.value
        if (state.isLoading || state.isLoadingMoreCorrections || state.isEndReachedCorrections) return
        viewModelScope.launch(ioDispatcher) {
            _uiState.update { it.copy(isLoadingMoreCorrections = true) }
            try {
                val next = correctionsPager.loadNextPage()
                _uiState.update { current ->
                    current.copy(
                        recentCorrections = current.recentCorrections + next,
                        isLoadingMoreCorrections = false,
                        isEndReachedCorrections = correctionsPager.isEndReached
                    )
                }
            } catch (e: Throwable) {
                _uiState.update { it.copy(isLoadingMoreCorrections = false) }
            }
        }
    }

    fun loadMoreRaw() {
        val state = _uiState.value
        if (state.isLoading || state.isLoadingMoreRaw || state.isEndReachedRaw) return
        viewModelScope.launch(ioDispatcher) {
            _uiState.update { it.copy(isLoadingMoreRaw = true) }
            try {
                val next = rawPager.loadNextPage()
                _uiState.update { current ->
                    current.copy(
                        rawNotifications = current.rawNotifications + next,
                        isLoadingMoreRaw = false,
                        isEndReachedRaw = rawPager.isEndReached
                    )
                }
            } catch (e: Throwable) {
                _uiState.update { it.copy(isLoadingMoreRaw = false) }
            }
        }
    }

    fun trainNow() {
        viewModelScope.launch(ioDispatcher) {
            _uiState.update { it.copy(isTraining = true) }
            try {
                val result = batchCategorizeUseCase()
                _messageEffect.emit(
                    "Re-evaluated ${result.reEvaluatedCount} items: ${result.newlyCategorizedCount} categorized (${result.autoConfirmedCount} auto-confirmed)"
                )
            } catch (e: Throwable) {
                _messageEffect.emit("Training batch failed: ${e.message}")
            } finally {
                _uiState.update { it.copy(isTraining = false) }
            }
        }
    }

    fun exportDataset() {
        viewModelScope.launch(ioDispatcher) {
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
                        _messageEffect.emit("Export failed: ${err.message}")
                    }
                )
            } else {
                _messageEffect.emit("Dataset ready: ${_uiState.value.rawSamplesCount} raw alerts, ${_uiState.value.correctionsCount} corrections")
            }
        }
    }

    fun resetLearningData() {
        viewModelScope.launch(ioDispatcher) {
            trainingDataRepository.clearTrainingData()
            val rules = merchantRuleRepository.getAll().firstOrNull() ?: emptyList()
            rules.forEach {
                merchantRuleRepository.deleteById(it.id)
            }
            rawPager.reset()
            correctionsPager.reset()
            loadData()
            _messageEffect.emit("Reset complete: training samples, corrections & learned rules wiped")
        }
    }
}
