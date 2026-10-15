package com.wolfeleo2.subsy.worker

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import com.wolfeleo2.subsy.MainActivity
import com.wolfeleo2.subsy.R
import com.wolfeleo2.subsy.ui.util.canPostNotifications

class ReminderNotifier(
    private val context: Context,
) {
    private val manager = NotificationManagerCompat.from(context)

    val canNotify: Boolean
        get() = canPostNotifications(context)

    // Callers check canNotify first; lint can't see through it now that the permission only exists from Android 13.
    @SuppressLint("MissingPermission")
    fun show(reminder: Reminder) {
        createChannel()
        val sub = reminder.subscription
        val res = context.resources
        val text =
            when {
                reminder.daysLeft >= 2 && reminder.isTrial ->
                    res.getQuantityString(
                        R.plurals.reminder_trial_days,
                        reminder.daysLeft,
                        reminder.daysLeft,
                    )
                reminder.daysLeft >= 2 -> res.getQuantityString(R.plurals.reminder_renews_days, reminder.daysLeft, reminder.daysLeft)
                reminder.daysLeft == 1 && reminder.isTrial -> res.getString(R.string.reminder_trial_tomorrow)
                reminder.daysLeft == 1 -> res.getString(R.string.reminder_renews_tomorrow)
                reminder.isTrial -> res.getString(R.string.reminder_trial_today)
                else -> res.getString(R.string.reminder_renews_today)
            }
        val open =
            PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        val builder =
            NotificationCompat
                .Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(sub.name)
                .setContentText(text)
                .setContentIntent(open)
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
        sub.cancellationUrl?.takeIf { it.startsWith("http://") || it.startsWith("https://") }?.let { url ->
            val jump =
                PendingIntent.getActivity(
                    context,
                    reminder.key.hashCode(),
                    Intent(Intent.ACTION_VIEW, url.toUri()),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
            builder.addAction(0, res.getString(R.string.reminder_action_cancel), jump)
        }
        manager.notify(reminder.key.hashCode(), builder.build())
    }

    private fun createChannel() {
        val channel =
            NotificationChannel(CHANNEL_ID, context.getString(R.string.reminder_channel_name), NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = context.getString(R.string.reminder_channel_description) }
        manager.createNotificationChannel(channel)
    }

    private companion object {
        const val CHANNEL_ID = "renewal_reminders"
    }
}
