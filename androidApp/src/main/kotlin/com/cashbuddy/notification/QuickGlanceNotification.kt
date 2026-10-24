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
import com.cashbuddy.core.budget.BudgetEngine
import com.cashbuddy.core.budget.BudgetPacer
import com.cashbuddy.domain.model.BudgetCategories
import com.cashbuddy.domain.model.TransactionType
import com.cashbuddy.domain.repository.BudgetRepository
import com.cashbuddy.domain.repository.SettingsRepository
import com.cashbuddy.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.firstOrNull
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.Calendar
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.math.roundToLong

object QuickGlanceNotification : KoinComponent {

    const val NOTIFICATION_ID = 40001

    private val transactionRepository: TransactionRepository by inject()
    private val budgetRepository: BudgetRepository by inject()
    private val budgetEngine: BudgetEngine by inject()
    private val settingsRepository: SettingsRepository by inject()

    suspend fun update(context: Context) {
        try {
            val smartEnabled = settingsRepository.getSmartNotificationsEnabled().firstOrNull() ?: true
            if (!smartEnabled) {
                dismiss(context)
                return
            }

            val now = System.currentTimeMillis()

            // Calculate start of today
            val cal = Calendar.getInstance().apply {
                timeInMillis = now
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val startOfToday = cal.timeInMillis

            // Calculate start of current month
            cal.set(Calendar.DAY_OF_MONTH, 1)
            val startOfMonth = cal.timeInMillis

            // Get transactions for month
            val monthTxs = transactionRepository.getByDateRange(startOfMonth, now + 1000L).firstOrNull() ?: emptyList()
            val monthDebits = monthTxs.filter { it.type == TransactionType.DEBIT && it.amount > 0 }
            val monthSpent = monthDebits.sumOf { it.amount }

            val todaySpent = monthDebits.filter { it.timestamp >= startOfToday }.sumOf { it.amount }

            // Find top category budget pacing
            val activeBudgets = budgetRepository.getActive().filter { it.category != BudgetCategories.GLOBAL }
            val highestPacing = activeBudgets.mapNotNull { budget ->
                val status = budgetEngine.statusFor(budget, now)
                val pacing = BudgetPacer.calculatePacing(
                    spent = status.spent,
                    budgetAmount = budget.amount,
                    period = budget.period,
                    nowEpochMs = now
                )
                Pair(budget.category, pacing.actualPercent)
            }.maxByOrNull { it.second }

            val line2 = when {
                highestPacing != null && highestPacing.second > 0 -> {
                    "${highestPacing.first}: ${highestPacing.second.roundToInt()}% used"
                }
                monthDebits.isNotEmpty() -> {
                    "Spending on track"
                }
                else -> {
                    "No expenses yet"
                }
            }

            val title = "Today: ₹${formatAmount(todaySpent)} | Month: ₹${formatAmount(monthSpent)}"

            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                NOTIFICATION_ID,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(context, NotificationHelper.CHANNEL_QUICK_GLANCE)
                .setSmallIcon(android.R.drawable.stat_notify_more)
                .setContentTitle(title)
                .setContentText(line2)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .setContentIntent(pendingIntent)
                .build()

            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
            }
        } catch (e: Throwable) {
            android.util.Log.e("QuickGlanceNotification", "Failed to update persistent notification", e)
        }
    }

    fun dismiss(context: Context) {
        try {
            NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
        } catch (e: Throwable) {
            android.util.Log.e("QuickGlanceNotification", "Failed to dismiss persistent notification", e)
        }
    }

    private fun formatAmount(amount: Double): String {
        return if (amount == amount.roundToLong().toDouble()) {
            amount.roundToLong().toString()
        } else {
            String.format(Locale.US, "%.2f", amount)
        }
    }
}
