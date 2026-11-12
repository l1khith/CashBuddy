package com.cashbuddy.platform

expect fun currentTimeMillis(): Long

expect fun getAppVersionDisplay(): String

expect fun getApplicationLabel(packageName: String): String

expect val ioDispatcher: kotlinx.coroutines.CoroutineDispatcher

expect fun notifyTransactionsUpdated()
