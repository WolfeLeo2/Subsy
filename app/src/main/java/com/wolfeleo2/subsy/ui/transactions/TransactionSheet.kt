package com.wolfeleo2.subsy.ui.transactions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wolfeleo2.subsy.R
import com.wolfeleo2.subsy.data.model.CustomCategory
import com.wolfeleo2.subsy.data.model.CustomCategoryColors
import com.wolfeleo2.subsy.data.model.TransactionType
import com.wolfeleo2.subsy.data.model.resolveCategory
import com.wolfeleo2.subsy.ui.components.CategoryBadge
import com.wolfeleo2.subsy.ui.components.ConnectedButtonGroup
import com.wolfeleo2.subsy.ui.components.CurrencyPicker
import com.wolfeleo2.subsy.ui.components.DateField
import com.wolfeleo2.subsy.ui.components.PickDateDialog
import com.wolfeleo2.subsy.ui.components.errorText
import com.wolfeleo2.subsy.ui.components.label
import com.wolfeleo2.subsy.ui.theme.AppTheme
import com.wolfeleo2.subsy.ui.theme.Spacing
import com.wolfeleo2.subsy.ui.theme.tabular
import java.time.LocalDate

/** Quick add (or edit) for one transaction, amount first. Closes itself once saved. */
@Composable
fun TransactionSheet(
    viewModel: TransactionSheetViewModel,
    onDismiss: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val customs by viewModel.customCategories.collectAsStateWithLifecycle()
    val sheetState =
        rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))

    LaunchedEffect(viewModel) { viewModel.saved.collect { onDismiss() } }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        TransactionSheetContent(
            state = state,
            customs = customs,
            onSetType = viewModel::setType,
            onSetAmount = viewModel::setAmount,
            onSetCurrency = viewModel::setCurrency,
            onSetCategory = viewModel::setCategory,
            onSetDate = viewModel::setDate,
            onSetNote = viewModel::setNote,
            onSave = viewModel::save,
            onAddCategory = viewModel::addCategory,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TransactionSheetContent(
    state: TransactionFormState,
    customs: List<CustomCategory>,
    onSetType: (TransactionType) -> Unit,
    onSetAmount: (String) -> Unit,
    onSetCurrency: (String) -> Unit,
    onSetCategory: (String) -> Unit,
    onSetDate: (LocalDate) -> Unit,
    onSetNote: (String) -> Unit,
    onSave: () -> Unit,
    onAddCategory: (String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val amountFocus = remember { FocusRequester() }
    var pickingDate by rememberSaveable { mutableStateOf(false) }
    var addingCategory by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(state.isLoaded) { if (state.isLoaded && !state.isEditing) amountFocus.requestFocus() }

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .imePadding()
                .padding(horizontal = Spacing.xl)
                .padding(bottom = Spacing.xl),
    ) {
        // Pinned Header: Title & Type Selector
        Column(
            modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.l),
            verticalArrangement = Arrangement.spacedBy(Spacing.l),
        ) {
            Text(
                text = stringResource(if (state.isEditing) R.string.sheet_edit_title else R.string.sheet_new_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() },
            )
            val typeLabels =
                mapOf(
                    TransactionType.EXPENSE to stringResource(R.string.kind_expense),
                    TransactionType.INCOME to stringResource(R.string.kind_income),
                )
            ConnectedButtonGroup(
                options = TransactionType.entries,
                selectedOption = state.type,
                onOptionSelected = onSetType,
                labelProvider = { typeLabels.getValue(it) },
            )
        }

        // Scrollable Middle Form
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Spacing.l),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                OutlinedTextField(
                    value = state.amount,
                    onValueChange = onSetAmount,
                    label = { Text(stringResource(R.string.sheet_amount)) },
                    textStyle = MaterialTheme.typography.headlineMedium.tabular,
                    isError = state.amountError != null,
                    supportingText = errorText(state.amountError),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                    modifier = Modifier.weight(1f).focusRequester(amountFocus),
                )
                CurrencyPicker(currency = state.currency, onCurrencyChange = onSetCurrency, modifier = Modifier.width(120.dp))
            }

            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                Text(stringResource(R.string.sheet_category), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    val customMap = customs.associateBy { it.key }
                    pickableKeys(state.type, customs).forEach { key ->
                        val ref = resolveCategory(key, state.type, customMap)
                        FilterChip(
                            selected = key == state.categoryKey,
                            onClick = { onSetCategory(key) },
                            label = { Text(ref.label()) },
                            leadingIcon = { CategoryBadge(ref, size = 24.dp) },
                        )
                    }
                    FilterChip(
                        selected = false,
                        onClick = { addingCategory = true },
                        label = { Text(stringResource(R.string.sheet_new_category)) },
                        leadingIcon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                    )
                }
                state.categoryError?.let {
                    Text(stringResource(it), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }

            DateField(label = stringResource(R.string.sheet_date), date = state.date, onClick = { pickingDate = true })
            OutlinedTextField(
                value = state.note,
                onValueChange = onSetNote,
                label = { Text(stringResource(R.string.sheet_note)) },
                placeholder = { Text(stringResource(R.string.sheet_note_hint)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(Spacing.xs))
        }

        // Pinned Footer: Save Button
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = Spacing.l),
        ) {
            Button(
                onClick = onSave,
                enabled = state.isLoaded && !state.isSaving,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.action_save))
            }
        }
    }

    if (pickingDate) {
        PickDateDialog(
            initialDate = state.date,
            onConfirm = {
                onSetDate(it)
                pickingDate = false
            },
            onDismiss = { pickingDate = false },
        )
    }
    if (addingCategory) {
        CategoryDialog(
            title = stringResource(R.string.category_dialog_new),
            initialName = "",
            initialColor = CustomCategoryColors.first(),
            onSave = { name, color ->
                onAddCategory(name, color)
                addingCategory = false
            },
            onDismiss = { addingCategory = false },
        )
    }
}

@Preview(showBackground = true)
@Composable
fun TransactionSheetPreview() {
    val sampleState =
        TransactionFormState(
            amount = "25.00",
            currency = "USD",
            categoryKey = "food",
            type = TransactionType.EXPENSE,
            isLoaded = true,
        )

    AppTheme {
        TransactionSheetContent(
            state = sampleState,
            customs = emptyList(),
            onSetType = {},
            onSetAmount = {},
            onSetCurrency = {},
            onSetCategory = {},
            onSetDate = {},
            onSetNote = {},
            onSave = {},
            onAddCategory = { _, _ -> },
        )
    }
}
