package com.cashbuddy.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.cashbuddy.R

object NotificationHelper {

    const val CHANNEL_BUDGET_ALERTS = "budget_alerts"
    const val CHANNEL_ANOMALY_ALERTS = "anomaly_alerts"
    const val CHANNEL_WEEKLY_DIGEST = "weekly_digest"
    const val CHANNEL_QUICK_GLANCE = "quick_glance"
    const val CHANNEL_DAILY_WRAP_UP = "daily_wrap_up"

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

            val budgetChannel = NotificationChannel(
                CHANNEL_BUDGET_ALERTS,
                context.getString(R.string.channel_budget_alerts_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = context.getString(R.string.channel_budget_alerts_desc)
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            }

            val anomalyChannel = NotificationChannel(
                CHANNEL_ANOMALY_ALERTS,
                context.getString(R.string.channel_anomaly_alerts_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.channel_anomaly_alerts_desc)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 250, 150, 250)
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            }

            val weeklyChannel = NotificationChannel(
                CHANNEL_WEEKLY_DIGEST,
                context.getString(R.string.channel_weekly_digest_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = context.getString(R.string.channel_weekly_digest_desc)
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            }

            val quickGlanceChannel = NotificationChannel(
                CHANNEL_QUICK_GLANCE,
                context.getString(R.string.channel_quick_glance_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = context.getString(R.string.channel_quick_glance_desc)
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            }

            val wrapUpChannel = NotificationChannel(
                CHANNEL_DAILY_WRAP_UP,
                context.getString(R.string.channel_daily_wrap_up_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = context.getString(R.string.channel_daily_wrap_up_desc)
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            }

            notificationManager.createNotificationChannels(
                listOf(budgetChannel, anomalyChannel, weeklyChannel, quickGlanceChannel, wrapUpChannel)
            )
        }
    }
}
