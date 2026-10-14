package com.wolfeleo2.subsy.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DonutLarge
import androidx.compose.material.icons.rounded.ViewAgenda
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.FloatingToolbarScrollBehavior
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import com.wolfeleo2.subsy.R

/**
 * One toolbar is drawn above Home and Spending (in [SubsyNavGraph]) so it stays still while they
 * fade through. The screens' lists feed its hide-on-scroll through this.
 */
val LocalRootToolbarScroll = compositionLocalOf<FloatingToolbarScrollBehavior?> { null }

@Composable
fun Modifier.rootToolbarScroll(): Modifier = LocalRootToolbarScroll.current?.let { nestedScroll(it) } ?: this

enum class RootTab {
    HOME,
    TRANSACTIONS,
    ANALYTICS,
}

/**
 * The app's main navigation (PRD 4.3): a floating toolbar with the root screens in [tabs] and the add FAB,
 * whose action and label ([addLabel]) depend on the current tab.
 * It hides while the list scrolls down and returns on scroll up via [scrollBehavior].
 */
@Composable
fun RootToolbar(
    tabs: List<RootTab>,
    current: RootTab,
    onSelect: (RootTab) -> Unit,
    addLabel: String,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
    scrollBehavior: FloatingToolbarScrollBehavior? = null,
) {
    HorizontalFloatingToolbar(
        expanded = true,
        modifier = modifier,
        scrollBehavior = scrollBehavior,
        floatingActionButton = {
            FloatingToolbarDefaults.VibrantFloatingActionButton(onClick = onAdd) {
                Icon(Icons.Rounded.Add, contentDescription = addLabel)
            }
        },
    ) {
        tabs.forEach { tab ->
            when (tab) {
                RootTab.HOME -> TabButton(tab, current, Icons.Rounded.ViewAgenda, stringResource(R.string.nav_home), onSelect)
                RootTab.TRANSACTIONS ->
                    TabButton(tab, current, Icons.AutoMirrored.Rounded.ReceiptLong, stringResource(R.string.nav_transactions), onSelect)
                RootTab.ANALYTICS -> TabButton(tab, current, Icons.Rounded.DonutLarge, stringResource(R.string.nav_analytics), onSelect)
            }
        }
    }
}

@Composable
private fun TabButton(
    tab: RootTab,
    current: RootTab,
    icon: ImageVector,
    label: String,
    onSelect: (RootTab) -> Unit,
) {
    FilledTonalIconToggleButton(
        checked = tab == current,
        onCheckedChange = { if (tab != current) onSelect(tab) },
    ) {
        Icon(icon, contentDescription = label)
    }
}
