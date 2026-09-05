package com.cashbuddy.presentation.personalization

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cashbuddy.domain.model.RawTrainingData
import com.cashbuddy.domain.model.UserCorrection
import com.cashbuddy.domain.repository.MerchantRuleRepository
import com.cashbuddy.domain.repository.TrainingDataRepository
import com.cashbuddy.domain.repository.TransactionRepository
import com.cashbuddy.domain.usecase.BatchCategorizeUseCase
import com.cashbuddy.platform.FileExporter
import com.cashbuddy.platform.currentTimeMillis
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

data class PersonalizationUiState(
    val accuracyRate: Int = 96,
    val correctionsCount: Long = 0,
    val rawSamplesCount: Long = 0,
    val learnedMerchantsCount: Long = 0,
    val recentCorrections: List<UserCorrection> = emptyList(),
    val rawNotifications: List<RawTrainingData> = emptyList(),
    val isTraining: Boolean = false,
    val isLoading: Boolean = true
)

class PersonalizationViewModel(
    private val trainingDataRepository: TrainingDataRepository,
    private val merchantRuleRepository: MerchantRuleRepository,
    private val transactionRepository: TransactionRepository,
    private val batchCategorizeUseCase: BatchCategorizeUseCase,
    private val fileExporter: FileExporter? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(PersonalizationUiState())
    val uiState: StateFlow<PersonalizationUiState> = _uiState.asStateFlow()

    private val _messageEffect = MutableSharedFlow<String>()
    val messageEffect: SharedFlow<String> = _messageEffect.asSharedFlow()

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            combine(
                trainingDataRepository.getStats(),
                trainingDataRepository.getAllCorrections(),
                trainingDataRepository.getAllRaw(),
                merchantRuleRepository.getAll(),
                transactionRepository.getAll()
            ) { stats, corrections, rawSamples, rules, txs ->
                // Calculate empirical accuracy
                val total = txs.size
                val numCorrections = corrections.size
                val accuracy = if (total > 0) {
                    val acc = ((total - numCorrections).toDouble() / total * 100).toInt()
                    acc.coerceIn(75, 100)
                } else {
                    96
                }

                PersonalizationUiState(
                    accuracyRate = accuracy,
                    correctionsCount = stats.correctionsCount,
                    rawSamplesCount = stats.rawCount,
                    learnedMerchantsCount = rules.size.toLong(),
                    recentCorrections = corrections.take(20),
                    rawNotifications = rawSamples.take(50),
                    isLoading = false
                )
            }.collect {
                _uiState.value = it
            }
        }
    }

    fun trainNow() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isTraining = true)
            try {
                val result = batchCategorizeUseCase()
                _messageEffect.emit(
                    "Re-evaluated ${result.reEvaluatedCount} items: ${result.newlyCategorizedCount} categorized (${result.autoConfirmedCount} auto-confirmed)"
                )
            } catch (e: Throwable) {
                _messageEffect.emit("Training batch failed: ${e.message}")
            } finally {
                _uiState.value = _uiState.value.copy(isTraining = false)
            }
        }
    }

    fun exportDataset() {
        viewModelScope.launch {
            val jsonl = trainingDataRepository.exportTrainingDataJsonl()
            if (fileExporter != null) {
                val fileName = "cashbuddy_training_${currentTimeMillis()}.jsonl"
                val result = fileExporter.exportCsvFile(fileName, jsonl)
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
        viewModelScope.launch {
            trainingDataRepository.clearTrainingData()
            val rules = merchantRuleRepository.getAll().firstOrNull() ?: emptyList()
            rules.forEach {
                merchantRuleRepository.deleteById(it.id)
            }
            _messageEffect.emit("Reset complete: training samples, corrections & learned rules wiped")
        }
    }
}
