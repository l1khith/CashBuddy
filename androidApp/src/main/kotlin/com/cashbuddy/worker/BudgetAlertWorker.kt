// NO-NETWORK
package com.cashbuddy.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.cashbuddy.core.budget.BudgetAlertScheduler
import com.cashbuddy.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.firstOrNull
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Periodic budget-alert check. Delegates entirely to the shared
 * [BudgetAlertScheduler] (pacing math, alert-state dedup via
 * budget.lastAlertState in the DB, and notification delivery through the
 * DI-bound [com.cashbuddy.core.budget.BudgetNotifier]).
 *
 * This replaces the worker's previously duplicated inline alert logic — the
 * scheduler is now the single source of truth for budget alerting, and the same
 * code path serves both the periodic worker and the pipeline's live check
 * (MessagePipeline.checkAndAlert).
 */
class BudgetAlertWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams), KoinComponent {

    private val budgetAlertScheduler: BudgetAlertScheduler by inject()
    private val settingsRepository: SettingsRepository by inject()

    companion object {
        const val WORK_NAME = "BudgetAlertWorker"
    }

    override suspend fun doWork(): Result {
        return try {
            val smartEnabled = settingsRepository.getSmartNotificationsEnabled().firstOrNull() ?: true
            if (!smartEnabled) {
                return Result.success()
            }

            budgetAlertScheduler.checkAndAlert(System.currentTimeMillis())

            Result.success()
        } catch (e: Throwable) {
            android.util.Log.e("BudgetAlertWorker", "Error executing budget alert check", e)
            Result.retry()
        }
    }
}
