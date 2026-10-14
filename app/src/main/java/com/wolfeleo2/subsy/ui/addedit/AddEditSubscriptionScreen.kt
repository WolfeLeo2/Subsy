package com.wolfeleo2.subsy.ui.addedit

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wolfeleo2.subsy.R
import com.wolfeleo2.subsy.data.model.BillingCycleType
import com.wolfeleo2.subsy.data.model.Category
import com.wolfeleo2.subsy.data.model.PresetService
import com.wolfeleo2.subsy.data.repository.ExchangeRates
import com.wolfeleo2.subsy.data.util.parseAmount
import com.wolfeleo2.subsy.ui.components.ConnectedButtonGroup
import com.wolfeleo2.subsy.ui.components.CurrencyPicker
import com.wolfeleo2.subsy.ui.components.DateField
import com.wolfeleo2.subsy.ui.components.EmptyState
import com.wolfeleo2.subsy.ui.components.LoadingState
import com.wolfeleo2.subsy.ui.components.PickDateDialog
import com.wolfeleo2.subsy.ui.components.SectionHeader
import com.wolfeleo2.subsy.ui.components.ServiceIconDefaults
import com.wolfeleo2.subsy.ui.components.ServiceIconView
import com.wolfeleo2.subsy.ui.components.errorText
import com.wolfeleo2.subsy.ui.components.label
import com.wolfeleo2.subsy.ui.theme.AppTheme
import com.wolfeleo2.subsy.ui.theme.Spacing
import com.wolfeleo2.subsy.ui.theme.tabular
import com.wolfeleo2.subsy.ui.util.FormattingUtils
import java.time.LocalDate

