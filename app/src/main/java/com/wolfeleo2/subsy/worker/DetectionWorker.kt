package com.wolfeleo2.subsy.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.wolfeleo2.subsy.SubsyApplication
import com.wolfeleo2.subsy.data.model.DetectedTransaction
import com.wolfeleo2.subsy.data.model.TransactionSource
import com.wolfeleo2.subsy.detection.ChargeGuards
import com.wolfeleo2.subsy.detection.ChargeModel
import com.wolfeleo2.subsy.detection.looksLikeMoney
import kotlinx.coroutines.flow.first
import java.util.UUID

/**
 * Reads one captured message with the on-device model and stores it if the guards accept it and it isn't a duplicate.
 * Jobs run one at a time (a single appended chain), so an SMS and the bank app's notification for the same charge
 * can't both pass the duplicate check before either is stored.
 */
class DetectionWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as SubsyApplication
        val settings = app.settingsRepository.settings.first()
        // The user may have opted out between capture and now.
        if (!settings.detectionEnabled || !app.modelRepository.isReady()) return Result.success()
        val text = inputData.getString(KEY_TEXT) ?: return Result.success()
        val at = inputData.getLong(KEY_AT, System.currentTimeMillis())
        // Never fail: a failed job would cancel every message queued behind it in the chain.
        return try {
            val reading = ChargeModel(app.modelRepository.dir).use { it.read(text) }
            val charge = ChargeGuards.accept(reading, text, settings.accountHolderName) ?: return Result.success()
            val detector = app.detectorRepository
            if (!detector.isDuplicate(charge.reference, charge.amount, charge.currency, at)) {
                val detection =
                    DetectedTransaction(
                        id = UUID.randomUUID().toString(),
                        merchantName = charge.merchant.orEmpty(),
                        amount = charge.amount,
                        rawMessage = text,
                        source = TransactionSource.fromString(inputData.getString(KEY_SOURCE)),
                        detectedAt = at,
                        currency = charge.currency,
                        kind = charge.kind,
                        reference = charge.reference,
                        categoryKey = charge.categoryKey,
                        sender = inputData.getString(KEY_SENDER),
                    )
                detector.recordDetectedTransaction(detection)
                // Only what the user could act on in the app gets a notification (no transfers, and no unmatched
                // charges in Subscriptions mode).
                app.detectionInbox.spot(detection, settings.appMode)?.let { DetectionNotifier(applicationContext).show(it) }
            }
            Result.success()
        } catch (e: Exception) {
            Log.w(TAG, "Could not read a message", e)
            Result.success()
        }
    }

    companion object {
        private const val TAG = "DetectionWorker"
        private const val WORK_NAME = "charge-detection"
        private const val KEY_TEXT = "text"
        private const val KEY_SOURCE = "source"
        private const val KEY_SENDER = "sender"
        private const val KEY_AT = "at"

        /**
         * Queues [text] for reading if it passes the pre-filter and the model is downloaded. Called from the SMS receiver
         * and the notification listener; a message that fails here is never stored anywhere.
         */
        fun enqueueIfMoney(
            context: Context,
            text: String,
            source: TransactionSource,
            sender: String?,
            at: Long,
        ) {
            val app = context.applicationContext as SubsyApplication
            if (!looksLikeMoney(text) || !app.modelRepository.isReady()) return
            val request =
                OneTimeWorkRequestBuilder<DetectionWorker>()
                    .setInputData(workDataOf(KEY_TEXT to text, KEY_SOURCE to source.name, KEY_SENDER to sender, KEY_AT to at))
                    .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                    .build()
            WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
        }
    }
}
