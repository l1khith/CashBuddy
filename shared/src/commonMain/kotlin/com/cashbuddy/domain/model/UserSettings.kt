package com.cashbuddy.domain.model

data class UserSettings(
    val autoConfirmThreshold: Double = 10000.0,
    val minConfidenceThreshold: Float = 0.85f,
    val notificationsEnabled: Boolean = true,
    val biometricEnabled: Boolean = false,
    val preferredCurrency: String = "INR",
    val smartNotificationsEnabled: Boolean = true,
    val customAppName: String = DEFAULT_APP_NAME,
    val customAppSubtitle: String = DEFAULT_APP_SUBTITLE,
    val showAppLogo: Boolean = true,
    val navOrder: String = DEFAULT_NAV_ORDER
) {
    companion object {
        const val DEFAULT_APP_NAME = "CashBuddy"
        const val DEFAULT_APP_SUBTITLE = "Personal Finance"
        const val DEFAULT_NAV_ORDER = "home,stats,history"
        const val MAX_APP_NAME_LENGTH = 15
        const val MAX_APP_SUBTITLE_LENGTH = 20
    }
}
