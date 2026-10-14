package com.wolfeleo2.subsy.ui.analytics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.wolfeleo2.subsy.ui.components.StatPill
import com.wolfeleo2.subsy.ui.components.StatTile
import com.wolfeleo2.subsy.ui.components.StatTone
import com.wolfeleo2.subsy.ui.theme.Spacing

data class HeroStat(
    val icon: ImageVector,
    val label: String,
    val amount: Double,
    val currency: String,
    val tone: StatTone = StatTone.NEUTRAL,
    /** Shown only when the hero stacks and there is room for a full tile. */
    val supporting: String? = null,
    val suffix: String? = null,
    /** 0 to 1, drawn as the pill's fill; null for figures with nothing to measure against. */
    val progress: Float? = null,
)

/** Below this width, or above this font scale, the pills no longer fit beside the ring and the hero stacks. */
private val MinSideBySideWidth = 320.dp
private const val MAX_SIDE_BY_SIDE_FONT_SCALE = 1.3f
private const val RING_WEIGHT = 1.15f

/**
 * The Spending hero, after a fitness app's home: the ring on the left and up to three stat pills stacked on
 * its right. It stacks into a full-width ring over full tiles on narrow screens or with large text.
 */
@Composable
fun RingHero(
    stats: List<HeroStat>,
    modifier: Modifier = Modifier,
    ring: @Composable (Modifier) -> Unit,
) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val fontScale = LocalDensity.current.fontScale
        if (stats.isNotEmpty() && maxWidth >= MinSideBySideWidth && fontScale <= MAX_SIDE_BY_SIDE_FONT_SCALE) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.m), verticalAlignment = Alignment.CenterVertically) {
                ring(Modifier.weight(RING_WEIGHT))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    stats.forEach { StatPill(it.icon, it.label, it.amount, it.currency, tone = it.tone, progress = it.progress) }
                }
            }
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                ring(Modifier.fillMaxWidth(RING_WIDTH))
                stats.forEach {
                    StatTile(
                        it.icon,
                        it.label,
                        it.amount,
                        it.currency,
                        tone = it.tone,
                        suffix = it.suffix,
                        supporting = it.supporting,
                    )
                }
            }
        }
    }
}
