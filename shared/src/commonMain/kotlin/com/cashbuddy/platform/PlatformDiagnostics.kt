package com.cashbuddy.platform

import com.cashbuddy.domain.model.SystemDiagnostics

expect class PlatformDiagnosticsProvider() {
    fun checkDiagnostics(lastSyncTimestamp: Long): SystemDiagnostics
    fun openNotificationListenerSettings()
    fun openBatteryOptimizationSettings()
    fun scheduleDailyWrapUp(hour: Int, minute: Int)
}
