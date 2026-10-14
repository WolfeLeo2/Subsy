package com.wolfeleo2.subsy.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.wolfeleo2.subsy.R
import com.wolfeleo2.subsy.ui.util.FormattingUtils
import com.wolfeleo2.subsy.ui.util.currencyChoices
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

// Form pieces shared by the subscription form and the transaction sheet.

@Composable
fun CurrencyPicker(
    currency: String,
    onCurrencyChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = currency,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text(stringResource(R.string.addedit_currency)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            currencyChoices(currency).forEach { code ->
                DropdownMenuItem(
                    text = { Text(code) },
                    onClick = {
                        onCurrencyChange(code)
                        expanded = false
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            }
        }
    }
}

/** A read-only field that opens the date picker; the overlay makes the whole field one tap and focus target. */
@Composable
fun DateField(
    label: String,
    date: LocalDate,
    onClick: () -> Unit,
) {
    val value = FormattingUtils.formatDate(date)
    val changeLabel = stringResource(R.string.addedit_change_date)
    Box(Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text(label) },
            trailingIcon = { Icon(Icons.Rounded.CalendarMonth, contentDescription = null) },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .focusProperties { canFocus = false }
                    .clearAndSetSemantics {},
        )
        Box(
            Modifier
                .matchParentSize()
                .clip(OutlinedTextFieldDefaults.shape)
                .clickable(onClickLabel = changeLabel, role = Role.Button, onClick = onClick)
                .semantics { contentDescription = "$label, $value" },
        )
    }
}

@Composable
fun PickDateDialog(
    initialDate: LocalDate,
    onConfirm: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    // DatePicker works in UTC midnight millis.
    val state = rememberDatePickerState(initialSelectedDateMillis = initialDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val millis = state.selectedDateMillis
                    if (millis != null) onConfirm(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()) else onDismiss()
                },
            ) {
                Text(stringResource(R.string.addedit_date_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    ) {
        DatePicker(state = state)
    }
}

fun errorText(
    @StringRes message: Int?,
): (@Composable () -> Unit)? =
    if (message == null) {
        null
    } else {
        { Text(stringResource(message)) }
    }
