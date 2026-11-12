package com.cashbuddy.worker

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.Calendar
import java.util.concurrent.TimeUnit

object AssistantWorkManager {

    fun scheduleAll(context: Context) {
        val workManager = WorkManager.getInstance(context)

        // 1. BudgetAlertWorker — Runs daily at 8:00 PM
        val budgetDelay = calculateInitialDelay(
            targetHour = 20,
            targetMinute = 0,
            daysInterval = 1
        )
        val budgetRequest = PeriodicWorkRequestBuilder<BudgetAlertWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(budgetDelay, TimeUnit.MILLISECONDS)
            .build()

        workManager.enqueueUniquePeriodicWork(
            BudgetAlertWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            budgetRequest
        )

        // 2. WeeklyDigestWorker — Runs every Sunday at 7:00 PM
        val weeklyDelay = calculateWeeklySundayDelay(
            targetHour = 19,
            targetMinute = 0
        )
        val weeklyRequest = PeriodicWorkRequestBuilder<WeeklyDigestWorker>(7, TimeUnit.DAYS)
            .setInitialDelay(weeklyDelay, TimeUnit.MILLISECONDS)
            .build()

        workManager.enqueueUniquePeriodicWork(
            WeeklyDigestWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            weeklyRequest
        )

        // 3. RecurrenceWorker — Runs daily every 24 hours
        scheduleRecurrenceWorker(context)

        // NOTE: TokenPurgeWorker is intentionally NOT scheduled here. The learned-token
        // GC is core data maintenance and must run regardless of notification settings —
        // it is scheduled unconditionally via [scheduleTokenPurge] (audit fix 1.4).
    }

    fun cancelAll(context: Context) {
        val workManager = WorkManager.getInstance(context)
        workManager.cancelUniqueWork(BudgetAlertWorker.WORK_NAME)
        workManager.cancelUniqueWork(WeeklyDigestWorker.WORK_NAME)
        workManager.cancelUniqueWork(DailyWrapUpWorker.WORK_NAME)
        // NOTE: TokenPurgeWorker is never cancelled here (audit fix 1.4) — see scheduleAll.
    }

    fun scheduleDailyWrapUp(context: Context, hour: Int, minute: Int) {
        val workManager = WorkManager.getInstance(context)
        val delay = calculateInitialDelay(targetHour = hour, targetMinute = minute, daysInterval = 1)
        val wrapUpRequest = androidx.work.OneTimeWorkRequestBuilder<DailyWrapUpWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .build()
        workManager.enqueueUniqueWork(
            DailyWrapUpWorker.WORK_NAME,
            androidx.work.ExistingWorkPolicy.REPLACE,
            wrapUpRequest
        )
    }

    fun cancelDailyWrapUp(context: Context) {
        val workManager = WorkManager.getInstance(context)
        workManager.cancelUniqueWork(DailyWrapUpWorker.WORK_NAME)
    }

    /**
     * Schedules the 24-hour recurrence pattern analysis. Core intelligence:
     * analyzes historical transactions to auto-confirm predictable transactions
     * and silently suppress false anomalies.
     */
    fun scheduleRecurrenceWorker(context: Context) {
        val workManager = WorkManager.getInstance(context)
        val constraints = androidx.work.Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .build()
        val recurrenceRequest = PeriodicWorkRequestBuilder<RecurrenceWorker>(24, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()
        workManager.enqueueUniquePeriodicWork(
            RecurrenceWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            recurrenceRequest
        )
    }

    /**
     * Schedules the weekly learned-token GC. Core data maintenance: always scheduled,
     * independent of the "smart notifications" toggle, so the learned_token table can
     * never grow unbounded because the user disabled assistant notifications.
     */
    fun scheduleTokenPurge(context: Context) {
        val workManager = WorkManager.getInstance(context)
        val purgeDelay = calculateWeeklySundayDelay(
            targetHour = 2,
            targetMinute = 0
        )
        val constraints = androidx.work.Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .build()
        val purgeRequest = PeriodicWorkRequestBuilder<TokenPurgeWorker>(7, TimeUnit.DAYS)
            .setInitialDelay(purgeDelay, TimeUnit.MILLISECONDS)
            .setConstraints(constraints)
            .build()

        workManager.enqueueUniquePeriodicWork(
            TokenPurgeWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            purgeRequest
        )
    }

    private fun calculateInitialDelay(targetHour: Int, targetMinute: Int, daysInterval: Int): Long {
        val now = System.currentTimeMillis()
        val calendar = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, targetHour)
            set(Calendar.MINUTE, targetMinute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (calendar.timeInMillis <= now) {
            calendar.add(Calendar.DAY_OF_YEAR, daysInterval)
        }
        return calendar.timeInMillis - now
    }

    private fun calculateWeeklySundayDelay(targetHour: Int, targetMinute: Int): Long {
        val now = System.currentTimeMillis()
        val calendar = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY)
            set(Calendar.HOUR_OF_DAY, targetHour)
            set(Calendar.MINUTE, targetMinute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (calendar.timeInMillis <= now) {
            calendar.add(Calendar.WEEK_OF_YEAR, 1)
        }
        return calendar.timeInMillis - now
    }
}
