package com.wolfeleo2.subsy.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wolfeleo2.subsy.R
import com.wolfeleo2.subsy.data.model.Subscription
import com.wolfeleo2.subsy.ui.theme.Spacing
import com.wolfeleo2.subsy.ui.theme.tabular
import com.wolfeleo2.subsy.ui.util.FormattingUtils

/** How the hero's totals treat subscriptions billed in another currency. */
data class CurrencyNote(
    val convertedCount: Int = 0,
    val unconvertedCount: Int = 0,
    val ratesUpdatedAtEpochSeconds: Long? = null,
)

private val LogoSize = 36.dp
private val LogoRing = 2.dp
private val LogoStep = 24.dp

/**
 * The top of Home, after subscription trackers like Rocket Money and Bobby: one bold card answering how much,
 * on what, and what's next. The monthly total (rolling up or down as it changes) and the yearly figure, the
 * costliest services as overlapping logos with the active and trial counts, and the next charge as a pill
 * that opens that subscription. It is the one place the primary container is used on Home.
 */
@Composable
fun SubscriptionsHero(
    monthly: Double,
    yearly: Double,
    currency: String,
    logos: List<Subscription>,
    moreCount: Int,
    activeCount: Int,
    trialCount: Int,
    trialCost: Double,
    nextCharge: Subscription?,
    onOpenNext: (Subscription) -> Unit,
    currencyNote: CurrencyNote,
    modifier: Modifier = Modifier,
) {
    val motion = MaterialTheme.motionScheme
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = scheme.primaryContainer,
        contentColor = scheme.onPrimaryContainer,
    ) {
        Column(modifier = Modifier.padding(Spacing.xl), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(
                text = stringResource(R.string.hero_label),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() },
            )
            // Rolls up when spend grows and down when it shrinks.
            AnimatedContent(
                targetState = monthly,
                transitionSpec = {
                    val direction = if (targetState > initialState) 1 else -1
                    (slideInVertically(motion.defaultSpatialSpec()) { it * direction } + fadeIn(motion.defaultEffectsSpec()))
                        .togetherWith(
                            slideOutVertically(motion.defaultSpatialSpec()) { -it * direction } + fadeOut(motion.defaultEffectsSpec()),
                        )
                },
                label = "monthly spend",
            ) { amount ->
                MoneyText(
                    amount = amount,
                    currency = currency,
                    style = MaterialTheme.typography.displayMediumEmphasized,
                    autoSize =
                        TextAutoSize.StepBased(
                            minFontSize = 28.sp,
                            maxFontSize = MaterialTheme.typography.displayMediumEmphasized.fontSize,
                        ),
                )
            }
            Text(
                text = stringResource(R.string.hero_yearly, FormattingUtils.formatCurrency(yearly, currency)),
                style = MaterialTheme.typography.bodyLarge.tabular,
            )
            if (logos.isNotEmpty()) {
                Row(Modifier.padding(top = Spacing.l), verticalAlignment = Alignment.CenterVertically) {
                    LogoStack(logos, moreCount)
                    Column(Modifier.padding(start = Spacing.m)) {
                        val active = pluralStringResource(R.plurals.hero_active, activeCount, activeCount)
                        Text(
                            text =
                                if (trialCount > 0) {
                                    stringResource(
                                        R.string.hero_counts,
                                        active,
                                        pluralStringResource(R.plurals.hero_trials, trialCount, trialCount),
                                    )
                                } else {
                                    active
                                },
                            style = MaterialTheme.typography.labelLarge,
                        )
                        if (trialCount > 0) {
                            Text(
                                text =
                                    pluralStringResource(
                                        R.plurals.hero_trials_convert,
                                        trialCount,
                                        trialCount,
                                        FormattingUtils.formatCurrency(trialCost, currency),
                                    ),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
            nextCharge?.let { NextChargePill(it, onClick = { onOpenNext(it) }, modifier = Modifier.padding(top = Spacing.m)) }
            CurrencyNoteLines(currencyNote)
        }
    }
}

/** Overlapping circular logos, each edged in the card color so the overlaps read; then "+3" for the rest. */
@Composable
private fun LogoStack(
    logos: List<Subscription>,
    moreCount: Int,
) {
    val scheme = MaterialTheme.colorScheme
    val count = logos.size + if (moreCount > 0) 1 else 0
    // Decorative: the counts beside the stack say the same in words.
    Box(Modifier.width(LogoStep * (count - 1) + LogoSize).clearAndSetSemantics {}) {
        logos.forEachIndexed { index, sub ->
            ServiceIconView(
                name = sub.name,
                colorHex = sub.colorHex,
                cancellationUrl = sub.cancellationUrl,
                size = LogoSize,
                shape = CircleShape,
                // Drawn over each logo's edge in the card color, so overlapping logos stay distinct.
                modifier = Modifier.offset(x = LogoStep * index).border(LogoRing, scheme.primaryContainer, CircleShape),
            )
        }
        if (moreCount > 0) {
            Box(
                modifier =
                    Modifier
                        .offset(x = LogoStep * logos.size)
                        .size(LogoSize)
                        .background(scheme.onPrimaryContainer, CircleShape)
                        .border(LogoRing, scheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(R.string.hero_more, moreCount),
                    style = MaterialTheme.typography.labelLarge,
                    color = scheme.primaryContainer,
                )
            }
        }
    }
}

/** The next charge or trial end, in the tertiary container (error when it needs attention now). */
@Composable
private fun NextChargePill(
    subscription: Subscription,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val urgent = subscription.isUrgent
    val whenText = subscription.nextEventLabel()
    val amount = FormattingUtils.formatCurrency(subscription.amount, subscription.currency)
    val description = stringResource(R.string.hero_next_description, subscription.name, whenText, amount)
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().semantics { contentDescription = description },
        shape = MaterialTheme.shapes.large,
        color = if (urgent) scheme.errorContainer else scheme.tertiaryContainer,
        contentColor = if (urgent) scheme.onErrorContainer else scheme.onTertiaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.l, vertical = Spacing.m),
            horizontalArrangement = Arrangement.spacedBy(Spacing.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(subscription.name, style = MaterialTheme.typography.titleSmallEmphasized, maxLines = 1)
                Text(whenText, style = MaterialTheme.typography.bodySmall)
            }
            MoneyText(subscription.amount, subscription.currency, style = MaterialTheme.typography.titleMediumEmphasized)
        }
    }
}

/** Footnotes under a total: how many subscriptions were converted, and how many could not be. */
@Composable
fun CurrencyNoteLines(currencyNote: CurrencyNote) {
    val ratesDate = currencyNote.ratesUpdatedAtEpochSeconds
    if (currencyNote.convertedCount > 0 && ratesDate != null) {
        Text(
            text =
                pluralStringResource(
                    R.plurals.hero_converted,
                    currencyNote.convertedCount,
                    currencyNote.convertedCount,
                    FormattingUtils.formatDate(ratesDate * 1000),
                ),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = Spacing.s),
        )
    }
    if (currencyNote.unconvertedCount > 0) {
        Text(
            text =
                pluralStringResource(
                    R.plurals.hero_other_currencies,
                    currencyNote.unconvertedCount,
                    currencyNote.unconvertedCount,
                ),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = Spacing.s),
        )
    }
}
