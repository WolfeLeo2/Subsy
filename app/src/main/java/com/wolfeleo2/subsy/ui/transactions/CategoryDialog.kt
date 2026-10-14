package com.wolfeleo2.subsy.ui.transactions

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.wolfeleo2.subsy.R
import com.wolfeleo2.subsy.data.model.CustomCategoryColors
import com.wolfeleo2.subsy.ui.theme.Spacing
import com.wolfeleo2.subsy.ui.util.parseColorHex

/** Name and color for a new or existing custom category. [onDelete] adds a delete button when editing. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CategoryDialog(
    title: String,
    initialName: String,
    initialColor: String,
    onSave: (name: String, colorHex: String) -> Unit,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    var name by rememberSaveable { mutableStateOf(initialName) }
    var color by rememberSaveable { mutableStateOf(initialColor) }
    var tried by rememberSaveable { mutableStateOf(false) }
    val nameMissing = name.isBlank()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.l)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.category_dialog_name)) },
                    singleLine = true,
                    isError = tried && nameMissing,
                    supportingText = if (tried && nameMissing) ({ Text(stringResource(R.string.error_category_name_required)) }) else null,
                )
                Text(stringResource(R.string.category_dialog_color), style = MaterialTheme.typography.labelLarge)
                FlowRow(
                    modifier = Modifier.selectableGroup(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                    verticalArrangement = Arrangement.spacedBy(Spacing.s),
                ) {
                    CustomCategoryColors.forEachIndexed { index, hex ->
                        val swatch = parseColorHex(hex) ?: Color.Gray
                        val label = stringResource(R.string.category_dialog_color_item, index + 1)
                        Box(
                            modifier =
                                Modifier
                                    .size(48.dp)
                                    .background(swatch, CircleShape)
                                    .then(
                                        if (hex ==
                                            color
                                        ) {
                                            Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                        } else {
                                            Modifier
                                        },
                                    ).selectable(selected = hex == color, role = Role.RadioButton, onClick = { color = hex })
                                    .semantics { contentDescription = label },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (hex == color) Icon(Icons.Rounded.Check, contentDescription = null, tint = Color.White)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                tried = true
                if (!nameMissing) onSave(name.trim(), color)
            }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            if (onDelete != null) {
                TextButton(onClick = onDelete) { Text(stringResource(R.string.categories_delete)) }
            } else {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
            }
        },
    )
}
