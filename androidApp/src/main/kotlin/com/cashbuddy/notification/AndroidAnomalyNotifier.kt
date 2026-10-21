package com.cashbuddy.notification

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.cashbuddy.MainActivity
import com.cashbuddy.core.anomaly.AnomalyNotifier
import com.cashbuddy.domain.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlin.math.roundToLong

class AndroidAnomalyNotifier(
    private val context: Context,
    private val settingsRepository: SettingsRepository
) : AnomalyNotifier {

    private val scope = CoroutineScope(Dispatchers.IO)

    override fun sendAnomalyAlert(transactionId: Long, amount: Double, merchant: String) {
        scope.launch {
            try {
                val smartEnabled = settingsRepository.getSmartNotificationsEnabled().firstOrNull() ?: true
                if (!smartEnabled) return@launch

                val notificationId = (30000 + (transactionId % 10000)).toInt()

                // 1. "It's mine" action -> BroadcastReceiver dismisses and marks CONFIRMED
                val itsMineIntent = Intent(context, AnomalyActionReceiver::class.java).apply {
                    action = AnomalyActionReceiver.ACTION_ITS_MINE
                    putExtra(AnomalyActionReceiver.EXTRA_TRANSACTION_ID, transactionId)
                    putExtra(AnomalyActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
                }
                val itsMinePendingIntent = PendingIntent.getBroadcast(
                    context,
                    notificationId * 2,
                    itsMineIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                // 2. "Review" action -> opens MainActivity to Review Inbox
                val reviewIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra("EXTRA_NAVIGATE_TO", "review_inbox")
                    putExtra("EXTRA_TRANSACTION_ID", transactionId)
                }
                val reviewPendingIntent = PendingIntent.getActivity(
                    context,
                    notificationId * 2 + 1,
                    reviewIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                val formattedAmount = if (amount == amount.roundToLong().toDouble()) {
                    amount.roundToLong().toString()
                } else {
                    String.format("%.2f", amount)
                }

                val notification = NotificationCompat.Builder(context, NotificationHelper.CHANNEL_ANOMALY_ALERTS)
                    .setSmallIcon(android.R.drawable.stat_notify_more)
                    .setContentTitle("Unusual Transaction")
                    .setContentText("₹$formattedAmount at $merchant is higher than usual.")
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setVibrate(longArrayOf(0, 250, 150, 250))
                    .setAutoCancel(true)
                    .setContentIntent(reviewPendingIntent)
                    .addAction(0, "It's mine", itsMinePendingIntent)
                    .addAction(0, "Review", reviewPendingIntent)
                    .build()

                if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                    NotificationManagerCompat.from(context).notify(notificationId, notification)
                }
            } catch (e: Throwable) {
                android.util.Log.e("AndroidAnomalyNotifier", "Failed to send anomaly alert", e)
            }
        }
    }
}
