// NO-NETWORK
package com.cashbuddy.core.prob

data class RawMessage(
    val id: String,
    val sourceType: SourceType,
    val packageName: String?,
    val senderId: String?,
    val title: String,
    val text: String,
    val timestamp: Long,
    val imagePath: String? = null
)

enum class SourceType {
    SMS,
    NOTIFICATION,
    SCREENSHOT
}

enum class NotificationSource {
    BANK,
    UPI_APP,
    MERCHANT_APP,
    UNKNOWN
}
