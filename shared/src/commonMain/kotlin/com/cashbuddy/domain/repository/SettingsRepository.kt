package com.cashbuddy.domain.repository

import com.cashbuddy.domain.model.UserSettings
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    fun getSettings(): Flow<UserSettings>
    suspend fun updateSettings(settings: UserSettings)
    fun getNotificationEnabled(): Flow<Boolean>
    suspend fun setNotificationEnabled(enabled: Boolean)
    fun getAutoConfirmThreshold(): Flow<Double>
    suspend fun setAutoConfirmThreshold(threshold: Double)
    fun getMinConfidenceThreshold(): Flow<Float>
    suspend fun setMinConfidenceThreshold(confidence: Float)
    fun getBiometricEnabled(): Flow<Boolean>
    suspend fun setBiometricEnabled(enabled: Boolean)
}
