package com.cashbuddy.platform

import android.content.Context
import android.content.Intent

private var androidVersionName: String = "1.0"
private var androidVersionCode: Int = 1
private var appContext: Context? = null

fun setAppContext(context: Context) {
    appContext = context.applicationContext
}

fun getAppContext(): Context? = appContext

fun setAppVersionInfo(versionName: String, versionCode: Int) {
    androidVersionName = versionName
    androidVersionCode = versionCode
}

actual fun currentTimeMillis(): Long = System.currentTimeMillis()

actual fun getAppVersionDisplay(): String = "CashBuddy v$androidVersionName (Build $androidVersionCode)"

actual fun getApplicationLabel(packageName: String): String {
    val ctx = appContext ?: return packageName.substringAfterLast('.').replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    return try {
        val pm = ctx.packageManager
        val appInfo = pm.getApplicationInfo(packageName, 0)
        pm.getApplicationLabel(appInfo).toString()
    } catch (_: Throwable) {
        packageName.substringAfterLast('.').replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }
}

actual val ioDispatcher: kotlinx.coroutines.CoroutineDispatcher = kotlinx.coroutines.Dispatchers.IO

actual fun notifyTransactionsUpdated() {
    val ctx = appContext ?: return
    try {
        val intent = Intent("com.cashbuddy.ACTION_TRANSACTIONS_UPDATED").setPackage(ctx.packageName)
        ctx.sendBroadcast(intent)
    } catch (_: Throwable) {
        // Safe fallback if context or broadcast receiver unavailable
    }
}
