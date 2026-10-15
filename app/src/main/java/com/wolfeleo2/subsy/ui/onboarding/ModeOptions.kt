package com.wolfeleo2.subsy.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Autorenew
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import com.wolfeleo2.subsy.R
import com.wolfeleo2.subsy.data.model.AppMode
import com.wolfeleo2.subsy.ui.theme.Spacing

/** The mode choice as a radio group; the host screen supplies the title and the Continue button. */
@Composable
fun ModeChoices(
    selected: AppMode,
    onSelect: (AppMode) -> Unit,
) {
    Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
        AppMode.entries.forEach { mode ->
            ModeOption(mode, selected = mode == selected, onClick = { onSelect(mode) })
        }
    }
    Text(
        text = stringResource(R.string.onboarding_change_later),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = Spacing.s),
    )
}

@Composable
private fun ModeOption(
    mode: AppMode,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = if (selected) scheme.secondaryContainer else scheme.surfaceContainer,
        contentColor = if (selected) scheme.onSecondaryContainer else scheme.onSurface,
        modifier = Modifier.fillMaxWidth().selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(Spacing.l),
            horizontalArrangement = Arrangement.spacedBy(Spacing.l),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(Spacing.xxl + Spacing.l)
                        .background(if (selected) scheme.secondary else scheme.secondaryContainer, MaterialShapes.Cookie9Sided.toShape()),
                contentAlignment = Alignment.Center,
            ) {
                Icon(mode.icon, contentDescription = null, tint = if (selected) scheme.onSecondary else scheme.onSecondaryContainer)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(mode.label(), style = MaterialTheme.typography.titleMediumEmphasized)
                Text(mode.description(), style = MaterialTheme.typography.bodyMedium)
            }
            RadioButton(selected = selected, onClick = null)
        }
    }
}

private val AppMode.icon: ImageVector
    get() =
        when (this) {
            AppMode.SUBSCRIPTIONS -> Icons.Rounded.Autorenew
            AppMode.ALL_SPENDING -> Icons.Rounded.AccountBalanceWallet
        }

@Composable
fun AppMode.label(): String =
    stringResource(
        when (this) {
            AppMode.SUBSCRIPTIONS -> R.string.mode_subscriptions
            AppMode.ALL_SPENDING -> R.string.mode_all_spending
        },
    )

@Composable
fun AppMode.description(): String =
    stringResource(
        when (this) {
            AppMode.SUBSCRIPTIONS -> R.string.mode_subscriptions_description
            AppMode.ALL_SPENDING -> R.string.mode_all_spending_description
        },
    )
