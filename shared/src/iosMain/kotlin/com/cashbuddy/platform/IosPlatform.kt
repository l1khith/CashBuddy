package com.cashbuddy.platform

import platform.Foundation.NSDate
import platform.Foundation.timeIntervalSince1970

actual fun currentTimeMillis(): Long = (NSDate().timeIntervalSince1970 * 1000).toLong()

actual fun getAppVersionDisplay(): String = "CashBuddy iOS"

actual fun getApplicationLabel(packageName: String): String =
    packageName.substringAfterLast('.').replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }

actual val ioDispatcher: kotlinx.coroutines.CoroutineDispatcher = kotlinx.coroutines.Dispatchers.Default

actual fun notifyTransactionsUpdated() {
    // No-op on iOS
}
