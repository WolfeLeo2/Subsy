package com.wolfeleo2.subsy.ui.transactions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wolfeleo2.subsy.R
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
import com.wolfeleo2.subsy.ui.theme.Spacing
import com.wolfeleo2.subsy.ui.theme.tabular

/** Quick add (or edit) for one transaction, amount first. Closes itself once saved. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TransactionSheet(
    viewModel: TransactionSheetViewModel,
    onDismiss: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val customs by viewModel.customCategories.collectAsStateWithLifecycle()
    val sheetState =
        rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))
    val amountFocus = remember { FocusRequester() }
    var pickingDate by rememberSaveable { mutableStateOf(false) }
    var addingCategory by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(viewModel) { viewModel.saved.collect { onDismiss() } }
    LaunchedEffect(state.isLoaded) { if (state.isLoaded && !state.isEditing) amountFocus.requestFocus() }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .imePadding()
                    .padding(horizontal = Spacing.xl)
                    .padding(bottom = Spacing.xl),
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
                onOptionSelected = viewModel::setType,
                labelProvider = { typeLabels.getValue(it) },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                OutlinedTextField(
                    value = state.amount,
                    onValueChange = viewModel::setAmount,
                    label = { Text(stringResource(R.string.sheet_amount)) },
                    textStyle = MaterialTheme.typography.headlineMedium.tabular,
                    isError = state.amountError != null,
                    supportingText = errorText(state.amountError),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                    modifier = Modifier.weight(1f).focusRequester(amountFocus),
                )
                CurrencyPicker(currency = state.currency, onCurrencyChange = viewModel::setCurrency, modifier = Modifier.width(120.dp))
            }

            Text(stringResource(R.string.sheet_category), style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                val customMap = customs.associateBy { it.key }
                pickableKeys(state.type, customs).forEach { key ->
                    val ref = resolveCategory(key, state.type, customMap)
                    FilterChip(
                        selected = key == state.categoryKey,
                        onClick = { viewModel.setCategory(key) },
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

            DateField(label = stringResource(R.string.sheet_date), date = state.date, onClick = { pickingDate = true })
            OutlinedTextField(
                value = state.note,
                onValueChange = viewModel::setNote,
                label = { Text(stringResource(R.string.sheet_note)) },
                placeholder = { Text(stringResource(R.string.sheet_note_hint)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
            Button(onClick = viewModel::save, enabled = state.isLoaded && !state.isSaving, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.action_save))
            }
        }
    }

    if (pickingDate) {
        PickDateDialog(
            initialDate = state.date,
            onConfirm = {
                viewModel.setDate(it)
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
                viewModel.addCategory(name, color)
                addingCategory = false
            },
            onDismiss = { addingCategory = false },
        )
    }
}
