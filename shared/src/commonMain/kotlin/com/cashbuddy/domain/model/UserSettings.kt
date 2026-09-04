package com.cashbuddy.domain.model

data class UserSettings(
    val autoConfirmThreshold: Double = 10000.0,
    val minConfidenceThreshold: Float = 0.85f,
    val notificationsEnabled: Boolean = true,
    val biometricEnabled: Boolean = false
)
