// NO-NETWORK
package com.cashbuddy.notification

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.cashbuddy.MainActivity
import com.cashbuddy.core.budget.AlertEvent
import com.cashbuddy.core.budget.BudgetNotifier
import com.cashbuddy.domain.model.AlertState

/**
 * Android implementation of [BudgetNotifier] — posts budget alerts on the
 * dedicated budget-alerts channel. Bound in DI (CashBuddyApplication) so the
 * shared [com.cashbuddy.core.budget.BudgetAlertScheduler] can actually deliver
 * the alerts it computes (previously the scheduler ran notifier-less and was
 * silent; notifications were only posted by BudgetAlertWorker's duplicated logic).
 */
class AndroidBudgetNotifier(
    private val context: Context
) : BudgetNotifier {

    override fun sendAlert(event: AlertEvent) {
        try {
            // POST_NOTIFICATIONS is a runtime permission on API 33+; respect it silently.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                return
            }

            val notificationId = BUDGET_NOTIFICATION_ID_BASE +
                (event.budgetId.hashCode() % BUDGET_NOTIFICATION_ID_SPAN)

            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("EXTRA_NAVIGATE_TO", "budgets")
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                notificationId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val title = when (event.state) {
                AlertState.EXCEEDED -> "${event.category} Budget Exceeded"
                else -> "${event.category} Budget Warning"
            }

            val notification = NotificationCompat.Builder(context, NotificationHelper.CHANNEL_BUDGET_ALERTS)
                .setSmallIcon(android.R.drawable.stat_notify_more)
                .setContentTitle(title)
                .setContentText(event.message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(event.message))
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build()

            NotificationManagerCompat.from(context).notify(notificationId, notification)
        } catch (e: Throwable) {
            // Non-blocking best-effort: a failed alert must never crash the caller
            android.util.Log.e("AndroidBudgetNotifier", "Failed to send budget alert", e)
        }
    }

    private companion object {
        const val BUDGET_NOTIFICATION_ID_BASE = 20000
        const val BUDGET_NOTIFICATION_ID_SPAN = 10000
    }
}
