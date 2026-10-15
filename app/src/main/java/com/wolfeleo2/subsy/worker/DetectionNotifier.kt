package com.wolfeleo2.subsy.worker

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.wolfeleo2.subsy.MainActivity
import com.wolfeleo2.subsy.R
import com.wolfeleo2.subsy.data.repository.SpottedCharge
import com.wolfeleo2.subsy.detection.DetectionActionReceiver
import com.wolfeleo2.subsy.detection.headline
import com.wolfeleo2.subsy.detection.question
import com.wolfeleo2.subsy.ui.util.canPostNotifications

/** A quiet notification per spotted charge, with Log it and Ignore handled by [DetectionActionReceiver]. */
class DetectionNotifier(
    private val context: Context,
) {
    private val manager = NotificationManagerCompat.from(context)

    // Guarded by canNotify(); lint can't see through it now that the permission only exists from Android 13.
    @SuppressLint("MissingPermission")
    fun show(charge: SpottedCharge) {
        if (!canNotify()) return
        createChannel()
        val id = charge.detection.id
        val res = context.resources
        val open =
            PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        val notification =
            NotificationCompat
                .Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(charge.headline(res))
                .setContentText(charge.question(res))
                .setContentIntent(open)
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
                // Track it needs the add form, so a suggestion-only charge opens the app from the notification body.
                .apply {
                    if (charge.loggable) {
                        addAction(0, res.getString(R.string.detection_action_log), action(id, DetectionActionReceiver.ACTION_LOG))
                    }
                }.addAction(0, res.getString(R.string.detection_action_ignore), action(id, DetectionActionReceiver.ACTION_IGNORE))
                .build()
        manager.notify(notificationId(id), notification)
    }

    fun cancel(detectionId: String) = manager.cancel(notificationId(detectionId))

    private fun action(
        detectionId: String,
        action: String,
    ): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            (action + detectionId).hashCode(),
            Intent(context, DetectionActionReceiver::class.java)
                .setAction(action)
                .putExtra(DetectionActionReceiver.EXTRA_DETECTION_ID, detectionId),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun canNotify(): Boolean = canPostNotifications(context)

    private fun createChannel() {
        val channel =
            NotificationChannel(CHANNEL_ID, context.getString(R.string.detection_channel_name), NotificationManager.IMPORTANCE_LOW)
                .apply { description = context.getString(R.string.detection_channel_description) }
        manager.createNotificationChannel(channel)
    }

    private fun notificationId(detectionId: String) = ("detected:$detectionId").hashCode()

    private companion object {
        const val CHANNEL_ID = "detected_charges"
    }
}
