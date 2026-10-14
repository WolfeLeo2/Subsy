package com.wolfeleo2.subsy.ui.components

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import com.wolfeleo2.subsy.R
import com.wolfeleo2.subsy.data.util.formatAmountForInput
import com.wolfeleo2.subsy.data.util.parseAmount
import com.wolfeleo2.subsy.ui.theme.tabular
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Currency

/** Edits a monthly budget amount in [currency]; [onSave] gets null to remove it. */
@Composable
fun BudgetDialog(
    title: String,
    current: Double?,
    currency: String,
    onSave: (Double?) -> Unit,
    onDismiss: () -> Unit,
    /** Whether "Remove" is offered; by default, whenever there is a budget to remove. */
    removable: Boolean = current != null,
) {
    // A converted budget can carry many decimals; offer it rounded to the currency's minor unit.
    var text by rememberSaveable { mutableStateOf(current?.let { formatAmountForInput(roundToMinorUnit(it, currency)) } ?: "") }
    val amount = parseAmount(text)?.takeIf { it > 0.0 }
    val showError = text.isNotBlank() && amount == null
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(stringResource(R.string.settings_budget_field, currency)) },
                singleLine = true,
                isError = showError,
                supportingText = if (showError) ({ Text(stringResource(R.string.error_amount_invalid)) }) else null,
                textStyle = MaterialTheme.typography.headlineSmall.tabular,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(amount) }, enabled = amount != null) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            if (removable) {
                TextButton(onClick = { onSave(null) }) { Text(stringResource(R.string.settings_budget_remove)) }
            } else {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
            }
        },
    )
}

private fun roundToMinorUnit(
    amount: Double,
    currency: String,
): Double {
    val digits = runCatching { Currency.getInstance(currency).defaultFractionDigits }.getOrDefault(2).coerceAtLeast(0)
    return BigDecimal.valueOf(amount).setScale(digits, RoundingMode.HALF_UP).toDouble()
}
