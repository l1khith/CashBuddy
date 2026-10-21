package com.cashbuddy.worker

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.cashbuddy.MainActivity
import com.cashbuddy.domain.model.TransactionType
import com.cashbuddy.domain.repository.CategoryRepository
import com.cashbuddy.domain.repository.SettingsRepository
import com.cashbuddy.domain.repository.TransactionRepository
import com.cashbuddy.notification.NotificationHelper
import kotlinx.coroutines.flow.firstOrNull
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.math.roundToLong

class WeeklyDigestWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams), KoinComponent {

    private val transactionRepository: TransactionRepository by inject()
    private val categoryRepository: CategoryRepository by inject()
    private val settingsRepository: SettingsRepository by inject()

    companion object {
        const val WORK_NAME = "WeeklyDigestWorker"
        const val NOTIFICATION_ID = 10001
        private const val SEVEN_DAYS_MS = 7 * 24 * 60 * 60 * 1000L
    }

    override suspend fun doWork(): Result {
        return try {
            val smartEnabled = settingsRepository.getSmartNotificationsEnabled().firstOrNull() ?: true
            if (!smartEnabled) {
                return Result.success()
            }

            val now = System.currentTimeMillis()
            val startWindow = now - SEVEN_DAYS_MS
            val txList = transactionRepository.getByDateRange(startWindow, now).firstOrNull() ?: emptyList()

            val debits = txList.filter { it.type == TransactionType.DEBIT && it.amount > 0 }
            val totalSpent = debits.sumOf { it.amount }

            val message = if (debits.isEmpty() || totalSpent <= 0.0) {
                "Your weekly recap: Spent ₹0. No expenses recorded this week."
            } else {
                val byCategory = debits.groupBy { it.categoryId }
                    .mapValues { (_, txs) -> txs.sumOf { it.amount } }
                val topEntry = byCategory.maxByOrNull { it.value }

                val topCategoryName = if (topEntry != null) {
                    categoryRepository.getById(topEntry.key).firstOrNull()?.name ?: "Expenses"
                } else {
                    "Expenses"
                }
                val topAmount = topEntry?.value ?: 0.0

                "Your weekly recap: Spent ₹${formatAmount(totalSpent)}. Top: $topCategoryName (₹${formatAmount(topAmount)})."
            }

            sendNotification(message)
            Result.success()
        } catch (e: Throwable) {
            android.util.Log.e("WeeklyDigestWorker", "Error executing weekly digest", e)
            Result.retry()
        }
    }

    private fun sendNotification(message: String) {
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("EXTRA_NAVIGATE_TO", "stats")
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            NOTIFICATION_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(applicationContext, NotificationHelper.CHANNEL_WEEKLY_DIGEST)
            .setSmallIcon(android.R.drawable.stat_notify_more)
            .setContentTitle("Weekly Spending Recap")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        if (ActivityCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            NotificationManagerCompat.from(applicationContext).notify(NOTIFICATION_ID, notification)
        }
    }

    private fun formatAmount(amount: Double): String {
        return if (amount == amount.roundToLong().toDouble()) {
            amount.roundToLong().toString()
        } else {
            String.format("%.2f", amount)
        }
    }
}
