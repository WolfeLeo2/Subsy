package com.wolfeleo2.subsy.ui.transactions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wolfeleo2.subsy.R
import com.wolfeleo2.subsy.data.model.CategoryRef
import com.wolfeleo2.subsy.data.model.CustomCategory
import com.wolfeleo2.subsy.data.model.CustomCategoryColors
import com.wolfeleo2.subsy.data.model.SpendCategory
import com.wolfeleo2.subsy.data.model.TransactionType
import com.wolfeleo2.subsy.ui.components.CategoryBadge
import com.wolfeleo2.subsy.ui.components.ConnectedButtonGroup
import com.wolfeleo2.subsy.ui.components.LoadingState
import com.wolfeleo2.subsy.ui.components.label
import com.wolfeleo2.subsy.ui.theme.Spacing

/** The user's own categories: add, rename, recolor, delete. Built-in ones are not listed; they can't change. */
@Composable
fun CategoriesScreen(
    viewModel: CategoriesViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var kind by rememberSaveable { mutableStateOf(TransactionType.EXPENSE) }
    var adding by rememberSaveable { mutableStateOf(false) }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var deletingId by rememberSaveable { mutableStateOf<String?>(null) }

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.categories_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    IconButton(onClick = { adding = true }) {
                        Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.category_dialog_new))
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        val all = categories
        if (all == null) {
            LoadingState(Modifier.padding(innerPadding))
            return@Scaffold
        }
        val shown = all.filter { it.kind == kind }
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
            item(key = "kind") {
                val labels =
                    mapOf(
                        TransactionType.EXPENSE to stringResource(R.string.kind_expense),
                        TransactionType.INCOME to stringResource(R.string.kind_income),
                    )
                ConnectedButtonGroup(
                    options = TransactionType.entries,
                    selectedOption = kind,
                    onOptionSelected = { kind = it },
                    labelProvider = { labels.getValue(it) },
                    modifier = Modifier.padding(vertical = Spacing.s),
                )
            }
            if (shown.isEmpty()) {
                item(key = "empty") {
                    Text(
                        text = stringResource(R.string.categories_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = Spacing.xs, vertical = Spacing.l),
                    )
                }
            }
            itemsIndexed(shown, key = { _, c -> c.id }) { index, category ->
                SegmentedListItem(
                    onClick = { editingId = category.id },
                    shapes = ListItemDefaults.segmentedShapes(index, shown.size),
                    colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    leadingContent = { CategoryBadge(CategoryRef.Custom(category)) },
                    modifier = Modifier.animateItem(),
                ) {
                    Text(category.name)
                }
            }
        }

        if (adding) {
            CategoryDialog(
                title = stringResource(R.string.category_dialog_new),
                initialName = "",
                initialColor = CustomCategoryColors.first(),
                onSave = { name, color ->
                    viewModel.add(name, kind, color)
                    adding = false
                },
                onDismiss = { adding = false },
            )
        }
        all.firstOrNull { it.id == editingId }?.let { category ->
            CategoryDialog(
                title = stringResource(R.string.category_dialog_edit),
                initialName = category.name,
                initialColor = category.colorHex,
                onSave = { name, color ->
                    viewModel.update(category.copy(name = name, colorHex = color))
                    editingId = null
                },
                onDismiss = { editingId = null },
                onDelete = {
                    editingId = null
                    deletingId = category.id
                },
            )
        }
        all.firstOrNull { it.id == deletingId }?.let { category ->
            DeleteCategoryDialog(
                category = category,
                onConfirm = {
                    viewModel.delete(category)
                    deletingId = null
                },
                onDismiss = { deletingId = null },
            )
        }
    }
}

@Composable
private fun DeleteCategoryDialog(
    category: CustomCategory,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.categories_delete)) },
        text = { Text(stringResource(R.string.categories_delete_body, category.name, SpendCategory.fallbackFor(category.kind).label())) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.action_delete)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
