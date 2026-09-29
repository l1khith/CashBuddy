// NO-NETWORK
package com.cashbuddy.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.cashbuddy.core.prob.MessagePipeline
import com.cashbuddy.core.prob.RawMessage
import com.cashbuddy.core.prob.SourceType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * In-device SMS Receiver for CashBuddy debug pipeline.
 * Follows strict offline rules: zero network permissions.
 * In production builds, CashBuddy uses NotificationListenerService exclusively.
 */
class SmsReceiver : BroadcastReceiver(), KoinComponent {

    private val pipeline: MessagePipeline by inject()
    private val scope = CoroutineScope(Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
            for (msg in messages) {
                val sender = msg.originatingAddress
                val body = msg.messageBody ?: ""
                val timestamp = msg.timestampMillis
                val raw = RawMessage(
                    id = "sms_${timestamp}_${(1000..9999).random()}",
                    sourceType = SourceType.SMS,
                    packageName = null,
                    senderId = sender,
                    title = "",
                    text = body,
                    timestamp = timestamp
                )
                scope.launch {
                    try {
                        pipeline.ingest(raw)
                    } catch (_: Throwable) { }
                }
            }
        }
    }
}
