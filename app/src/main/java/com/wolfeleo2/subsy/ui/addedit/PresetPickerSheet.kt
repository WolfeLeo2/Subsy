package com.wolfeleo2.subsy.ui.addedit

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import com.wolfeleo2.subsy.R
import com.wolfeleo2.subsy.data.catalog.PresetServiceCatalog
import com.wolfeleo2.subsy.data.model.Category
import com.wolfeleo2.subsy.data.model.PresetService
import com.wolfeleo2.subsy.ui.components.EmptyState
import com.wolfeleo2.subsy.ui.components.MoneyText
import com.wolfeleo2.subsy.ui.components.ServiceIconDefaults
import com.wolfeleo2.subsy.ui.components.ServiceIconView
import com.wolfeleo2.subsy.ui.components.label
import com.wolfeleo2.subsy.ui.components.suffix
import com.wolfeleo2.subsy.ui.theme.AppTheme
import com.wolfeleo2.subsy.ui.theme.Spacing
import com.wolfeleo2.subsy.ui.theme.tabular

private const val CUSTOM_ROW_KEY = "custom"

@Composable
fun PresetPickerSheet(
    approxPrice: (PresetService) -> String?,
    onDismissRequest: () -> Unit,
    onPresetSelected: (PresetService) -> Unit,
    onCustomSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState =
            rememberBottomSheetState(
                initialValue = SheetValue.Hidden,
                enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
            ),
        modifier = modifier,
    ) {
        PresetPickerContent(
            approxPrice = approxPrice,
            onPresetSelected = onPresetSelected,
            onCustomSelected = onCustomSelected,
        )
    }
}

@Composable
fun PresetPickerContent(
    onPresetSelected: (PresetService) -> Unit,
    approxPrice: (PresetService) -> String? = { null },
    onCustomSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var selectedCategory by rememberSaveable { mutableStateOf<Category?>(null) }
    val presets =
        remember(query, selectedCategory) {
            PresetServiceCatalog.search(query).filter { selectedCategory == null || it.category == selectedCategory }
        }

    Column(modifier.fillMaxHeight()) {
        Text(
            text = stringResource(R.string.addedit_catalog_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = Spacing.gutter).semantics { heading() },
        )
        CatalogSearchField(
            query = query,
            onQueryChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.gutter, vertical = Spacing.m),
        )
        CategoryFilterRow(
            selected = selectedCategory,
            onSelect = { category -> selectedCategory = category.takeIf { it != selectedCategory } },
        )
        if (presets.isEmpty()) {
            val name = query.trim()
            EmptyState(
                icon = Icons.Rounded.SearchOff,
                title = stringResource(R.string.addedit_no_match_title, name),
                body = stringResource(R.string.addedit_no_match_body),
                action = {
                    FilledTonalButton(onClick = { onCustomSelected(name) }) {
                        Text(stringResource(R.string.addedit_add_named, name))
                    }
                },
            )
        } else {
            PresetList(
                approxPrice = approxPrice,
                query = query.trim(),
                presets = presets,
                onPresetSelected = onPresetSelected,
                onCustomSelected = onCustomSelected,
            )
        }
    }
}

@Composable
private fun CatalogSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val keyboard = LocalSoftwareKeyboardController.current
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text(stringResource(R.string.addedit_search_placeholder)) },
        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
        trailingIcon =
            if (query.isNotEmpty()) {
                {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.addedit_clear_search))
                    }
                }
            } else {
                null
            },
        singleLine = true,
        shape = MaterialTheme.shapes.extraLarge,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
        modifier = modifier,
    )
}

@Composable
private fun CategoryFilterRow(
    selected: Category?,
    onSelect: (Category) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = Spacing.gutter),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        Category.entries.forEach { category ->
            FilterChip(
                selected = category == selected,
                onClick = { onSelect(category) },
                label = { Text(category.label()) },
            )
        }
    }
}

@Composable
private fun PresetList(
    approxPrice: (PresetService) -> String?,
    query: String,
    presets: List<PresetService>,
    onPresetSelected: (PresetService) -> Unit,
    onCustomSelected: (String) -> Unit,
) {
    // The "add your own" row and every preset form one segmented group.
    val count = presets.size + 1
    val colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    LazyColumn(
        contentPadding = PaddingValues(start = Spacing.gutter, end = Spacing.gutter, top = Spacing.m, bottom = Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
    ) {
        item(key = CUSTOM_ROW_KEY) {
            SegmentedListItem(
                onClick = { onCustomSelected(query) },
                shapes = ListItemDefaults.segmentedShapes(index = 0, count = count),
                colors = colors,
                leadingContent = { AddOwnIcon() },
                modifier = Modifier.animateItem(),
                content = {
                    Text(
                        if (query.isBlank()) {
                            stringResource(R.string.addedit_add_your_own)
                        } else {
                            stringResource(R.string.addedit_add_named_as_own, query)
                        },
                    )
                },
            )
        }
        itemsIndexed(presets, key = { _, preset -> preset.id }) { index, preset ->
            SegmentedListItem(
                onClick = { onPresetSelected(preset) },
                shapes = ListItemDefaults.segmentedShapes(index = index + 1, count = count),
                colors = colors,
                leadingContent = {
                    ServiceIconView(name = preset.name, colorHex = preset.colorHex, cancellationUrl = preset.cancellationUrl)
                },
                supportingContent = { Text(preset.category.label()) },
                trailingContent = {
                    Column(horizontalAlignment = Alignment.End) {
                        MoneyText(
                            amount = preset.defaultAmount,
                            currency = preset.defaultCurrency,
                            suffix = preset.defaultBillingCycle.suffix(),
                        )
                        approxPrice(preset)?.let {
                            Text(
                                text = stringResource(R.string.addedit_about_price, it),
                                style = MaterialTheme.typography.bodySmall.tabular,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                modifier = Modifier.animateItem(),
                content = { Text(preset.name) },
            )
        }
    }
}

@Composable
private fun AddOwnIcon() {
    Box(
        modifier =
            Modifier
                .size(ServiceIconDefaults.ListSize)
                .clip(ServiceIconDefaults.shape)
                .background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Rounded.Add, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
    }
}

@Preview(showBackground = true)
@Composable
fun PresetPickerSheetPreview() {
    AppTheme {
        PresetPickerContent(
            onPresetSelected = {},
            onCustomSelected = {},
        )
    }
}
