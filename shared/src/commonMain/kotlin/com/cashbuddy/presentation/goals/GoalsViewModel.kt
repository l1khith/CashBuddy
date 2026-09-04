package com.cashbuddy.presentation.goals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cashbuddy.domain.model.Goal
import com.cashbuddy.domain.model.GoalStatus
import com.cashbuddy.domain.repository.GoalRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class GoalsUiState(
    val goals: List<Goal> = emptyList(),
    val totalSaved: Double = 0.0,
    val isLoading: Boolean = true
)

class GoalsViewModel(
    private val goalRepository: GoalRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(GoalsUiState())
    val uiState: StateFlow<GoalsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            goalRepository.getAll().collect { goalsList ->
                _uiState.value = GoalsUiState(
                    goals = goalsList,
                    totalSaved = goalsList.sumOf { it.currentAmount },
                    isLoading = false
                )
            }
        }
    }

    fun addGoal(name: String, targetAmount: Double) {
        viewModelScope.launch {
            val now = com.cashbuddy.platform.currentTimeMillis()
            val goal = Goal(
                id = 0L,
                name = name,
                targetAmount = targetAmount,
                currentAmount = 0.0,
                deadline = now + (90L * 24 * 60 * 60 * 1000), // 3 months default
                color = "#26A69A",
                icon = "savings",
                status = GoalStatus.ACTIVE,
                createdAt = now,
                updatedAt = now
            )
            goalRepository.insert(goal)
        }
    }

    fun contribute(goalId: Long, additionalAmount: Double) {
        viewModelScope.launch {
            val goal = _uiState.value.goals.find { it.id == goalId } ?: return@launch
            val newAmount = goal.currentAmount + additionalAmount
            goalRepository.updateProgress(goalId, newAmount)
            if (newAmount >= goal.targetAmount) {
                goalRepository.updateStatus(goalId, GoalStatus.COMPLETED)
            }
        }
    }
}
