package com.wolfeleo2.subsy.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.wolfeleo2.subsy.SubsyApplication
import com.wolfeleo2.subsy.data.repository.BillingLedger
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

/** Daily at about 08:00: sends due reminders, then settles billing dates that have arrived. */
class DailyWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as SubsyApplication
        // Reminders first: on the day of a renewal the ledger moves the date on, and the alert must still see it.
        sendReminders(app)
        BillingLedger(app.subscriptionRepository, app.paymentRecordRepository).settleDue()
        return Result.success()
    }

    private suspend fun sendReminders(app: SubsyApplication) {
        val notifier = ReminderNotifier(applicationContext)
        if (!notifier.canNotify) return
        val sent = pruneSent(app.settingsRepository.remindersSent())
        val due = dueReminders(app.subscriptionRepository.getAllSubscriptions().first(), sent)
        due.forEach(notifier::show)
        app.settingsRepository.setRemindersSent(sent + due.map { it.key })
    }

    companion object {
        private const val WORK_NAME = "daily_ledger_and_reminders"
        private val RUN_AT = LocalTime.of(8, 0)

        fun schedule(context: Context) {
            val now = LocalDateTime.now()
            var first = now.toLocalDate().atTime(RUN_AT)
            if (!first.isAfter(now)) first = first.plusDays(1)
            val request =
                PeriodicWorkRequestBuilder<DailyWorker>(1, TimeUnit.DAYS)
                    .setInitialDelay(Duration.between(now, first))
                    .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
