package com.cashbuddy.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import com.cashbuddy.core.NotificationParser
import com.cashbuddy.core.RawNotification
import com.cashbuddy.core.TransactionClassifier
import com.cashbuddy.data.classifier.ModelManager
import com.cashbuddy.domain.model.Transaction
import com.cashbuddy.domain.model.TransactionStatus
import com.cashbuddy.domain.model.TransactionType
import com.cashbuddy.domain.parser.KotlinNotificationParser
import com.cashbuddy.domain.parser.ParsedNotificationResult
import com.cashbuddy.domain.parser.RawNotificationData
import com.cashbuddy.domain.repository.AccountRepository
import com.cashbuddy.domain.repository.CategoryRepository
import com.cashbuddy.domain.repository.SettingsRepository
import com.cashbuddy.domain.repository.TransactionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class TransactionNotificationListener : NotificationListenerService(), KoinComponent {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val transactionRepository: TransactionRepository by inject()
    private val accountRepository: AccountRepository by inject()
    private val categoryRepository: CategoryRepository by inject()
    private val settingsRepository: SettingsRepository by inject()
    private val modelManager: ModelManager by inject()

    private var classifier: TransactionClassifier? = null

    companion object {
        private const val TAG = "TxNotificationListener"
        const val REVIEW_CHANNEL_ID = "cashbuddy_review_channel"
        const val REVIEW_CHANNEL_NAME = "Transaction Reviews"
        const val NOTIFICATION_ID_BASE = 1000
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        initClassifierAsync()
    }

    private fun initClassifierAsync() {
        serviceScope.launch {
            try {
                val modelFile = modelManager.ensureModelExtracted()
                if (modelFile != null && modelFile.exists()) {
                    classifier = TransactionClassifier(modelFile.absolutePath)
                    android.util.Log.i(TAG, "On-device INT8 ONNX TransactionClassifier initialized")
                }
            } catch (e: Throwable) {
                android.util.Log.w(TAG, "Failed to initialize ONNX TransactionClassifier: ${e.message}")
            }
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val activeSbn = sbn ?: return
        if (activeSbn.isOngoing) return

        val packageName = activeSbn.packageName ?: return
        val extras = activeSbn.notification?.extras ?: return

        val title = extras.getString(android.app.Notification.EXTRA_TITLE) ?: ""
        val text = extras.getCharSequence(android.app.Notification.EXTRA_TEXT)?.toString()
            ?: extras.getCharSequence(android.app.Notification.EXTRA_BIG_TEXT)?.toString()
            ?: ""

        if (title.isBlank() && text.isBlank()) return
        val postTime = if (activeSbn.postTime > 0) activeSbn.postTime else System.currentTimeMillis()

        serviceScope.launch {
            try {
                // Verify notifications are enabled in settings
                val isEnabled = settingsRepository.getNotificationEnabled().firstOrNull() ?: true
                if (!isEnabled) return@launch

                // 1. Try Rust parser, fallback to Kotlin parser
                val parsed = parseNotification(packageName, title, text, postTime) ?: return@launch

                // 2. Duplicate suppression (within 5-minute window)
                val windowMs = 300_000L
                val nearbyTransactions = transactionRepository.getByDateRange(
                    postTime - windowMs,
                    postTime + windowMs
                ).firstOrNull() ?: emptyList()

                val isDuplicate = nearbyTransactions.any { existing ->
                    existing.amount == parsed.amount &&
                    existing.merchant.equals(parsed.merchant, ignoreCase = true) &&
                    existing.type == parsed.type
                }
                if (isDuplicate) return@launch

                // 3. Resolve Category ID (with On-Device ML Classifier enhancement)
                var resolvedCategoryName = parsed.categoryName
                var effectiveConfidence = parsed.confidence

                if (resolvedCategoryName.equals("UNKNOWN", ignoreCase = true) || parsed.confidence < 0.75f) {
                    try {
                        val queryText = parsed.merchant.ifBlank { parsed.rawText }
                        val mlResult = classifier?.classify(queryText)
                        if (mlResult != null && mlResult.confidence > 0.60f && mlResult.category != com.cashbuddy.core.Category.UNKNOWN) {
                            resolvedCategoryName = mlResult.category.name
                            effectiveConfidence = maxOf(effectiveConfidence, mlResult.confidence)
                            android.util.Log.i(TAG, "ML Classifier refined category to $resolvedCategoryName (conf=${mlResult.confidence})")
                        }
                    } catch (e: Throwable) {
                        android.util.Log.w(TAG, "ML classification fallback: ${e.message}")
                    }
                }

                val allCategories = categoryRepository.getAll().firstOrNull() ?: emptyList()
                val normalizedCategory = resolvedCategoryName.lowercase()
                val matchedCategory = allCategories.find {
                    it.name.equals(resolvedCategoryName, ignoreCase = true)
                } ?: allCategories.find {
                    it.name.lowercase().startsWith(normalizedCategory) ||
                    normalizedCategory.startsWith(it.name.lowercase().substringBefore(" "))
                } ?: allCategories.firstOrNull()
                val categoryId = matchedCategory?.id ?: 1L

                // 4. Status determination: Human-in-the-loop policy
                // Rule: Confidence >= 0.85 AND amount < autoConfirmThreshold => CONFIRMED, else PENDING
                val autoConfirmThreshold = settingsRepository.getAutoConfirmThreshold().firstOrNull() ?: 10000.0
                val minConfidence = settingsRepository.getMinConfidenceThreshold().firstOrNull() ?: 0.85f

                val status = if (effectiveConfidence >= minConfidence && parsed.amount < autoConfirmThreshold) {
                    TransactionStatus.CONFIRMED
                } else {
                    TransactionStatus.PENDING
                }

                // 5. Resolve Account ID
                val allAccounts = accountRepository.getAll().firstOrNull() ?: emptyList()
                val matchedAccount = if (!parsed.accountId.isNullOrBlank()) {
                    val last4 = parsed.accountId.takeLast(4)
                    allAccounts.find { it.number?.endsWith(last4) == true }
                } else {
                    allAccounts.find { it.name.contains(parsed.sourceApp, ignoreCase = true) }
                } ?: allAccounts.firstOrNull()
                val accountId = matchedAccount?.id ?: 1L

                // 6. Persist Transaction
                val newTransaction = Transaction(
                    id = 0L,
                    accountId = accountId,
                    categoryId = categoryId,
                    amount = parsed.amount,
                    type = parsed.type,
                    status = status,
                    rawText = parsed.rawText,
                    sourceApp = parsed.sourceApp,
                    merchant = parsed.merchant,
                    confidence = effectiveConfidence,
                    timestamp = parsed.timestamp,
                    createdAt = parsed.timestamp,
                    updatedAt = parsed.timestamp
                )

                val insertedId = transactionRepository.insert(newTransaction)

                // 7. If PENDING, show local Review Alert
                if (status == TransactionStatus.PENDING) {
                    showReviewAlert(insertedId, parsed.amount, parsed.merchant)
                }
            } catch (e: Throwable) {
                // Fail-safe: 100% offline, zero crash
                e.printStackTrace()
            }
        }
    }

    private fun parseNotification(
        packageName: String,
        title: String,
        text: String,
        postTime: Long
    ): ParsedNotificationResult? {
        // Attempt native Rust engine
        try {
            val rustParser = NotificationParser()
            val raw = RawNotification(
                packageName = packageName,
                title = title,
                text = text,
                timestamp = postTime
            )
            val parsed = rustParser.parse(raw)
            if (parsed != null) {
                return ParsedNotificationResult(
                    amount = parsed.amount,
                    type = if (parsed.transactionType == com.cashbuddy.core.TransactionType.DEBIT) {
                        TransactionType.DEBIT
                    } else {
                        TransactionType.CREDIT
                    },
                    categoryName = parsed.category.name,
                    merchant = parsed.merchant,
                    accountId = parsed.accountId,
                    sourceApp = parsed.sourceApp,
                    rawText = parsed.rawText,
                    confidence = parsed.confidence,
                    timestamp = parsed.timestamp
                )
            }
        } catch (_: Throwable) {
            // Rust native lib unavailable or fallback
        }

        // Pure Kotlin parser fallback
        val kotlinParser = KotlinNotificationParser()
        return kotlinParser.parse(
            RawNotificationData(
                packageName = packageName,
                title = title,
                text = text,
                timestamp = postTime
            )
        )
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                REVIEW_CHANNEL_ID,
                REVIEW_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Alerts for transactions requiring review"
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            notificationManager?.createNotificationChannel(channel)
        }
    }

    private fun showReviewAlert(transactionId: Long, amount: Double, merchant: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

        // Launch app directly into review flow
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("EXTRA_NAVIGATE_TO", "review_inbox")
            putExtra("EXTRA_TRANSACTION_ID", transactionId)
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            transactionId.toInt(),
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val formattedAmount = "₹" + if (amount == amount.toLong().toDouble()) {
            amount.toLong().toString()
        } else {
            String.format("%.2f", amount)
        }

        val notification = NotificationCompat.Builder(this, REVIEW_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_more)
            .setContentTitle("Transaction Review Required")
            .setContentText("$formattedAmount at $merchant needs your confirmation")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify((NOTIFICATION_ID_BASE + (transactionId % 1000)).toInt(), notification)
    }
}
