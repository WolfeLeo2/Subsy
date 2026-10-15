package com.wolfeleo2.subsy.ui.util

import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.wolfeleo2.subsy.detection.BankNotificationListener

fun hasSmsAccess(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED

fun hasNotificationAccess(context: Context): Boolean =
    NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

/** Android 13 made notifications a runtime permission; before it they're allowed unless switched off in Settings. */
fun canPostNotifications(context: Context): Boolean =
    (
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    ) &&
        NotificationManagerCompat.from(context).areNotificationsEnabled()

/** Android's own page for Subsy's notification access; apps can't grant it themselves. */
fun openNotificationAccess(context: Context) {
    val component = ComponentName(context, BankNotificationListener::class.java).flattenToString()
    context.startActivity(
        Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
            .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, component),
    )
}

/** Subsy's App info page, where "Allow restricted settings" lives for apps installed outside Play. */
fun openAppInfo(context: Context) {
    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
}

/**
 * Asks for SMS access. [blocked] turns true once Android has stopped showing the prompt (denied twice, or restricted):
 * from then on [launch] opens App info instead, since another request would do nothing.
 */
class SmsRequest(
    val blocked: Boolean,
    val launch: () -> Unit,
)

@Composable
fun rememberSmsRequest(onResult: (granted: Boolean) -> Unit): SmsRequest {
    val context = LocalContext.current
    var blocked by remember { mutableStateOf(false) }
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            // Only after a denial does "no rationale" mean the prompt is gone for good.
            val activity = generateSequence(context) { (it as? ContextWrapper)?.baseContext }.filterIsInstance<Activity>().firstOrNull()
            blocked =
                !granted &&
                activity != null &&
                !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.RECEIVE_SMS)
            onResult(granted)
        }
    return SmsRequest(blocked) { if (blocked) openAppInfo(context) else launcher.launch(Manifest.permission.RECEIVE_SMS) }
}
