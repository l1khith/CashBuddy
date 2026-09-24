package com.cashbuddy.screenshot

import android.content.Context
import android.content.Intent
import android.net.Uri
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
    private val parser = ScreenshotParser(context)

    companion object {
        private const val DEDUP_WINDOW_SECS = 300L
    }

    /**
     * CRITICAL for Samsung/OEM devices: Immediately copy shared image stream to app cache
     * synchronously during intent handling before content URI access expires.
     */
    fun copyUriToCache(uri: Uri): File? {
        return try {
            val cacheFolder = File(context.cacheDir, "screenshots").apply { mkdirs() }
            val destFile = File(cacheFolder, "screenshot_${System.currentTimeMillis()}_${(1000..9999).random()}.jpg")

            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }
            destFile
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Process list of cached screenshot files, run OCR, deduplicate, and record into Unified Ledger.
     * Returns the list of newly created transaction IDs.
     */
    suspend fun processScreenshots(files: List<File>): List<Long> = withContext(Dispatchers.IO) {
        val createdIds = mutableListOf<Long>()

        for (file in files) {
            try {
                val parsed = parser.parseImageFile(file)
                if (parsed == null) {
                    file.delete()
                    continue
                }

                val timestamp = System.currentTimeMillis()

                // Deduplication check (5-minute window)
                val windowMs = DEDUP_WINDOW_SECS * 1000L
                val nearbyTransactions = transactionRepository.getByDateRange(
                    timestamp - windowMs,
                    timestamp + windowMs
                ).firstOrNull() ?: emptyList()

                val isDuplicate = nearbyTransactions.any { existing ->
                    isDuplicateTransaction(
                        amount1 = parsed.amount,
                        merchant1 = parsed.merchant,
                        time1 = timestamp,
                        amount2 = existing.amount,
                        merchant2 = existing.merchant,
                        time2 = existing.timestamp,
                        windowSecs = DEDUP_WINDOW_SECS
                    )
                }

                if (!isDuplicate) {
                    // Record raw text for transparency and learning
                    try {
                        trainingDataRepository.recordRawNotification(
                            rawText = parsed.rawText,
                            source = "screenshot",
                            sourceApp = parsed.appName,
                            extractedAmount = parsed.amount,
                            extractedType = parsed.transactionType.name,
                            extractedMerchant = parsed.merchant,
                            timestamp = timestamp
                        )
                    } catch (_: Throwable) {
                    }

                    // Resolve category
                    val allCategories = categoryRepository.getAll().firstOrNull() ?: emptyList()
                    val normalizedCategory = parsed.category.lowercase()
                    val matchedCategory = allCategories.find {
                        it.name.equals(parsed.category, ignoreCase = true)
                    } ?: allCategories.find {
                        it.name.lowercase().startsWith(normalizedCategory) ||
                        normalizedCategory.startsWith(it.name.lowercase().substringBefore(" "))
                    } ?: allCategories.firstOrNull()
                    val categoryId = matchedCategory?.id ?: 1L

                    // Resolve account
                    val allAccounts = accountRepository.getAll().firstOrNull() ?: emptyList()
                    val matchedAccount = allAccounts.find {
                        it.name.contains(parsed.appName, ignoreCase = true)
                    } ?: allAccounts.firstOrNull()
                    val accountId = matchedAccount?.id ?: 1L

                    val txType = when (parsed.transactionType) {
                        com.cashbuddy.core.TransactionType.DEBIT -> TransactionType.DEBIT
                        com.cashbuddy.core.TransactionType.CREDIT -> TransactionType.CREDIT
                    }

                    val autoConfirmThreshold = settingsRepository.getAutoConfirmThreshold().firstOrNull() ?: 10000.0
                    val minConfidence = settingsRepository.getMinConfidenceThreshold().firstOrNull() ?: 0.85f

                    val status = if (parsed.confidence >= minConfidence && parsed.amount < autoConfirmThreshold) {
                        TransactionStatus.CONFIRMED
                    } else {
                        TransactionStatus.PENDING
                    }

                    val transaction = Transaction(
                        id = 0L,
                        accountId = accountId,
                        categoryId = categoryId,
                        amount = parsed.amount,
                        type = txType,
                        status = status,
                        rawText = parsed.rawText,
                        sourceApp = "Screenshot: ${parsed.appName}",
                        merchant = parsed.merchant,
                        confidence = parsed.confidence,
                        timestamp = timestamp,
                        createdAt = timestamp,
                        updatedAt = timestamp
                    )

                    val insertedId = transactionRepository.insert(transaction)
                    createdIds.add(insertedId)
                }

                // Clean up cached file
                file.delete()
            } catch (_: Throwable) {
                file.delete()
            }
        }

        createdIds
    }
}
