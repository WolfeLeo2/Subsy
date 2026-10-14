package com.wolfeleo2.subsy.ui.onboarding

import android.Manifest
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import com.wolfeleo2.subsy.R
import com.wolfeleo2.subsy.detection.DetectorRelease
import com.wolfeleo2.subsy.ui.theme.AppTheme
import com.wolfeleo2.subsy.ui.theme.Spacing

/**
 * Shown once, after the mode chooser (and once to existing users): offers charge detection, says what it downloads
 * and that messages stay on the phone. Turning it on asks for SMS access right away; bank app notification access is
 * granted from Settings, since it lives in Android's own settings.
 */
@Composable
fun DetectionIntroScreen(
    onTurnOn: () -> Unit,
    onNotNow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Whatever the user answers, detection is on; without SMS access it still reads bank app notifications.
    val smsPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { onTurnOn() }
    val size = Formatter.formatShortFileSize(LocalContext.current, DetectorRelease.totalBytes)
    Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(
            modifier =
                Modifier
                    .safeDrawingPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.xl, vertical = Spacing.xxl),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            Text(
                text = stringResource(R.string.detection_intro_title),
                style = MaterialTheme.typography.displaySmallEmphasized,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = stringResource(R.string.detection_intro_body),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = Spacing.l),
            )
            Point(Icons.Rounded.Sms, stringResource(R.string.detection_intro_reads))
            Point(Icons.Rounded.Download, stringResource(R.string.detection_intro_download, size))
            Point(Icons.Rounded.Lock, stringResource(R.string.detection_intro_private))
            Text(
                text = stringResource(R.string.detection_intro_restricted),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.s),
            )
            Spacer(Modifier.size(Spacing.l))
            Button(onClick = { smsPermission.launch(Manifest.permission.RECEIVE_SMS) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.detection_intro_turn_on))
            }
            TextButton(onClick = onNotNow, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.detection_intro_not_now))
            }
        }
    }
}

@Composable
private fun Point(
    icon: ImageVector,
    text: String,
) {
    val scheme = MaterialTheme.colorScheme
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.l), verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier.size(Spacing.xxl + Spacing.s).background(scheme.tertiaryContainer, MaterialShapes.Cookie9Sided.toShape()),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = scheme.onTertiaryContainer)
        }
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
}

@Preview(showBackground = true)
@Composable
private fun DetectionIntroScreenPreview() {
    AppTheme { DetectionIntroScreen(onTurnOn = {}, onNotNow = {}) }
}
