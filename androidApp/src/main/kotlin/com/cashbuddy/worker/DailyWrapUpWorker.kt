package com.cashbuddy.worker

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.cashbuddy.MainActivity
import com.cashbuddy.R
import com.cashbuddy.domain.model.TransactionType
import com.cashbuddy.domain.repository.SettingsRepository
import com.cashbuddy.domain.repository.TransactionRepository
import com.cashbuddy.notification.NotificationHelper
import kotlinx.coroutines.flow.firstOrNull
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.Calendar
import kotlin.math.roundToLong

class DailyWrapUpWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams), KoinComponent {

    private val transactionRepository: TransactionRepository by inject()
    private val settingsRepository: SettingsRepository by inject()

    companion object {
        const val WORK_NAME = "DailyWrapUpWorker"
        const val NOTIFICATION_ID = 4001
    }

    override suspend fun doWork(): Result {
        return try {
            val settings = settingsRepository.getSettings().firstOrNull()
            if (settings?.notificationsEnabled == false) {
                return Result.success()
            }

            val now = System.currentTimeMillis()
            val startOfDay = Calendar.getInstance().apply {
                timeInMillis = now
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis

            val todayTransactions = transactionRepository.getByDateRange(startOfDay, now).firstOrNull().orEmpty()
            val todaySpent = todayTransactions
                .filter { it.type == TransactionType.DEBIT }
                .sumOf { it.amount }

            val formattedAmount = if (todaySpent % 1.0 == 0.0) {
                "₹${todaySpent.roundToLong()}"
            } else {
                "₹${(todaySpent * 100.0).roundToLong() / 100.0}"
            }

            val contentText = if (todaySpent > 0.0) {
                applicationContext.getString(R.string.notification_daily_wrap_up_spent, formattedAmount)
            } else {
                applicationContext.getString(R.string.notification_daily_wrap_up_zero)
            }

            sendNotification(contentText)

            // Reschedule for next day
            val hour = settings?.dailyWrapUpHour ?: 22
            val minute = settings?.dailyWrapUpMinute ?: 0
            AssistantWorkManager.scheduleDailyWrapUp(applicationContext, hour, minute)

            Result.success()
        } catch (e: Throwable) {
            android.util.Log.e("DailyWrapUpWorker", "Failed to run daily wrap up", e)
            Result.success() // Return success to avoid unnecessary failure retries
        }
    }

    private fun sendNotification(text: String) {
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("EXTRA_NAVIGATE_TO", "review")
        }

        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            NOTIFICATION_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(applicationContext, NotificationHelper.CHANNEL_DAILY_WRAP_UP)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(applicationContext.getString(R.string.notification_daily_wrap_up_title))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(applicationContext).notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // Permission not granted on Android 13+
        }
    }
}
