package com.wolfeleo2.subsy.detection

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.wolfeleo2.subsy.data.model.TransactionSource
import com.wolfeleo2.subsy.worker.DetectionWorker

/**
 * Notifications from every app, bound by the system once the user allows notification access. Only the body text is
 * read (the model was trained on message bodies). An SMS shown by the messaging app arrives here too; the reference
 * check in [DetectionWorker] drops the copy.
 */
class BankNotificationListener : NotificationListenerService() {
    /** Apps re-post the same notification when it updates; one read per key and text is enough. */
    private val lastSeen = HashMap<String, String>()

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val notification = sbn.notification
        if (sbn.packageName == packageName || sbn.isOngoing) return
        if (notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        val extras = notification.extras
        val text =
            (extras.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: extras.getCharSequence(Notification.EXTRA_TEXT))
                ?.toString()
                ?.takeIf { it.isNotBlank() }
                ?: return
        if (lastSeen.put(sbn.key, text) == text) return
        if (lastSeen.size > MAX_REMEMBERED) lastSeen.clear()
        DetectionWorker.enqueueIfMoney(this, text, TransactionSource.NOTIFICATION, sbn.packageName, sbn.postTime)
    }

    private companion object {
        const val MAX_REMEMBERED = 200
    }
}
