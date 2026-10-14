package com.wolfeleo2.subsy.ui.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri

/** Opens [url] in the browser, adding https:// when the scheme is missing. Returns false if nothing could open it. */
fun openUrl(
    context: Context,
    url: String,
): Boolean {
    val trimmed = url.trim()
    if (trimmed.isEmpty()) return false
    val withScheme = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) trimmed else "https://$trimmed"
    return try {
        context.startActivity(Intent(Intent.ACTION_VIEW, withScheme.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (_: ActivityNotFoundException) {
        false
    }
}
