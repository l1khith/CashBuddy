package com.cashbuddy.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.cashbuddy.domain.usecase.DetectAndStoreRecurrencesUseCase
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class RecurrenceWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams), KoinComponent {

    private val detectAndStoreRecurrencesUseCase: DetectAndStoreRecurrencesUseCase by inject()

    companion object {
        const val WORK_NAME = "RecurrenceWorker"
    }

    override suspend fun doWork(): Result {
        return try {
            val now = System.currentTimeMillis()
            detectAndStoreRecurrencesUseCase(now)
            Result.success()
        } catch (e: Throwable) {
            Result.retry()
        }
    }
}
