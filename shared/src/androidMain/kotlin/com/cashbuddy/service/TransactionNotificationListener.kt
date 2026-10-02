package com.cashbuddy.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationCompat
import com.cashbuddy.core.CategoryEngine
import com.cashbuddy.core.MerchantRuleEntry
import com.cashbuddy.core.NotificationParser
import com.cashbuddy.core.RawNotification
import com.cashbuddy.domain.model.Transaction
import com.cashbuddy.domain.model.TransactionStatus
import com.cashbuddy.domain.model.TransactionType
import com.cashbuddy.domain.parser.KotlinNotificationParser
import com.cashbuddy.domain.parser.ParsedNotificationResult
import com.cashbuddy.domain.parser.RawNotificationData
import com.cashbuddy.domain.repository.AccountRepository
import com.cashbuddy.domain.repository.CategoryRepository
import com.cashbuddy.domain.repository.MerchantRuleRepository
import com.cashbuddy.domain.repository.SettingsRepository
import com.cashbuddy.domain.repository.TrainingDataRepository
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
    private val trainingDataRepository: TrainingDataRepository by inject()
    private val merchantRuleRepository: MerchantRuleRepository by inject()
    private val categoryEngine: CategoryEngine by inject()
    private val notificationParser: NotificationParser by inject()
    private val kotlinParser: KotlinNotificationParser by inject()
    private val sourceDetector: com.cashbuddy.core.prob.SourceDetector by inject()
    private val messagePipeline: com.cashbuddy.core.prob.MessagePipeline by inject()

    companion object {
        private const val TAG = "TxNotificationListener"
        const val REVIEW_CHANNEL_ID = "cashbuddy_review_channel"
        const val REVIEW_CHANNEL_NAME = "Transaction Reviews"
        const val NOTIFICATION_ID_BASE = 1000
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        loadRulesIntoEngine()
        seedDefaultsIfEmpty()
    }

    private fun seedDefaultsIfEmpty() {
        serviceScope.launch {
            try {
                val now = System.currentTimeMillis()
                categoryRepository.seedDefaults(now)
                accountRepository.seedDefaults(now)
            } catch (e: Throwable) {
                Log.w(TAG, "Failed seeding defaults in listener", e)
            }
        }
    }

    private fun loadRulesIntoEngine() {
        serviceScope.launch {
            try {
                val rules = merchantRuleRepository.getAll().firstOrNull() ?: emptyList()
                val entries = rules.map {
                    MerchantRuleEntry(
                        merchant = it.pattern,
                        category = it.categoryName ?: "Unknown"
                    )
                }
                categoryEngine.loadUserRules(entries)
            } catch (e: Throwable) {
                Log.e(TAG, "Failed to load rules into CategoryEngine", e)
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
        if (packageName == applicationContext.packageName) return
        val extras = activeSbn.notification?.extras ?: return

        val title = extras.getCharSequence(android.app.Notification.EXTRA_TITLE)?.toString() ?: ""
        val subText = extras.getCharSequence(android.app.Notification.EXTRA_SUB_TEXT)?.toString()
        val effectiveTitle = when {
            title.isNotBlank() && !subText.isNullOrBlank() && !title.contains(subText, ignoreCase = true) -> "$subText · $title"
            title.isBlank() && !subText.isNullOrBlank() -> subText
            else -> title
        }
        var text = extras.getCharSequence(android.app.Notification.EXTRA_BIG_TEXT)?.toString()
            ?: extras.getCharSequence(android.app.Notification.EXTRA_TEXT)?.toString()
            ?: ""

        // Extract from InboxStyle notifications (e.g. grouped app notifications)
        val lines = extras.getCharSequenceArray(android.app.Notification.EXTRA_TEXT_LINES)
        if (!lines.isNullOrEmpty()) {
            val linesJoined = lines.filterNotNull().joinToString(" ") { it.toString() }
            text = if (text.isBlank()) {
                linesJoined
            } else if (!text.contains(linesJoined, ignoreCase = true)) {
                "$text $linesJoined"
            } else {
                text
            }
        }

        // Extract from MessagingStyle notifications (e.g. Google Messages, Samsung Messages)
        var senderId: String? = null
        val messagingStyle = androidx.core.app.NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(activeSbn.notification)
        if (messagingStyle != null) {
            val messages = messagingStyle.messages
            if (messages.isNotEmpty()) {
                val lastMessage = messages.last()
                val msgText = lastMessage.text?.toString()
                if (!msgText.isNullOrBlank()) {
                    text = msgText
                }
                val personName = lastMessage.person?.name?.toString()
                val conversationTitle = messagingStyle.conversationTitle?.toString()
                senderId = personName ?: conversationTitle
            }
        }

        // Fallback: If title or subtext looks like an Indian bank sender ID (e.g., "JM-UNIONB-T")
        if (senderId == null) {
            if (!subText.isNullOrBlank() && (subText.contains('-') || subText.length in 3..12)) {
                senderId = subText
            } else if (title.contains('-') || (title.length in 3..12 && title.all { it.isLetterOrDigit() || it == '-' })) {
                senderId = title
            }
        }

        if (effectiveTitle.isBlank() && text.isBlank()) return
        val postTime = if (activeSbn.postTime > 0) activeSbn.postTime else System.currentTimeMillis()

        serviceScope.launch {
            try {
                // Verify notifications are enabled in settings
                val isEnabled = settingsRepository.getNotificationEnabled().firstOrNull() ?: true
                if (!isEnabled) return@launch

                // 1. Ingest into MessagePipeline (captures raw, classifies, applies policy, dedups, logs debug)
                val rawMessage = com.cashbuddy.core.prob.RawMessage(
                    id = "notif_${postTime}_${(1000..9999).random()}",
                    sourceType = com.cashbuddy.core.prob.SourceType.NOTIFICATION,
                    packageName = packageName,
                    senderId = senderId,
                    title = effectiveTitle,
                    text = text,
                    timestamp = postTime
                )

                val outcome = messagePipeline.ingest(rawMessage)

                // 2. Training Data Pipeline: Record raw notification for banking apps or parsed transactions
                val source = sourceDetector.detect(packageName, if (title.isNotBlank()) "$title: $text" else text)
                if (source != com.cashbuddy.core.prob.NotificationSource.UNKNOWN || outcome !is com.cashbuddy.core.prob.PipelineOutcome.Ignored) {
                    val fullRawText = if (title.isNotBlank()) "$title: $text" else text
                    try {
                        trainingDataRepository.recordRawNotification(
                            rawText = fullRawText,
                            source = "notification",
                            sourceApp = packageName,
                            extractedAmount = when (outcome) {
                                is com.cashbuddy.core.prob.PipelineOutcome.Logged -> outcome.amount
                                is com.cashbuddy.core.prob.PipelineOutcome.PendingReview -> outcome.amount
                                else -> null
                            },
                            extractedType = null,
                            extractedMerchant = when (outcome) {
                                is com.cashbuddy.core.prob.PipelineOutcome.Logged -> outcome.merchant
                                is com.cashbuddy.core.prob.PipelineOutcome.PendingReview -> outcome.merchant
                                else -> null
                            },
                            timestamp = postTime
                        )
                    } catch (e: Throwable) {
                        Log.w(TAG, "Failed to record raw notification for training", e)
                    }
                }

                // 3. If PENDING, show local Review Alert
                if (outcome is com.cashbuddy.core.prob.PipelineOutcome.PendingReview) {
                    showReviewAlert(outcome.txId, outcome.amount, outcome.merchant)
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Failed to process notification from ${activeSbn.packageName}", e)
            }
        }
    }

    private fun parseNotification(
        packageName: String,
        title: String,
        text: String,
        postTime: Long
    ): ParsedNotificationResult? {
        try {
            val raw = RawNotification(
                packageName = packageName,
                title = title,
                text = text,
                timestamp = postTime
            )
            val parsed = notificationParser.parse(raw)
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
        } catch (e: Throwable) {
            Log.w(TAG, "NotificationParser failed for $packageName, falling back to KotlinNotificationParser", e)
        }

        // Secondary fallback
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
