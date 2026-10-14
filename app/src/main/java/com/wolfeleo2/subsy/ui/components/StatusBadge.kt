package com.wolfeleo2.subsy.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.wolfeleo2.subsy.R
import com.wolfeleo2.subsy.data.model.SubscriptionStatus
import com.wolfeleo2.subsy.ui.theme.Spacing

/**
 * The single status marker in the app. Colors come from scheme roles: trials are tertiary (amber),
 * paused and cancelled are neutral. ACTIVE needs no badge, so it renders nothing.
 */
@Composable
fun StatusBadge(
    status: SubscriptionStatus,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val (label, container, content) =
        when (status) {
            SubscriptionStatus.ACTIVE -> return
            SubscriptionStatus.TRIAL -> Triple(R.string.status_trial, colors.tertiaryContainer, colors.onTertiaryContainer)
            SubscriptionStatus.PAUSED -> Triple(R.string.status_paused, colors.surfaceContainerHighest, colors.onSurfaceVariant)
            SubscriptionStatus.CANCELLED -> Triple(R.string.status_cancelled, colors.surfaceContainerHighest, colors.onSurfaceVariant)
        }
    Badge(stringResource(label), container, content, modifier)
}

@Composable
fun Badge(
    text: String,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = contentColor,
        maxLines = 1,
        modifier =
            modifier
                .clip(MaterialTheme.shapes.small)
                .background(containerColor)
                .padding(horizontal = Spacing.s, vertical = Spacing.xs / 2),
    )
}
