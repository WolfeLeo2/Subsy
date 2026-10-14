package com.wolfeleo2.subsy.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.wolfeleo2.subsy.ui.theme.Spacing

/**
 * Which color role a tile uses. Primary: income and within budget. Secondary: totals and upcoming charges.
 * Trial (tertiary): trials and spending. Urgent (error): over budget, overdue, a trial about to end.
 */
enum class StatTone { NEUTRAL, PRIMARY, SECONDARY, TRIAL, URGENT }

/** A pill with a cookie-shaped icon, a small label, one amount, and an optional line under it. */
@Composable
fun StatTile(
    icon: ImageVector,
    label: String,
    amount: Double,
    currency: String,
    modifier: Modifier = Modifier,
    tone: StatTone = StatTone.NEUTRAL,
    suffix: String? = null,
    supporting: String? = null,
) {
    val (container, content, badge, onBadge) = tone.colors()
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = container,
        contentColor = content,
    ) {
        Row(
            modifier = Modifier.padding(Spacing.m),
            horizontalArrangement = Arrangement.spacedBy(Spacing.l),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(Spacing.xxl + Spacing.l).background(badge, MaterialShapes.Cookie12Sided.toShape()),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = onBadge)
            }
            Column {
                Text(label, style = MaterialTheme.typography.labelLarge)
                MoneyText(amount, currency, style = MaterialTheme.typography.titleLargeEmphasized, suffix = suffix)
                if (supporting != null) Text(supporting, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

/** Container, content, badge and badge-content colors for a [StatTone]. */
@Composable
private fun StatTone.colors(): List<Color> {
    val scheme = MaterialTheme.colorScheme
    return when (this) {
        StatTone.NEUTRAL -> listOf(scheme.surfaceContainer, scheme.onSurface, scheme.secondaryContainer, scheme.onSecondaryContainer)
        StatTone.PRIMARY -> listOf(scheme.primaryContainer, scheme.onPrimaryContainer, scheme.primary, scheme.onPrimary)
        StatTone.SECONDARY -> listOf(scheme.secondaryContainer, scheme.onSecondaryContainer, scheme.secondary, scheme.onSecondary)
        StatTone.TRIAL -> listOf(scheme.tertiaryContainer, scheme.onTertiaryContainer, scheme.tertiary, scheme.onTertiary)
        StatTone.URGENT -> listOf(scheme.errorContainer, scheme.onErrorContainer, scheme.error, scheme.onError)
    }
}

/**
 * The compact form of [StatTile] for the column beside the ring, after a fitness app's stat pills: a colored
 * card with the icon in a solid circle, then the label and the amount, which shrinks to fit rather than wrap.
 * A [progress] (0 to 1) fills the card from the left in a deeper shade, like those pills' goal bars; without
 * one the card stays flat.
 */
@Composable
fun StatPill(
    icon: ImageVector,
    label: String,
    amount: Double,
    currency: String,
    modifier: Modifier = Modifier,
    tone: StatTone = StatTone.NEUTRAL,
    progress: Float? = null,
) {
    val (container, content, strong, onStrong) = tone.colors()
    val fill by animateFloatAsState(progress?.coerceIn(0f, 1f) ?: 0f, MaterialTheme.motionScheme.slowSpatialSpec(), label = "pill fill")
    Surface(modifier = modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, color = container, contentColor = content) {
        Box(Modifier.height(IntrinsicSize.Min)) {
            if (progress != null) {
                // A step toward the content color keeps the text readable on the fill in light and dark.
                Box(Modifier.matchParentSize()) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(fill).background(lerp(container, content, PROGRESS_SHADE)))
                }
            }
            Row(
                modifier = Modifier.padding(Spacing.m),
                horizontalArrangement = Arrangement.spacedBy(Spacing.m),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(Spacing.xxl + Spacing.s).background(strong, MaterialShapes.Puffy.toShape()),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(icon, contentDescription = null, tint = onStrong, modifier = Modifier.size(Spacing.l + Spacing.xs))
                }
                Column(Modifier.weight(1f)) {
                    Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    MoneyText(
                        amount = amount,
                        currency = currency,
                        style = MaterialTheme.typography.titleLargeEmphasized,
                        autoSize =
                            TextAutoSize.StepBased(
                                minFontSize = 11.sp,
                                maxFontSize = MaterialTheme.typography.titleLargeEmphasized.fontSize,
                            ),
                    )
                }
            }
        }
    }
}

private const val PROGRESS_SHADE = 0.14f
