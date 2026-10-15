package com.wolfeleo2.subsy.ui.settings

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wolfeleo2.subsy.BuildConfig
import com.wolfeleo2.subsy.R
import com.wolfeleo2.subsy.data.model.AppMode
import com.wolfeleo2.subsy.data.model.ThemeMode
import com.wolfeleo2.subsy.data.model.convertTo
import com.wolfeleo2.subsy.data.repository.ExchangeRates
import com.wolfeleo2.subsy.data.repository.UserSettings
import com.wolfeleo2.subsy.detection.DetectorRelease
import com.wolfeleo2.subsy.detection.DetectorState
import com.wolfeleo2.subsy.ui.components.BudgetDialog
import com.wolfeleo2.subsy.ui.components.LoadingState
import com.wolfeleo2.subsy.ui.components.SectionHeader
import com.wolfeleo2.subsy.ui.onboarding.description
import com.wolfeleo2.subsy.ui.onboarding.label
import com.wolfeleo2.subsy.ui.theme.AppTheme
import com.wolfeleo2.subsy.ui.theme.Spacing
import com.wolfeleo2.subsy.ui.theme.tabular
import com.wolfeleo2.subsy.ui.update.CheckOutcome
import com.wolfeleo2.subsy.ui.update.UpdateUiState
import com.wolfeleo2.subsy.ui.util.FormattingUtils
import com.wolfeleo2.subsy.ui.util.currencyChoices
import com.wolfeleo2.subsy.ui.util.currencyName
import com.wolfeleo2.subsy.ui.util.hasNotificationAccess
import com.wolfeleo2.subsy.ui.util.hasSmsAccess
import com.wolfeleo2.subsy.ui.util.openNotificationAccess
import com.wolfeleo2.subsy.ui.util.openUrl
import com.wolfeleo2.subsy.ui.util.rememberSmsRequest

