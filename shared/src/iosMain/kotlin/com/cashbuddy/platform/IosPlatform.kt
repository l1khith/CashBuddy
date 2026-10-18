package com.cashbuddy.platform

import platform.Foundation.NSBundle
import platform.Foundation.NSDate
import platform.Foundation.timeIntervalSince1970

actual fun currentTimeMillis(): Long = (NSDate().timeIntervalSince1970 * 1000).toLong()

actual fun getAppVersionDisplay(): String {
    val version = NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String ?: "1.0"
    val build = NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleVersion") as? String ?: "1"
    return "CashBuddy v$version (Build $build)"
}

actual val ioDispatcher: kotlinx.coroutines.CoroutineDispatcher = kotlinx.coroutines.Dispatchers.Default
