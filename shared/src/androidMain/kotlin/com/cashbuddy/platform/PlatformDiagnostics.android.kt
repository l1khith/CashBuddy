package com.cashbuddy.platform

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import com.cashbuddy.domain.model.SystemDiagnostics

actual class PlatformDiagnosticsProvider actual constructor() {

    actual fun checkDiagnostics(lastSyncTimestamp: Long): SystemDiagnostics {
        val ctx = getAppContext() ?: return SystemDiagnostics(lastSyncTimestamp = lastSyncTimestamp)
        val isNotif = NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName)
        val pm = ctx.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val isBattery = pm?.isIgnoringBatteryOptimizations(ctx.packageName) ?: false

        return SystemDiagnostics(
            isNotificationAccessGranted = isNotif,
            isBatteryOptimizationIgnored = isBattery,
            lastSyncTimestamp = lastSyncTimestamp
        )
    }

    actual fun openNotificationListenerSettings() {
        val ctx = getAppContext() ?: return
        try {
            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            ctx.startActivity(intent)
        } catch (e: Throwable) {
            android.util.Log.e("PlatformDiagnostics", "Failed to open notification settings", e)
        }
    }

    actual fun openBatteryOptimizationSettings() {
        val ctx = getAppContext() ?: return
        try {
            val directIntent = Intent(
                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                Uri.parse("package:${ctx.packageName}")
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            ctx.startActivity(directIntent)
        } catch (_: Throwable) {
            try {
                val fallbackIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                ctx.startActivity(fallbackIntent)
            } catch (fallbackEx: Throwable) {
                android.util.Log.e("PlatformDiagnostics", "Failed to open battery optimization settings", fallbackEx)
            }
        }
    }

    actual fun scheduleDailyWrapUp(hour: Int, minute: Int) {
        val ctx = getAppContext() ?: return
        try {
            val clazz = Class.forName("com.cashbuddy.worker.AssistantWorkManager")
            val method = clazz.getMethod("scheduleDailyWrapUp", Context::class.java, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
            val instance = clazz.getField("INSTANCE").get(null)
            method.invoke(instance, ctx, hour, minute)
        } catch (e: Throwable) {
            android.util.Log.e("PlatformDiagnostics", "Failed to invoke scheduleDailyWrapUp via reflection", e)
        }
    }
}
