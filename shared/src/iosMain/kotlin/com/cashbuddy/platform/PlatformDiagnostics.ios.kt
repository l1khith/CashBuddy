package com.cashbuddy.platform

import com.cashbuddy.domain.model.SystemDiagnostics

actual class PlatformDiagnosticsProvider actual constructor() {
    actual fun checkDiagnostics(lastSyncTimestamp: Long): SystemDiagnostics {
        return SystemDiagnostics(
            isNotificationAccessGranted = true,
            isBatteryOptimizationIgnored = true,
            lastSyncTimestamp = lastSyncTimestamp
        )
    }

    actual fun openNotificationListenerSettings() {
        // No-op on iOS
    }

    actual fun openBatteryOptimizationSettings() {
        // No-op on iOS
    }

    actual fun scheduleDailyWrapUp(hour: Int, minute: Int) {
        // No-op on iOS
    }
}
