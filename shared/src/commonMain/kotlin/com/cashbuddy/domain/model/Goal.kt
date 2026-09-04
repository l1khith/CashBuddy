package com.cashbuddy.domain.model

enum class GoalStatus {
    ACTIVE,
    COMPLETED,
    CANCELLED
}

data class Goal(
    val id: Long = 0,
    val name: String,
    val targetAmount: Double,
    val currentAmount: Double = 0.0,
    val deadline: Long? = null,
    val categoryId: Long? = null,
    val icon: String = "flag",
    val color: String = "#2ECC71",
    val status: GoalStatus = GoalStatus.ACTIVE,
    val createdAt: Long = 0,
    val updatedAt: Long = 0
)
