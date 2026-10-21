package com.cashbuddy.worker

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.edit
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.cashbuddy.MainActivity
import com.cashbuddy.core.budget.BudgetEngine
import com.cashbuddy.core.budget.BudgetPacer
import com.cashbuddy.domain.model.AlertState
import com.cashbuddy.domain.model.BudgetCategories
import com.cashbuddy.domain.repository.BudgetRepository
import com.cashbuddy.domain.repository.SettingsRepository
import com.cashbuddy.notification.NotificationHelper
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.datetime.Instant
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.math.roundToInt
import kotlin.math.roundToLong

class BudgetAlertWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams), KoinComponent {

    private val budgetRepository: BudgetRepository by inject()
    private val budgetEngine: BudgetEngine by inject()
    private val settingsRepository: SettingsRepository by inject()

    private val prefs: SharedPreferences =
        appContext.getSharedPreferences("cashbuddy_budget_alerts", Context.MODE_PRIVATE)

    companion object {
        const val WORK_NAME = "BudgetAlertWorker"
        private const val DEDUPLICATION_WINDOW_MS = 24 * 60 * 60 * 1000L // 24 hours
    }

    override suspend fun doWork(): Result {
        return try {
            val smartEnabled = settingsRepository.getSmartNotificationsEnabled().firstOrNull() ?: true
            if (!smartEnabled) {
                return Result.success()
            }

            val now = System.currentTimeMillis()
            val activeBudgets = budgetRepository.getActive()

            // Sort: specific categories first, then GLOBAL
            val categoryBudgets = activeBudgets.filter { it.category != BudgetCategories.GLOBAL }
            val globalBudgets = activeBudgets.filter { it.category == BudgetCategories.GLOBAL }
            val orderedBudgets = categoryBudgets + globalBudgets

            for (budget in orderedBudgets) {
                val isGlobal = (budget.category == BudgetCategories.GLOBAL)
                val status = budgetEngine.statusFor(budget, now)
                val pacing = BudgetPacer.calculatePacing(
                    spent = status.spent,
                    budgetAmount = budget.amount,
                    period = budget.period,
                    now = Instant.fromEpochMilliseconds(now)
                )

                val daysRemaining = pacing.daysRemaining

                // Do not fire on the last day (daysRemaining <= 0), user cannot adjust spending
                if (daysRemaining <= 0) continue

                // Check 24-hour deduplication per category
                val prefKey = "last_alert_${budget.category}"
                val lastAlertTimestamp = prefs.getLong(prefKey, 0L)
                if (now - lastAlertTimestamp < DEDUPLICATION_WINDOW_MS) {
                    continue
                }

                var title: String? = null
                var message: String? = null
                var alertState: AlertState? = null

                val categoryLabel = if (isGlobal) "Global" else budget.category

                // 1. BREACHED: actualPercent >= 100%
                if (pacing.actualPercent >= 100.0) {
                    alertState = AlertState.EXCEEDED
                    val overage = formatAmount(status.spent - budget.amount)
                    title = "$categoryLabel Budget Exceeded"
                    message = "$categoryLabel budget exceeded by ₹$overage."
                }
                // 2. DANGER ZONE: actualPercent >= 85% AND daysRemaining >= 3
                else if (pacing.actualPercent >= 85.0 && daysRemaining >= 3) {
                    if (!isGlobal || pacing.projectedOverage > 0.0) {
                        alertState = AlertState.WARNING
                        title = "$categoryLabel Budget Danger Zone"
                        message = "$categoryLabel budget is at ${pacing.actualPercent.roundToInt()}% with $daysRemaining days left."
                    }
                }
                // 3. OVERSPENDING PACE: actualPercent - expectedPercent >= 15% AND daysRemaining >= 3
                else if ((pacing.actualPercent - pacing.expectedPercent) >= 15.0 && daysRemaining >= 3) {
                    if (!isGlobal || pacing.projectedOverage > 0.0) {
                        alertState = AlertState.WARNING
                        val projectedOverageStr = formatAmount(pacing.projectedOverage)
                        title = "$categoryLabel Overspending Warning"
                        message = "At this rate, you'll exceed your $categoryLabel budget by ₹$projectedOverageStr."
                    }
                }

                if (title != null && message != null && alertState != null) {
                    sendNotification(
                        notificationId = (10000 + (budget.id.hashCode() % 10000)),
                        title = title,
                        message = message
                    )
                    prefs.edit { putLong(prefKey, now) }
                    budgetRepository.markAlerted(budget.id, alertState, now)
                }
            }

            Result.success()
        } catch (e: Throwable) {
            android.util.Log.e("BudgetAlertWorker", "Error executing budget alert check", e)
            Result.retry()
        }
    }

    private fun sendNotification(notificationId: Int, title: String, message: String) {
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("EXTRA_NAVIGATE_TO", "budgets")
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(applicationContext, NotificationHelper.CHANNEL_BUDGET_ALERTS)
            .setSmallIcon(android.R.drawable.stat_notify_more)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        if (ActivityCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            NotificationManagerCompat.from(applicationContext).notify(notificationId, notification)
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
