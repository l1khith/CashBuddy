package com.cashbuddy.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.cashbuddy.db.AppDatabase
import com.cashbuddy.domain.model.UserSettings
import com.cashbuddy.domain.repository.SettingsRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class SettingsRepositoryImpl(
    private val db: AppDatabase,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) : SettingsRepository {

    private val queries = db.settingsQueries

    override fun getSettings(): Flow<UserSettings> =
        queries.getAll().asFlow().mapToList(dispatcher).map { list ->
            val map = list.associate { it.key to it.value_ }
            UserSettings(
                autoConfirmThreshold = map["autoConfirmThreshold"]?.toDoubleOrNull() ?: 10000.0,
                minConfidenceThreshold = map["minConfidenceThreshold"]?.toFloatOrNull() ?: 0.85f,
                notificationsEnabled = map["notificationsEnabled"]?.toBooleanStrictOrNull() ?: true,
                biometricEnabled = map["biometricEnabled"]?.toBooleanStrictOrNull() ?: false
            )
        }

    override suspend fun updateSettings(settings: UserSettings): Unit = withContext(dispatcher) {
        val now = com.cashbuddy.platform.currentTimeMillis()
        queries.set("autoConfirmThreshold", settings.autoConfirmThreshold.toString(), now)
        queries.set("minConfidenceThreshold", settings.minConfidenceThreshold.toString(), now)
        queries.set("notificationsEnabled", settings.notificationsEnabled.toString(), now)
        queries.set("biometricEnabled", settings.biometricEnabled.toString(), now)
    }

    override fun getNotificationEnabled(): Flow<Boolean> =
        queries.get("notificationsEnabled").asFlow().mapToOneOrNull(dispatcher).map { record ->
            record?.value_?.toBooleanStrictOrNull() ?: true
        }

    override suspend fun setNotificationEnabled(enabled: Boolean): Unit = withContext(dispatcher) {
        queries.set("notificationsEnabled", enabled.toString(), com.cashbuddy.platform.currentTimeMillis())
    }

    override fun getAutoConfirmThreshold(): Flow<Double> =
        queries.get("autoConfirmThreshold").asFlow().mapToOneOrNull(dispatcher).map { record ->
            record?.value_?.toDoubleOrNull() ?: 10000.0
        }

    override suspend fun setAutoConfirmThreshold(threshold: Double): Unit = withContext(dispatcher) {
        queries.set("autoConfirmThreshold", threshold.toString(), com.cashbuddy.platform.currentTimeMillis())
    }

    override fun getMinConfidenceThreshold(): Flow<Float> =
        queries.get("minConfidenceThreshold").asFlow().mapToOneOrNull(dispatcher).map { record ->
            record?.value_?.toFloatOrNull() ?: 0.85f
        }

    override suspend fun setMinConfidenceThreshold(confidence: Float): Unit = withContext(dispatcher) {
        queries.set("minConfidenceThreshold", confidence.toString(), com.cashbuddy.platform.currentTimeMillis())
    }

    override fun getBiometricEnabled(): Flow<Boolean> =
        queries.get("biometricEnabled").asFlow().mapToOneOrNull(dispatcher).map { record ->
            record?.value_?.toBooleanStrictOrNull() ?: false
        }

    override suspend fun setBiometricEnabled(enabled: Boolean): Unit = withContext(dispatcher) {
        queries.set("biometricEnabled", enabled.toString(), com.cashbuddy.platform.currentTimeMillis())
    }
}
