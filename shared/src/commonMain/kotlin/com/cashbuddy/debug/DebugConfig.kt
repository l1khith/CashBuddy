// NO-NETWORK
package com.cashbuddy.debug

import com.cashbuddy.domain.repository.SettingsRepository
import com.cashbuddy.platform.currentTimeMillis
import kotlinx.coroutines.flow.firstOrNull

class DebugConfig(
    var isDebugBuild: Boolean = false,
    private val settingsRepository: SettingsRepository? = null
) {
    var developerModeOverride: Boolean? = null
    var cachedDeveloperMode: Boolean = false
    private var recordingActiveUntil: Long = 0L

    val enabled: Boolean
        get() = isDebugBuild || (developerModeOverride ?: cachedDeveloperMode)

    suspend fun isEnabled(): Boolean {
        if (isDebugBuild) return true
        developerModeOverride?.let { return it }
        val persisted = settingsRepository?.getDeveloperMode()?.firstOrNull() ?: false
        cachedDeveloperMode = persisted
        return persisted
    }

    suspend fun updateDeveloperMode(enabled: Boolean) {
        cachedDeveloperMode = enabled
        developerModeOverride = enabled
        settingsRepository?.setDeveloperMode(enabled)
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
