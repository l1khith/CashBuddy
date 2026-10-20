package com.cashbuddy.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.cashbuddy.domain.model.TransactionStatus
import com.cashbuddy.domain.repository.TransactionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class AnomalyActionReceiver : BroadcastReceiver(), KoinComponent {

    private val transactionRepository: TransactionRepository by inject()

    companion object {
        const val ACTION_ITS_MINE = "com.cashbuddy.ACTION_ANOMALY_ITS_MINE"
        const val EXTRA_TRANSACTION_ID = "EXTRA_TRANSACTION_ID"
        const val EXTRA_NOTIFICATION_ID = "EXTRA_NOTIFICATION_ID"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)
        if (notificationId != -1) {
            NotificationManagerCompat.from(context).cancel(notificationId)
        }

        if (intent.action == ACTION_ITS_MINE) {
            val transactionId = intent.getLongExtra(EXTRA_TRANSACTION_ID, -1L)
            if (transactionId > 0L) {
                val pendingResult = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        transactionRepository.updateStatus(transactionId, TransactionStatus.CONFIRMED)
                        context.sendBroadcast(
                            Intent("com.cashbuddy.ACTION_TRANSACTIONS_UPDATED").setPackage(context.packageName)
                        )
                    } catch (e: Throwable) {
                        android.util.Log.e("AnomalyActionReceiver", "Failed to confirm anomalous transaction $transactionId", e)
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
        }
    }
}
