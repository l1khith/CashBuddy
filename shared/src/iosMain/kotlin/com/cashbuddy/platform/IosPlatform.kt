package com.cashbuddy.platform

import platform.Foundation.NSDate
import platform.Foundation.timeIntervalSince1970

actual fun currentTimeMillis(): Long = (NSDate().timeIntervalSince1970 * 1000).toLong()

actual fun getAppVersionDisplay(): String = "CashBuddy iOS"

actual val ioDispatcher: kotlinx.coroutines.CoroutineDispatcher = kotlinx.coroutines.Dispatchers.Default

actual fun notifyTransactionsUpdated() {
    // No-op on iOS
}
