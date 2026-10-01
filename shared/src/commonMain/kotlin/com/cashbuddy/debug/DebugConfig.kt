// NO-NETWORK
package com.cashbuddy.debug

import com.cashbuddy.domain.repository.SettingsRepository
import com.cashbuddy.platform.currentTimeMillis
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

class DebugConfig(
    var isDebugBuild: Boolean = false,
    private val settingsRepository: SettingsRepository? = null,
    scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
) {
    var developerModeOverride: Boolean? = null
    var cachedDeveloperMode: Boolean? = null
    private var recordingActiveUntil: Long = 0L

    init {
        settingsRepository?.let { repo ->
            scope.launch {
                repo.getDebugLogEnabled().collect { persisted ->
                    cachedDeveloperMode = persisted
                }
            }
        }
    }

    val enabled: Boolean
        get() = developerModeOverride ?: cachedDeveloperMode ?: isDebugBuild

    suspend fun isEnabled(): Boolean {
        developerModeOverride?.let { return it }
        cachedDeveloperMode?.let { return it }
        val persisted = settingsRepository?.getDebugLogEnabled()?.firstOrNull()
        if (persisted != null) {
            cachedDeveloperMode = persisted
            return persisted
        }
        return isDebugBuild
    }

    suspend fun updateDeveloperMode(enabled: Boolean) {
        cachedDeveloperMode = enabled
        developerModeOverride = enabled
        settingsRepository?.setDebugLogEnabled(enabled)
    }

    fun isRecordingActive(now: Long = currentTimeMillis()): Boolean {
        return recordingActiveUntil > now
    }

    fun startRecording(durationMs: Long = 15 * 60 * 1000L, now: Long = currentTimeMillis()) {
        recordingActiveUntil = now + durationMs
    }

    fun stopRecording() {
        recordingActiveUntil = 0L
    }

    fun getRecordingRemainingSeconds(now: Long = currentTimeMillis()): Long {
        val remaining = recordingActiveUntil - now
        return if (remaining > 0) remaining / 1000L else 0L
    }
}