private const val EXCHANGE_RATE_SOURCE_URL = "https://www.exchangerate-api.com"

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit,
    onOpenCategories: () -> Unit,
    updateState: UpdateUiState,
    onUpdateClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val rates by viewModel.exchangeRates.collectAsStateWithLifecycle()
    val detectorState by viewModel.detectorState.collectAsStateWithLifecycle()
    val spottedCount by viewModel.spottedCount.collectAsStateWithLifecycle()

    val current = settings
    if (current == null) {
        LoadingState()
        return
    }

    SettingsContent(
        settings = current,
        rates = rates,
        onSetPrimaryCurrency = viewModel::setPrimaryCurrency,
        onSetAppMode = viewModel::setAppMode,
        onSetMonthlyBudget = viewModel::setMonthlyBudget,
        onSetSpreadLongCharges = viewModel::setSpreadLongCharges,
        onSetUseDynamicColor = viewModel::setUseDynamicColor,
        onSetThemeMode = viewModel::setThemeMode,
        detectorState = detectorState,
        spottedCount = spottedCount,
        onSetDetectionEnabled = viewModel::setDetectionEnabled,
        onSetAccountHolderName = viewModel::setAccountHolderName,
        onNavigateBack = onNavigateBack,
        onOpenCategories = onOpenCategories,
        updateState = updateState,
        onUpdateClick = onUpdateClick,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsContent(
    settings: UserSettings,
    rates: ExchangeRates?,
    onSetPrimaryCurrency: (String) -> Unit,
    onSetAppMode: (AppMode) -> Unit,
    onSetMonthlyBudget: (Double?) -> Unit,
    onSetSpreadLongCharges: (Boolean) -> Unit,
    onSetUseDynamicColor: (Boolean) -> Unit,
    onSetThemeMode: (ThemeMode) -> Unit,
    detectorState: DetectorState,
    spottedCount: Int,
    onSetDetectionEnabled: (Boolean) -> Unit,
    onSetAccountHolderName: (String) -> Unit,
    onNavigateBack: () -> Unit,
    onOpenCategories: () -> Unit,
    updateState: UpdateUiState,
    onUpdateClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    // Both are granted outside this screen (a system dialog, or Android's settings), so re-check on every resume.
    var smsAllowed by remember { mutableStateOf(hasSmsAccess(context)) }
    var notificationsAllowed by remember { mutableStateOf(hasNotificationAccess(context)) }
    LifecycleResumeEffect(Unit) {
        smsAllowed = hasSmsAccess(context)
        notificationsAllowed = hasNotificationAccess(context)
        onPauseOrDispose {}
    }
    val smsRequest = rememberSmsRequest { smsAllowed = it }
    var showCurrencyPicker by rememberSaveable { mutableStateOf(false) }
    var showModePicker by rememberSaveable { mutableStateOf(false) }
    var showBudgetEditor by rememberSaveable { mutableStateOf(false) }
    var showThemePicker by rememberSaveable { mutableStateOf(false) }
    var showHolderEditor by rememberSaveable { mutableStateOf(false) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.nav_settings)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding =
                PaddingValues(
                    start = Spacing.gutter,
                    end = Spacing.gutter,
                    top = innerPadding.calculateTopPadding(),
                    bottom = Spacing.xl,
                ),
            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
        ) {
            val allSpending = settings.appMode == AppMode.ALL_SPENDING
            val trackingRows = if (allSpending) 4 else 3
            item { SectionHeader(stringResource(R.string.settings_section_tracking)) }
            item {
                SegmentedListItem(
                    onClick = { showModePicker = true },
                    shapes = ListItemDefaults.segmentedShapes(index = 0, count = trackingRows),
                    supportingContent = { Text((settings.appMode ?: AppMode.SUBSCRIPTIONS).description()) },
                    trailingContent = { Text((settings.appMode ?: AppMode.SUBSCRIPTIONS).label()) },
                ) {
                    Text(stringResource(R.string.settings_mode))
                }
            }
            item {
                val budget = settings.monthlyBudget
                val budgetHere = budget?.convertTo(settings.primaryCurrency, rates)
                SegmentedListItem(
                    onClick = { showBudgetEditor = true },
                    shapes = ListItemDefaults.segmentedShapes(index = 1, count = trackingRows),
                    supportingContent = { Text(stringResource(R.string.settings_budget_supporting)) },
                    trailingContent = {
                        Text(
                            when {
                                budget == null -> stringResource(R.string.settings_budget_none)
                                budgetHere != null -> FormattingUtils.formatCurrency(budgetHere, settings.primaryCurrency)
                                else -> FormattingUtils.formatCurrency(budget.amount, budget.currency)
                            },
                            style = MaterialTheme.typography.bodyMedium.tabular,
                        )
                    },
                ) {
                    Text(stringResource(R.string.settings_budget))
                }
            }
            item {
                SegmentedListItem(
                    checked = settings.spreadLongCharges,
                    onCheckedChange = onSetSpreadLongCharges,
                    shapes = ListItemDefaults.segmentedShapes(index = 2, count = trackingRows),
                    supportingContent = { Text(stringResource(R.string.settings_spread_supporting)) },
                    trailingContent = { Switch(checked = settings.spreadLongCharges, onCheckedChange = null) },
                ) {
                    Text(stringResource(R.string.settings_spread))
                }
            }
            if (allSpending) {
                item {
                    SegmentedListItem(
                        onClick = onOpenCategories,
                        shapes = ListItemDefaults.segmentedShapes(index = 3, count = trackingRows),
                        supportingContent = { Text(stringResource(R.string.categories_supporting)) },
                    ) {
                        Text(stringResource(R.string.categories_title))
                    }
                }
            }
            item { SectionHeader(stringResource(R.string.settings_section_money)) }
            item {
                SegmentedListItem(
                    onClick = { showCurrencyPicker = true },
                    shapes = ListItemDefaults.segmentedShapes(index = 0, count = 2),
                    supportingContent = {
                        Text(
                            stringResource(R.string.settings_currency_supporting, currencyName(settings.primaryCurrency)),
                        )
                    },
                    trailingContent = { Text(settings.primaryCurrency) },
                ) {
                    Text(stringResource(R.string.settings_currency))
                }
            }
            item {
                SegmentedListItem(
                    onClick = { openUrl(context, EXCHANGE_RATE_SOURCE_URL) },
                    shapes = ListItemDefaults.segmentedShapes(index = 1, count = 2),
                    supportingContent = {
                        val updated = rates?.updatedAtEpochSeconds
                        Text(
                            if (updated == null) {
                                stringResource(R.string.settings_rates_not_loaded)
                            } else {
                                stringResource(R.string.settings_rates_updated, FormattingUtils.formatDate(updated * 1000))
                            },
                        )
                    },
                ) {
                    Text(stringResource(R.string.settings_rates))
                }
            }
            item { SectionHeader(stringResource(R.string.detection_section)) }
            item {
                SegmentedListItem(
                    checked = settings.detectionEnabled,
                    onCheckedChange = onSetDetectionEnabled,
                    shapes = ListItemDefaults.segmentedShapes(index = 0, count = if (settings.detectionEnabled) 4 else 1),
                    supportingContent = { Text(detectionStatus(settings.detectionEnabled, detectorState, spottedCount)) },
                    trailingContent = { Switch(checked = settings.detectionEnabled, onCheckedChange = null) },
                ) {
                    Text(stringResource(R.string.detection_switch))
                }
            }
            if (settings.detectionEnabled) {
                item {
                    SegmentedListItem(
                        onClick = { if (!smsAllowed) smsRequest.launch() },
                        shapes = ListItemDefaults.segmentedShapes(index = 1, count = 4),
                        supportingContent = { Text(stringResource(R.string.detection_sms_supporting)) },
                        trailingContent = { Text(accessLabel(smsAllowed)) },
                    ) {
                        Text(stringResource(R.string.detection_sms))
                    }
                }
                item {
                    SegmentedListItem(
                        onClick = { if (!notificationsAllowed) openNotificationAccess(context) },
                        shapes = ListItemDefaults.segmentedShapes(index = 2, count = 4),
                        supportingContent = { Text(stringResource(R.string.detection_notifications_supporting)) },
                        trailingContent = { Text(accessLabel(notificationsAllowed)) },
                    ) {
                        Text(stringResource(R.string.detection_notifications))
                    }
                }
                item {
                    SegmentedListItem(
                        onClick = { showHolderEditor = true },
                        shapes = ListItemDefaults.segmentedShapes(index = 3, count = 4),
                        supportingContent = { Text(stringResource(R.string.detection_holder_supporting)) },
                        trailingContent = {
                            Text(settings.accountHolderName.ifBlank { stringResource(R.string.detection_holder_none) })
                        },
                    ) {
                        Text(stringResource(R.string.detection_holder))
                    }
                }
            }
            item { SectionHeader(stringResource(R.string.settings_section_appearance)) }
            item {
                SegmentedListItem(
                    onClick = { showThemePicker = true },
                    shapes = ListItemDefaults.segmentedShapes(index = 0, count = 2),
                    trailingContent = {
                        Text(
                            when (settings.themeMode) {
                                ThemeMode.SYSTEM -> stringResource(R.string.settings_theme_system)
                                ThemeMode.LIGHT -> stringResource(R.string.settings_theme_light)
                                ThemeMode.DARK -> stringResource(R.string.settings_theme_dark)
                            },
                        )
                    },
                ) {
                    Text(stringResource(R.string.settings_theme))
                }
            }
            item {
                SegmentedListItem(
                    checked = settings.useDynamicColor,
                    onCheckedChange = onSetUseDynamicColor,
                    shapes = ListItemDefaults.segmentedShapes(index = 1, count = 2),
                    supportingContent = { Text(stringResource(R.string.settings_dynamic_color_supporting)) },
                    trailingContent = { Switch(checked = settings.useDynamicColor, onCheckedChange = null) },
                ) {
                    Text(stringResource(R.string.settings_dynamic_color))
                }
            }
            item { SectionHeader(stringResource(R.string.settings_section_about)) }
            item {
                SegmentedListItem(
                    onClick = onUpdateClick,
                    shapes = ListItemDefaults.segmentedShapes(index = 0, count = 1),
                    supportingContent = { Text(updateSummary(updateState)) },
                ) {
                    Text(stringResource(R.string.settings_updates))
                }
            }
        }

        if (showHolderEditor) {
            AccountHolderDialog(
                current = settings.accountHolderName,
                onSave = {
                    onSetAccountHolderName(it)
                    showHolderEditor = false
                },
                onDismiss = { showHolderEditor = false },
            )
        }
        if (showThemePicker) {
            ThemePickerDialog(
                selected = settings.themeMode,
                onSelect = {
                    onSetThemeMode(it)
                    showThemePicker = false
                },
                onDismiss = { showThemePicker = false },
            )
        }
        if (showModePicker) {
            ModePickerDialog(
                selected = settings.appMode ?: AppMode.SUBSCRIPTIONS,
                onSelect = {
                    onSetAppMode(it)
                    showModePicker = false
                },
                onDismiss = { showModePicker = false },
            )
        }
        if (showBudgetEditor) {
            BudgetDialog(
                title = stringResource(R.string.settings_budget),
                current = settings.monthlyBudget?.convertTo(settings.primaryCurrency, rates),
                currency = settings.primaryCurrency,
                onSave = {
                    onSetMonthlyBudget(it)
                    showBudgetEditor = false
                },
                onDismiss = { showBudgetEditor = false },
            )
        }
        if (showCurrencyPicker) {
            CurrencyPickerDialog(
                selected = settings.primaryCurrency,
                onSelect = {
                    onSetPrimaryCurrency(it)
                    showCurrencyPicker = false
                },
                onDismiss = { showCurrencyPicker = false },
            )
        }
    }
}

