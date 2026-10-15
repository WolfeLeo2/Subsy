package com.wolfeleo2.subsy.ui.components

import androidx.compose.material3.RichTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wolfeleo2.subsy.R
import com.wolfeleo2.subsy.SubsyApplication
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val HINT_DELAY_MS = 600L

/**
 * Wraps [content] and, the first time [enabled] is true, shows [text] in a rich tooltip anchored to it. The hint is
 * remembered by [id] in settings once it has been shown, so it never appears again. For gestures that can't be seen.
 */
@Composable
fun OneTimeHint(
    id: String,
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val settings = (LocalContext.current.applicationContext as? SubsyApplication)?.settingsRepository
    // Null until the stored set loads, so a hint that was already seen never flashes.
    val seen by remember(settings) { settings?.settings?.map { it.hintsSeen } ?: emptyFlow() }.collectAsStateWithLifecycle(null)
    // Once started, the box stays composed through dismissal so the anchor keeps its state.
    var started by remember { mutableStateOf(false) }
    if (!started && !(enabled && seen?.contains(id) == false)) {
        content()
        return
    }
    val state = rememberTooltipState(isPersistent = true)
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        started = true
        delay(HINT_DELAY_MS)
        // show() returns once the tooltip is dismissed; leaving the screen before it appears doesn't use the hint up.
        try {
            state.show()
        } finally {
            withContext(NonCancellable) { settings?.markHintSeen(id) }
        }
    }
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = {
            RichTooltip(
                action = { TextButton(onClick = { scope.launch { state.dismiss() } }) { Text(stringResource(R.string.hint_dismiss)) } },
            ) { Text(text) }
        },
        state = state,
        modifier = modifier,
        content = content,
    )
}
