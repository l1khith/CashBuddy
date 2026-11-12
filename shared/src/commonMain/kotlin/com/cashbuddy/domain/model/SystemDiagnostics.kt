package com.cashbuddy.domain.model

data class SystemDiagnostics(
    val isNotificationAccessGranted: Boolean = false,
    val lastSyncTimestamp: Long = 0L
) {
    val isFullyHealthy: Boolean get() = isNotificationAccessGranted
}
