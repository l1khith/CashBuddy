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
    }

    fun cancelAll(context: Context) {
        val workManager = WorkManager.getInstance(context)
        workManager.cancelUniqueWork(BudgetAlertWorker.WORK_NAME)
        workManager.cancelUniqueWork(WeeklyDigestWorker.WORK_NAME)
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
