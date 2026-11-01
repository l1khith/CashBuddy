package com.cashbuddy.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.cashbuddy.domain.repository.LearnedTokenRepository
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Weekly background garbage collection worker for the learned token dictionary.
 * Prevents memory and storage bloat by purging the rarest/least-observed tokens
 * if the total row count exceeds the safety threshold (default 20,000).
 */
class TokenPurgeWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams), KoinComponent {

    private val learnedTokenRepository: LearnedTokenRepository by inject()

    override suspend fun doWork(): Result {
        return try {
            learnedTokenRepository.purgeExcessTokens(maxCount = MAX_TOKEN_COUNT)
            Result.success()
        } catch (_: Throwable) {
            Result.retry()
        }
    }

    companion object {
        const val WORK_NAME = "token_purge_worker"
        const val MAX_TOKEN_COUNT = 20_000L
    }
}
