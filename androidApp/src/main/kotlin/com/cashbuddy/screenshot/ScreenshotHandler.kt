package com.cashbuddy.screenshot

import android.content.Context
import android.net.Uri
import android.util.Log
import com.cashbuddy.core.isDuplicateTransaction
import com.cashbuddy.domain.model.Transaction
import com.cashbuddy.domain.model.TransactionStatus
import com.cashbuddy.domain.model.TransactionType
import com.cashbuddy.domain.repository.AccountRepository
import com.cashbuddy.domain.repository.CategoryRepository
import com.cashbuddy.domain.repository.SettingsRepository
import com.cashbuddy.domain.repository.TrainingDataRepository
import com.cashbuddy.domain.repository.TransactionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.File
import java.io.FileOutputStream

class ScreenshotHandler(private val context: Context) : KoinComponent {

    private val transactionRepository: TransactionRepository by inject()
    private val categoryRepository: CategoryRepository by inject()
    private val accountRepository: AccountRepository by inject()
    private val settingsRepository: SettingsRepository by inject()
    private val trainingDataRepository: TrainingDataRepository by inject()
    private val pipeline: com.cashbuddy.core.prob.MessagePipeline by inject()
    private val parser = ScreenshotParser(context)

    companion object {
        private const val TAG = "ScreenshotHandler"
        private const val DEDUP_WINDOW_SECS = 300L
    }

    /**
     * CRITICAL for Samsung/OEM devices: Immediately copy shared image stream to app cache
     * synchronously during intent handling before content URI access expires.
     */
    fun copyUriToCache(uri: Uri): File? {
        val cacheFolder = File(context.cacheDir, "screenshots").apply { mkdirs() }
        val destFile = File(cacheFolder, "screenshot_${System.currentTimeMillis()}_${(1000..9999).random()}.jpg")
        var delayMs = 150L

        repeat(5) { attempt ->
            try {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(destFile).use { output ->
                        input.copyTo(output)
                    }
                }
                if (destFile.exists() && destFile.length() > 0) {
                    Log.d(TAG, "Cached screenshot: ${destFile.name} (${destFile.length()} bytes)")
                    return destFile
                }
            } catch (e: Throwable) {
                if (attempt == 4) {
                    Log.e(TAG, "Failed to cache screenshot URI: $uri", e)
                }
            }
            try {
                Thread.sleep(delayMs)
            } catch (_: InterruptedException) { }
            delayMs *= 2
        }
        return null
    }

    /**
     * Process list of cached screenshot files, run OCR, deduplicate, and record into Unified Ledger.
     * Returns the list of newly created transaction IDs.
     */
    suspend fun processScreenshots(files: List<File>): List<Long> = withContext(Dispatchers.IO) {
        val createdIds = mutableListOf<Long>()
        Log.i(TAG, "Processing ${files.size} screenshot(s)")

        for (file in files) {
            try {
                val parsed = parser.parseImageFile(file)
                if (parsed == null) {
                    Log.w(TAG, "Could not parse screenshot: ${file.name}")
                    file.delete()
                    continue
                }

                val timestamp = System.currentTimeMillis()
                val rawMessage = com.cashbuddy.core.prob.RawMessage(
                    id = "scr_${timestamp}_${(1000..9999).random()}",
                    sourceType = com.cashbuddy.core.prob.SourceType.SCREENSHOT,
                    packageName = "screenshot",
                    senderId = null,
                    title = parsed.appName ?: "Screenshot",
                    text = parsed.rawText,
                    timestamp = timestamp,
                    imagePath = file.absolutePath
                )

                val outcome = pipeline.ingest(rawMessage)

                try {
                    trainingDataRepository.recordRawNotification(
                        rawText = parsed.rawText,
                        source = "screenshot",
                        sourceApp = parsed.appName ?: "screenshot",
                        extractedAmount = parsed.amount,
                        extractedType = parsed.transactionType.name,
                        extractedMerchant = parsed.merchant,
                        timestamp = timestamp
                    )
                } catch (e: Throwable) {
                    Log.w(TAG, "Failed to record training data", e)
                }

                when (outcome) {
                    is com.cashbuddy.core.prob.PipelineOutcome.Logged -> {
                        createdIds.add(outcome.txId)
                        Log.i(TAG, "Screenshot logged: txId=${outcome.txId}")
                    }
                    is com.cashbuddy.core.prob.PipelineOutcome.PendingReview -> {
                        createdIds.add(outcome.txId)
                        Log.i(TAG, "Screenshot flagged for review: txId=${outcome.txId}")
                    }
                    else -> Log.i(TAG, "Screenshot outcome: $outcome")
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Failed to process screenshot: ${file.name}", e)
            } finally {
                file.delete()
            }
        }

        Log.i(TAG, "Screenshot processing complete: ${createdIds.size} transaction(s) created")
        createdIds
    }
}
