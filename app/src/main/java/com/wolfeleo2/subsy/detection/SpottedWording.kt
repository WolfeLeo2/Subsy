package com.wolfeleo2.subsy.detection

import android.content.res.Resources
import com.wolfeleo2.subsy.R
import com.wolfeleo2.subsy.data.model.DetectedKind
import com.wolfeleo2.subsy.data.repository.SpottedCharge
import com.wolfeleo2.subsy.ui.util.FormattingUtils

/** "Spotted KES 1,100.00 to Netflix", shared by the card and the notification so they always read the same. */
fun SpottedCharge.headline(res: Resources): String {
    val amount = FormattingUtils.formatCurrency(detection.amount, detection.currency)
    val who = subscription?.name ?: suggestion?.name ?: detection.merchantName.takeIf { it.isNotBlank() }
    return when {
        detection.kind == DetectedKind.INCOME && who != null -> res.getString(R.string.detection_received, amount, who)
        detection.kind == DetectedKind.INCOME -> res.getString(R.string.detection_received_no_merchant, amount)
        who != null -> res.getString(R.string.detection_spent, amount, who)
        else -> res.getString(R.string.detection_spent_no_merchant, amount)
    }
}

/** What the buttons will do: "Log it as your Netflix payment?", or "Looks like a subscription. Track Showmax?". */
fun SpottedCharge.question(res: Resources): String =
    when {
        subscription != null -> res.getString(R.string.detection_log_subscription, subscription.name)
        suggestion != null -> res.getString(R.string.detection_track_question, suggestion.name)
        detection.kind == DetectedKind.INCOME -> res.getString(R.string.detection_log_income)
        else -> res.getString(R.string.detection_log_expense)
    }