@Composable
fun AddEditSubscriptionScreen(
    viewModel: AddEditViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val formState by viewModel.formState.collectAsStateWithLifecycle()
    val exchangeRates by viewModel.exchangeRates.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val currentOnNavigateBack by rememberUpdatedState(onNavigateBack)

    LaunchedEffect(viewModel) {
        viewModel.eventFlow.collect { event ->
            when (event) {
                AddEditEvent.Saved -> currentOnNavigateBack()
            }
        }
    }

    val saveErrorText = formState.saveError?.let { stringResource(it) }
    LaunchedEffect(saveErrorText) {
        saveErrorText?.let { snackbarHostState.showSnackbar(it) }
    }

    AddEditSubscriptionScreenContent(
        formState = formState,
        exchangeRates = exchangeRates,
        hasUnsavedChanges = viewModel.hasUnsavedChanges,
        onSave = viewModel::save,
        onNameChange = viewModel::onNameChange,
        onAmountChange = viewModel::onAmountChange,
        onCurrencyChange = viewModel::onCurrencyChange,
        onBillingCycleChange = viewModel::onBillingCycleChange,
        onCustomCycleDaysChange = viewModel::onCustomCycleDaysChange,
        onFreeTrialToggle = viewModel::onFreeTrialToggle,
        onTrialDurationDaysChange = viewModel::onTrialDurationDaysChange,
        onFirstChargeDateChange = viewModel::onFirstChargeDateChange,
        onCategoryChange = viewModel::onCategoryChange,
        onCancellationUrlChange = viewModel::onCancellationUrlChange,
        onNotesChange = viewModel::onNotesChange,
        onApplyPreset = viewModel::applyPreset,
        onNavigateBack = onNavigateBack,
        snackbarHostState = snackbarHostState,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditSubscriptionScreenContent(
    formState: AddEditFormState,
    exchangeRates: ExchangeRates? = null,
    hasUnsavedChanges: Boolean,
    onSave: () -> Unit,
    onNameChange: (String) -> Unit,
    onAmountChange: (String) -> Unit,
    onCurrencyChange: (String) -> Unit,
    onBillingCycleChange: (BillingCycleType) -> Unit,
    onCustomCycleDaysChange: (String) -> Unit,
    onFreeTrialToggle: (Boolean) -> Unit,
    onTrialDurationDaysChange: (Int) -> Unit,
    onFirstChargeDateChange: (LocalDate) -> Unit,
    onCategoryChange: (Category) -> Unit,
    onCancellationUrlChange: (String) -> Unit,
    onNotesChange: (String) -> Unit,
    onApplyPreset: (PresetService) -> Unit,
    onNavigateBack: () -> Unit,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    modifier: Modifier = Modifier,
) {
    var showPresetSheet by rememberSaveable { mutableStateOf(!formState.isEditing) }
    var showDiscardDialog by rememberSaveable { mutableStateOf(false) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    val requestClose: () -> Unit = {
        if (hasUnsavedChanges) showDiscardDialog = true else onNavigateBack()
    }
    BackHandler(enabled = hasUnsavedChanges) { showDiscardDialog = true }

    if (showDiscardDialog) {
        DiscardChangesDialog(
            onDiscard = {
                showDiscardDialog = false
                onNavigateBack()
            },
            onKeepEditing = { showDiscardDialog = false },
        )
    }

    if (showPresetSheet) {
        PresetPickerSheet(
            approxPrice = { approxPrice(it.defaultAmount, it.defaultCurrency, formState.currency, exchangeRates) },
            onDismissRequest = { showPresetSheet = false },
            onPresetSelected = { preset ->
                onApplyPreset(preset)
                showPresetSheet = false
            },
            onCustomSelected = { name ->
                if (name.isNotBlank()) onNameChange(name.trim())
                showPresetSheet = false
            },
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MediumFlexibleTopAppBar(
                title = { Text(stringResource(if (formState.isEditing) R.string.addedit_title_edit else R.string.addedit_title_new)) },
                navigationIcon = {
                    IconButton(onClick = requestClose) {
                        Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.action_close))
                    }
                },
                actions = {
                    Button(
                        onClick = onSave,
                        shapes = ButtonDefaults.shapes(),
                        enabled = !formState.isSaving && !formState.isLoading,
                        modifier = Modifier.padding(end = Spacing.s),
                    ) {
                        Text(stringResource(R.string.action_save))
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        if (formState.isLoading) {
            LoadingState(Modifier.padding(innerPadding))
        } else if (formState.isNotFound) {
            EmptyState(
                icon = Icons.Rounded.SearchOff,
                title = stringResource(R.string.detail_not_found_title),
                body = stringResource(R.string.detail_not_found_body),
                modifier = Modifier.padding(innerPadding),
                action = { Button(onClick = onNavigateBack) { Text(stringResource(R.string.detail_back_to_list)) } },
            )
        } else {
            AddEditForm(
                formState = formState,
                exchangeRates = exchangeRates,
                onNameChange = onNameChange,
                onAmountChange = onAmountChange,
                onCurrencyChange = onCurrencyChange,
                onBillingCycleChange = onBillingCycleChange,
                onCustomCycleDaysChange = onCustomCycleDaysChange,
                onFreeTrialToggle = onFreeTrialToggle,
                onTrialDurationDaysChange = onTrialDurationDaysChange,
                onFirstChargeDateChange = onFirstChargeDateChange,
                onCategoryChange = onCategoryChange,
                onCancellationUrlChange = onCancellationUrlChange,
                onNotesChange = onNotesChange,
                onOpenCatalog = { showPresetSheet = true },
                modifier =
                    Modifier
                        .padding(innerPadding)
                        .consumeWindowInsets(innerPadding)
                        .imePadding(),
            )
        }
    }
}

@Composable
private fun AddEditForm(
    formState: AddEditFormState,
    exchangeRates: ExchangeRates?,
    onNameChange: (String) -> Unit,
    onAmountChange: (String) -> Unit,
    onCurrencyChange: (String) -> Unit,
    onBillingCycleChange: (BillingCycleType) -> Unit,
    onCustomCycleDaysChange: (String) -> Unit,
    onFreeTrialToggle: (Boolean) -> Unit,
    onTrialDurationDaysChange: (Int) -> Unit,
    onFirstChargeDateChange: (LocalDate) -> Unit,
    onCategoryChange: (Category) -> Unit,
    onCancellationUrlChange: (String) -> Unit,
    onNotesChange: (String) -> Unit,
    onOpenCatalog: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.gutter)
                .padding(bottom = Spacing.xxl),
    ) {
        IdentitySection(formState, onNameChange, onOpenCatalog)
        AmountSection(formState, exchangeRates, onAmountChange, onCurrencyChange)
        SectionHeader(stringResource(R.string.addedit_section_billing))
        BillingSection(
            formState = formState,
            onBillingCycleChange = onBillingCycleChange,
            onCustomCycleDaysChange = onCustomCycleDaysChange,
            onFreeTrialToggle = onFreeTrialToggle,
            onTrialDurationDaysChange = onTrialDurationDaysChange,
            onFirstChargeDateChange = onFirstChargeDateChange,
        )
        SectionHeader(stringResource(R.string.addedit_section_category))
        CategorySection(selected = formState.category, onSelect = onCategoryChange)
        SectionHeader(stringResource(R.string.addedit_section_link_notes))
        LinkAndNotesSection(formState, onCancellationUrlChange, onNotesChange)
    }
}

@Composable
private fun IdentitySection(
    formState: AddEditFormState,
    onNameChange: (String) -> Unit,
    onOpenCatalog: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.l),
    ) {
        ServiceIconView(
            name = formState.name,
            colorHex = formState.colorHex,
            cancellationUrl = formState.cancellationUrl,
            size = ServiceIconDefaults.HeroSize,
        )
        OutlinedTextField(
            value = formState.name,
            onValueChange = onNameChange,
            label = { Text(stringResource(R.string.addedit_name)) },
            isError = formState.nameError != null,
            supportingText = errorText(formState.nameError),
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
            modifier = Modifier.weight(1f),
        )
    }
    // Applying a preset overwrites the form, so the catalog is only offered while adding.
    if (!formState.isEditing) {
        TextButton(onClick = onOpenCatalog) {
            Icon(Icons.Rounded.Apps, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
            Text(
                text = stringResource(R.string.addedit_pick_from_catalog),
                modifier = Modifier.padding(start = ButtonDefaults.IconSpacing),
            )
        }
    }
}

@Composable
private fun AmountSection(
    formState: AddEditFormState,
    exchangeRates: ExchangeRates?,
    onAmountChange: (String) -> Unit,
    onCurrencyChange: (String) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = Spacing.m),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        verticalAlignment = Alignment.Top,
    ) {
        OutlinedTextField(
            value = formState.amount,
            onValueChange = onAmountChange,
            label = { Text(stringResource(R.string.addedit_amount)) },
            textStyle = MaterialTheme.typography.headlineSmall.tabular,
            isError = formState.amountError != null,
            supportingText = errorText(formState.amountError) ?: catalogPriceHint(formState, exchangeRates),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
            modifier = Modifier.weight(1f),
        )
        CurrencyPicker(
            currency = formState.currency,
            onCurrencyChange = onCurrencyChange,
            modifier = Modifier.width(120.dp),
        )
    }
    val cycle = formState.cycleOrNull()
    val amount = parseAmount(formState.amount)
    if (cycle != null && amount != null && cycle.type != BillingCycleType.MONTHLY) {
        Text(
            text =
                stringResource(
                    R.string.addedit_about_monthly,
                    FormattingUtils.formatCurrency(cycle.normalizedMonthlyAmount(amount), formState.currency),
                ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = Spacing.l, top = Spacing.xs),
        )
    }
}

