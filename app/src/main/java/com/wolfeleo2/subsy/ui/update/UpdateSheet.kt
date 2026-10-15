package com.wolfeleo2.subsy.ui.update

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.wolfeleo2.subsy.R
import com.wolfeleo2.subsy.ui.theme.Spacing

/** The update offer: release notes, download progress and the install button. State lives in [UpdateViewModel]. */
@Composable
fun UpdateSheet(
    state: UpdateUiState,
    viewModel: UpdateViewModel,
) {
    val update = state.available ?: return
    val context = LocalContext.current
    // Back from the "allow installs" screen: carry on with the file already downloaded.
    LifecycleResumeEffect(Unit) {
        viewModel.retryPendingInstall()
        onPauseOrDispose {}
    }
    ModalBottomSheet(
        onDismissRequest = viewModel::closeSheet,
        sheetState =
            rememberBottomSheetState(
                initialValue = SheetValue.Hidden,
                enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
            ),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.xl).padding(bottom = Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.l),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(
                    stringResource(R.string.update_title),
                    style = MaterialTheme.typography.headlineSmallEmphasized,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    stringResource(R.string.update_body, update.version),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Text(
                        stringResource(R.string.update_notes_title),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    // GitHub's notes are Markdown; shown as plain text rather than pulling in a renderer.
                    Text(
                        update.notes.ifBlank { stringResource(R.string.update_notes_empty) },
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.heightIn(max = 200.dp).verticalScroll(rememberScrollState()),
                    )
                }
            }
            if (state.downloading) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    if (state.totalBytes > 0) {
                        LinearWavyProgressIndicator(progress = {
                            (state.doneBytes.toFloat() / state.totalBytes).coerceIn(0f, 1f)
                        }, modifier = Modifier.fillMaxWidth())
                        Text(
                            stringResource(
                                R.string.update_downloading,
                                Formatter.formatShortFileSize(context, state.doneBytes),
                                Formatter.formatShortFileSize(context, state.totalBytes),
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
            }
            state.error?.let {
                Text(
                    stringResource(R.string.update_failed, it),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.s, Alignment.End)) {
                if (state.downloading) {
                    TextButton(onClick = viewModel::cancelDownload) { Text(stringResource(R.string.action_cancel)) }
                } else {
                    TextButton(onClick = viewModel::skipVersion) { Text(stringResource(R.string.update_skip)) }
                    TextButton(onClick = viewModel::closeSheet) { Text(stringResource(R.string.update_later)) }
                }
                Button(onClick = viewModel::startDownload, enabled = !state.downloading) { Text(stringResource(R.string.update_install)) }
            }
        }
    }
}
