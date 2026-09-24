package com.cashbuddy.receiver

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony
import androidx.core.app.NotificationCompat
import com.cashbuddy.MainActivity
import com.cashbuddy.core.isDuplicateTransaction
import com.cashbuddy.core.isTrustedSender
import com.cashbuddy.core.learnTrustedSender
import com.cashbuddy.core.parseSms
import com.cashbuddy.domain.model.Transaction
import com.cashbuddy.domain.model.TransactionStatus
import com.cashbuddy.domain.model.TransactionType
import com.cashbuddy.domain.repository.AccountRepository
import com.cashbuddy.domain.repository.CategoryRepository
import com.cashbuddy.domain.repository.SettingsRepository
import com.cashbuddy.domain.repository.TrainingDataRepository
import com.cashbuddy.domain.repository.TransactionRepository
import com.cashbuddy.domain.repository.TrustedSenderRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Layer 1: SMS Broadcast Receiver for Indian Bank & UPI transaction SMS.
 * Processes SMS in real-time, extracts amounts (excluding balances), identifies merchants,
 * learns trusted senders dynamically, and records to the Unified Ledger.
 */
class SmsReceiver : BroadcastReceiver(), KoinComponent {

    private val transactionRepository: TransactionRepository by inject()
    private val categoryRepository: CategoryRepository by inject()
    private val accountRepository: AccountRepository by inject()
    private val settingsRepository: SettingsRepository by inject()
    private val trainingDataRepository: TrainingDataRepository by inject()
    private val trustedSenderRepository: TrustedSenderRepository by inject()

    companion object {
        private const val TAG = "SmsReceiver"
        private const val CHANNEL_ID = "cashbuddy_sms_channel"
        private const val CHANNEL_NAME = "Transaction Alerts"
        private const val DEDUP_WINDOW_SECS = 300L // 5 minutes
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        if (messages.isEmpty()) return

        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Group multi-part messages by originating address
                val messagesBySender = messages.groupBy { it.originatingAddress ?: "Unknown" }

                for ((sender, parts) in messagesBySender) {
                    val fullBody = parts.joinToString(separator = "") { it.messageBody ?: "" }
                    if (fullBody.isBlank()) continue

                    val messageTimestamp = parts.firstOrNull()?.timestampMillis ?: System.currentTimeMillis()

                    processSms(context, sender, fullBody, messageTimestamp)
                }
            } catch (_: Throwable) {
                // Safe failover
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun processSms(context: Context, sender: String, body: String, timestamp: Long) {
        // 1. Check if sender is trusted or TRAI format
        val isSenderKnown = isTrustedSender(sender) || trustedSenderRepository.isTrusted(sender)
        val cleanSender = sender.trim()

        // 2. Parse SMS with Rust Core Parser
        val parsed = parseSms(cleanSender, body) ?: return

        // 3. Dynamic Sender Learning: Learn new bank sender ID locally
        if (!isSenderKnown) {
            learnTrustedSender(cleanSender)
            trustedSenderRepository.addSender(cleanSender, parsed.bank, timestamp)
        }

        // 4. Record raw training data for transparency
        try {
            trainingDataRepository.recordRawNotification(
                rawText = "$cleanSender: $body",
                source = "sms",
                sourceApp = cleanSender,
                extractedAmount = parsed.amount,
                extractedType = parsed.transactionType.name,
                extractedMerchant = parsed.merchant,
                timestamp = timestamp
            )
        } catch (_: Throwable) {
        }

        // 5. Deduplication check (5-minute window)
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
        if (isDuplicate) return

        // 6. Resolve Category
        val allCategories = categoryRepository.getAll().firstOrNull() ?: emptyList()
        val normalizedCategory = parsed.category.lowercase()
        val matchedCategory = allCategories.find {
            it.name.equals(parsed.category, ignoreCase = true)
        } ?: allCategories.find {
            it.name.lowercase().startsWith(normalizedCategory) ||
            normalizedCategory.startsWith(it.name.lowercase().substringBefore(" "))
        } ?: allCategories.firstOrNull()
        val categoryId = matchedCategory?.id ?: 1L

        // 7. Resolve Account
        val allAccounts = accountRepository.getAll().firstOrNull() ?: emptyList()
        val accountLast4 = parsed.accountLast4
        val bank = parsed.bank
        val matchedAccount = if (!accountLast4.isNullOrBlank()) {
            val last4 = accountLast4.takeLast(4)
            allAccounts.find { it.number?.endsWith(last4) == true }
        } else if (!bank.isNullOrBlank()) {
            allAccounts.find { it.name.contains(bank, ignoreCase = true) }
        } else {
            null
        } ?: allAccounts.firstOrNull()
        val accountId = matchedAccount?.id ?: 1L

        // 8. Auto-confirmation policy
        val autoConfirmThreshold = settingsRepository.getAutoConfirmThreshold().firstOrNull() ?: 10000.0
        val minConfidence = settingsRepository.getMinConfidenceThreshold().firstOrNull() ?: 0.85f

        val txType = when (parsed.transactionType) {
            com.cashbuddy.core.TransactionType.DEBIT -> TransactionType.DEBIT
            com.cashbuddy.core.TransactionType.CREDIT -> TransactionType.CREDIT
        }

        val status = if (parsed.confidence >= minConfidence && parsed.amount < autoConfirmThreshold) {
            TransactionStatus.CONFIRMED
        } else {
            TransactionStatus.PENDING
        }

        // 9. Persist into Unified Ledger
        val transaction = Transaction(
            id = 0L,
            accountId = accountId,
            categoryId = categoryId,
            amount = parsed.amount,
            type = txType,
            status = status,
            rawText = parsed.rawText,
            sourceApp = "SMS: $cleanSender",
            merchant = parsed.merchant,
            confidence = parsed.confidence,
            timestamp = timestamp,
            createdAt = timestamp,
            updatedAt = timestamp
        )

        val insertedId = transactionRepository.insert(transaction)

        // 10. Local notification if pending review
        if (status == TransactionStatus.PENDING) {
            showReviewNotification(context, insertedId, parsed.amount, parsed.merchant)
        }
    }

    private fun showReviewNotification(context: Context, txId: Long, amount: Double, merchant: String) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            )
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("route", "review")
            putExtra("tx_id", txId)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            txId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("New Transaction: ₹$amount")
            .setContentText("Transaction at $merchant requires review.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(txId.toInt(), notification)
    }
}