private fun catalogPriceHint(
    formState: AddEditFormState,
    rates: ExchangeRates?,
): (@Composable () -> Unit)? {
    val price = formState.catalogPrice ?: return null
    if (formState.currency == formState.catalogCurrency) return null
    val usPrice = FormattingUtils.formatCurrency(price, formState.catalogCurrency)
    val approx = approxPrice(price, formState.catalogCurrency, formState.currency, rates)
    return {
        Text(
            if (approx == null) {
                stringResource(R.string.addedit_catalog_price_hint, usPrice)
            } else {
                stringResource(R.string.addedit_catalog_price_hint_converted, usPrice, approx)
            },
        )
    }
}

/** A catalog price converted at today's rate, in whole units since it's only a ballpark. */
internal fun approxPrice(
    amount: Double,
    from: String,
    to: String,
    rates: ExchangeRates?,
): String? {
    if (from == to) return null
    val converted = rates?.convert(amount, from, to) ?: return null
    return FormattingUtils.formatCurrency(converted, to, wholeUnits = true)
}

@Composable
private fun BillingSection(
    formState: AddEditFormState,
    onBillingCycleChange: (BillingCycleType) -> Unit,
    onCustomCycleDaysChange: (String) -> Unit,
    onFreeTrialToggle: (Boolean) -> Unit,
    onTrialDurationDaysChange: (Int) -> Unit,
    onFirstChargeDateChange: (LocalDate) -> Unit,
) {
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    Column(
        modifier = Modifier.animateContentSize(MaterialTheme.motionScheme.defaultSpatialSpec()),
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        CycleDropdown(selected = formState.billingCycleType, onSelect = onBillingCycleChange)
        if (formState.billingCycleType == BillingCycleType.CUSTOM) {
            OutlinedTextField(
                value = formState.customCycleDays,
                onValueChange = onCustomCycleDaysChange,
                label = { Text(stringResource(R.string.addedit_every_days)) },
                isError = formState.customDaysError != null,
                supportingText = errorText(formState.customDaysError),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        ListItem(
            checked = formState.isFreeTrial,
            onCheckedChange = onFreeTrialToggle,
            supportingContent = { Text(stringResource(R.string.addedit_free_trial_supporting)) },
            trailingContent = { Switch(checked = formState.isFreeTrial, onCheckedChange = null) },
            content = { Text(stringResource(R.string.addedit_free_trial)) },
        )
        if (formState.isFreeTrial) {
            val labels = TRIAL_DURATION_PRESETS.associateWith { pluralStringResource(R.plurals.addedit_trial_days, it, it) }
            ConnectedButtonGroup<Int?>(
                options = TRIAL_DURATION_PRESETS,
                selectedOption = formState.trialDurationDays,
                onOptionSelected = { days -> days?.let(onTrialDurationDaysChange) },
                labelProvider = { labels[it].orEmpty() },
            )
        }
        DateField(
            label = stringResource(if (formState.isFreeTrial) R.string.addedit_trial_ends else R.string.addedit_next_charge),
            date = formState.firstChargeDate,
            onClick = { showDatePicker = true },
        )
    }
    if (showDatePicker) {
        PickDateDialog(
            initialDate = formState.firstChargeDate,
            onConfirm = { date ->
                onFirstChargeDateChange(date)
                showDatePicker = false
            },
            onDismiss = { showDatePicker = false },
        )
    }
}

@Composable
private fun CycleDropdown(
    selected: BillingCycleType,
    onSelect: (BillingCycleType) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected.label(),
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text(stringResource(R.string.addedit_repeats)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            BillingCycleType.entries.forEach { type ->
                DropdownMenuItem(
                    text = { Text(type.label()) },
                    onClick = {
                        onSelect(type)
                        expanded = false
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            }
        }
    }
}

@Composable
private fun CategorySection(
    selected: Category,
    onSelect: (Category) -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        Category.entries.forEach { category ->
            val isSelected = category == selected
            FilterChip(
                selected = isSelected,
                onClick = { onSelect(category) },
                label = { Text(category.label()) },
                leadingIcon =
                    if (isSelected) {
                        { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
                    } else {
                        null
                    },
            )
        }
    }
}

@Composable
private fun LinkAndNotesSection(
    formState: AddEditFormState,
    onCancellationUrlChange: (String) -> Unit,
    onNotesChange: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        OutlinedTextField(
            value = formState.cancellationUrl,
            onValueChange = onCancellationUrlChange,
            label = { Text(stringResource(R.string.addedit_cancellation_page)) },
            placeholder = { Text(stringResource(R.string.addedit_cancellation_placeholder)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = formState.notes,
            onValueChange = onNotesChange,
            label = { Text(stringResource(R.string.addedit_notes)) },
            minLines = 3,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun DiscardChangesDialog(
    onDiscard: () -> Unit,
    onKeepEditing: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onKeepEditing,
        title = { Text(stringResource(R.string.addedit_discard_title)) },
        confirmButton = {
            TextButton(onClick = onDiscard) { Text(stringResource(R.string.action_discard)) }
        },
        dismissButton = {
            TextButton(onClick = onKeepEditing) { Text(stringResource(R.string.action_keep_editing)) }
        },
    )
}

@Preview(showBackground = true)
@Composable
fun AddEditSubscriptionScreenPreview() {
    val sampleFormState =
        AddEditFormState(
            name = "Netflix",
            amount = "15.99",
            currency = "USD",
            category = Category.ENTERTAINMENT,
            billingCycleType = BillingCycleType.MONTHLY,
            isEditing = true,
            cancellationUrl = "https://www.netflix.com/cancel",
            notes = "Standard monthly plan",
        )

    AppTheme {
        AddEditSubscriptionScreenContent(
            formState = sampleFormState,
            hasUnsavedChanges = false,
            onSave = {},
            onNameChange = {},
            onAmountChange = {},
            onCurrencyChange = {},
            onBillingCycleChange = {},
            onCustomCycleDaysChange = {},
            onFreeTrialToggle = {},
            onTrialDurationDaysChange = {},
            onFirstChargeDateChange = {},
            onCategoryChange = {},
            onCancellationUrlChange = {},
            onNotesChange = {},
            onApplyPreset = {},
            onNavigateBack = {},
        )
    }
}