/** Banks print the user's own name on transfers between their accounts ("received from JOHN KAMAU"). */
@Composable
private fun AccountHolderDialog(
    current: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.detection_holder)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                supportingText = { Text(stringResource(R.string.detection_holder_hint)) },
            )
        },
        confirmButton = { TextButton(onClick = { onSave(name) }) { Text(stringResource(R.string.action_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun CurrencyPickerDialog(
    selected: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_currency)) },
        text = {
            LazyColumn(Modifier.selectableGroup()) {
                items(currencyChoices(selected), key = { it }) { code ->
                    ListItem(
                        selected = code == selected,
                        onClick = { onSelect(code) },
                        leadingContent = { RadioButton(selected = code == selected, onClick = null) },
                        supportingContent = { Text(currencyName(code)) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    ) {
                        Text(code)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun ModePickerDialog(
    selected: AppMode,
    onSelect: (AppMode) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_mode)) },
        text = {
            Column(Modifier.selectableGroup()) {
                AppMode.entries.forEach { mode ->
                    ListItem(
                        selected = mode == selected,
                        onClick = { onSelect(mode) },
                        leadingContent = { RadioButton(selected = mode == selected, onClick = null) },
                        supportingContent = { Text(mode.description()) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    ) {
                        Text(mode.label())
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun ThemePickerDialog(
    selected: ThemeMode,
    onSelect: (ThemeMode) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_theme)) },
        text = {
            Column(Modifier.selectableGroup()) {
                ThemeMode.entries.forEach { mode ->
                    val label =
                        when (mode) {
                            ThemeMode.SYSTEM -> stringResource(R.string.settings_theme_system)
                            ThemeMode.LIGHT -> stringResource(R.string.settings_theme_light)
                            ThemeMode.DARK -> stringResource(R.string.settings_theme_dark)
                        }
                    ListItem(
                        selected = mode == selected,
                        onClick = { onSelect(mode) },
                        leadingContent = { RadioButton(selected = mode == selected, onClick = null) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    ) {
                        Text(label)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun updateSummary(state: UpdateUiState): String =
    when {
        state.checking -> stringResource(R.string.settings_updates_checking)
        state.available != null -> stringResource(R.string.settings_updates_available, state.available.version)
        state.outcome == CheckOutcome.UP_TO_DATE -> stringResource(R.string.settings_updates_current, BuildConfig.VERSION_NAME)
        state.outcome == CheckOutcome.FAILED -> stringResource(R.string.settings_updates_failed)
        else -> stringResource(R.string.settings_updates_version, BuildConfig.VERSION_NAME)
    }

/** What the detection row says under its title: what turning it on costs, or how the download is going. */
@Composable
private fun detectionStatus(
    enabled: Boolean,
    state: DetectorState,
    spottedCount: Int,
): String {
    val size = Formatter.formatShortFileSize(LocalContext.current, DetectorRelease.totalBytes)
    return when {
        !enabled -> stringResource(R.string.detection_off, size)
        state is DetectorState.Downloading ->
            stringResource(R.string.detection_downloading, (state.doneBytes * 100 / DetectorRelease.totalBytes).toInt())
        state == DetectorState.Ready -> pluralStringResource(R.plurals.detection_ready, spottedCount, spottedCount)
        state == DetectorState.Failed -> stringResource(R.string.detection_failed)
        else -> stringResource(R.string.detection_waiting)
    }
}

@Composable
private fun accessLabel(allowed: Boolean): String =
    stringResource(if (allowed) R.string.detection_access_allowed else R.string.detection_access_needed)

@Preview(showBackground = true)
@Composable
fun SettingsScreenPreview() {
    AppTheme {
        SettingsContent(
            settings =
                UserSettings(
                    primaryCurrency = "USD",
                    useDynamicColor = false,
                    themeMode = ThemeMode.SYSTEM,
                    appMode = AppMode.SUBSCRIPTIONS,
                ),
            rates = null,
            onSetPrimaryCurrency = {},
            onSetAppMode = {},
            onSetMonthlyBudget = {},
            onSetSpreadLongCharges = {},
            onSetUseDynamicColor = {},
            onSetThemeMode = {},
            detectorState = DetectorState.Absent,
            spottedCount = 0,
            onSetDetectionEnabled = {},
            onSetAccountHolderName = {},
            onNavigateBack = {},
            onOpenCategories = {},
            updateState = UpdateUiState(),
            onUpdateClick = {},
        )
    }
}
