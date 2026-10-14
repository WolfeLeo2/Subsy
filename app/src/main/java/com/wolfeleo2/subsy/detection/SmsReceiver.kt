package com.wolfeleo2.subsy.detection

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.wolfeleo2.subsy.data.model.TransactionSource
import com.wolfeleo2.subsy.worker.DetectionWorker

/**
 * Incoming SMS, delivered only once the user granted RECEIVE_SMS from Settings. Long messages arrive in parts, so the
 * parts are joined per sender before the pre-filter; the model work happens in [DetectionWorker], not here.
 */
class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val parts = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        parts.groupBy { it.displayOriginatingAddress }.forEach { (sender, messages) ->
            DetectionWorker.enqueueIfMoney(
                context = context,
                text = messages.joinToString("") { it.displayMessageBody.orEmpty() },
                source = TransactionSource.SMS,
                sender = sender,
                at = messages.first().timestampMillis,
            )
        }
    }
}
