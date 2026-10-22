package com.cashbuddy.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

object NotificationHelper {

    const val CHANNEL_BUDGET_ALERTS = "budget_alerts"
    const val CHANNEL_ANOMALY_ALERTS = "anomaly_alerts"
    const val CHANNEL_WEEKLY_DIGEST = "weekly_digest"
    const val CHANNEL_QUICK_GLANCE = "quick_glance"

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

            val budgetChannel = NotificationChannel(
                CHANNEL_BUDGET_ALERTS,
                "Budget Alerts",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Alerts when spending approaches or exceeds budget limits"
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            }

            val anomalyChannel = NotificationChannel(
                CHANNEL_ANOMALY_ALERTS,
                "Anomaly Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Real-time alerts for unusually high or irregular transactions"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 250, 150, 250)
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            }

            val weeklyChannel = NotificationChannel(
                CHANNEL_WEEKLY_DIGEST,
                "Weekly Digest",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Weekly recap of expenses and top spending categories"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            }

            val quickGlanceChannel = NotificationChannel(
                CHANNEL_QUICK_GLANCE,
                "Quick Glance",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Persistent daily and monthly spending status"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            }

            notificationManager.createNotificationChannels(
                listOf(budgetChannel, anomalyChannel, weeklyChannel, quickGlanceChannel)
            )
        }
    }
}
