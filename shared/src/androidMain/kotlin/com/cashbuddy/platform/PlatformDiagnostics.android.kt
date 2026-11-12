package com.cashbuddy.platform

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import com.cashbuddy.domain.model.SystemDiagnostics

actual class PlatformDiagnosticsProvider actual constructor() {

    actual fun checkDiagnostics(lastSyncTimestamp: Long): SystemDiagnostics {
        val ctx = getAppContext() ?: return SystemDiagnostics(lastSyncTimestamp = lastSyncTimestamp)
        val isNotif = NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName)

        return SystemDiagnostics(
            isNotificationAccessGranted = isNotif,
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
