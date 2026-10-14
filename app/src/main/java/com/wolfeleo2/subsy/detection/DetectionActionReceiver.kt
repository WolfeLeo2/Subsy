package com.wolfeleo2.subsy.detection

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.wolfeleo2.subsy.SubsyApplication
import com.wolfeleo2.subsy.worker.DetectionNotifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Log it / Ignore from a detection notification. Not exported: only Subsy's own pending intents reach it. */
class DetectionActionReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val id = intent.getStringExtra(EXTRA_DETECTION_ID) ?: return
        val app = context.applicationContext as SubsyApplication
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (intent.action) {
                    ACTION_LOG ->
                        app.detectionInbox.logById(
                            id,
                            app.settingsRepository.settings
                                .first()
                                .appMode,
                        )
                    ACTION_IGNORE -> app.detectionInbox.ignore(id)
                }
                DetectionNotifier(context).cancel(id)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_LOG = "com.wolfeleo2.subsy.detection.LOG"
        const val ACTION_IGNORE = "com.wolfeleo2.subsy.detection.IGNORE"
        const val EXTRA_DETECTION_ID = "detection_id"
    }
}
