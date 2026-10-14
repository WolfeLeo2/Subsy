package com.wolfeleo2.subsy.ui.detection

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.wolfeleo2.subsy.R
import com.wolfeleo2.subsy.data.model.AppMode
import com.wolfeleo2.subsy.data.model.CategoryRef
import com.wolfeleo2.subsy.data.model.CustomCategory
import com.wolfeleo2.subsy.data.model.CustomCategoryColors
import com.wolfeleo2.subsy.data.model.DetectedKind
import com.wolfeleo2.subsy.data.model.SpendCategory
import com.wolfeleo2.subsy.data.model.TransactionType
import com.wolfeleo2.subsy.data.model.resolveCategory
import com.wolfeleo2.subsy.data.repository.CategoryRepository
import com.wolfeleo2.subsy.data.repository.DetectionInbox
import com.wolfeleo2.subsy.data.repository.SpottedCharge
import com.wolfeleo2.subsy.detection.headline
import com.wolfeleo2.subsy.detection.question
import com.wolfeleo2.subsy.ui.components.CategoryBadge
import com.wolfeleo2.subsy.ui.components.label
import com.wolfeleo2.subsy.ui.theme.Spacing
import com.wolfeleo2.subsy.ui.transactions.CategoryDialog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

class SpottedChargesViewModel(
    private val inbox: DetectionInbox,
    mode: Flow<AppMode?>,
    private val categories: CategoryRepository,
    /** Removes the charge's notification once it's handled in the app. */
    private val onHandled: (String) -> Unit,
) : ViewModel() {
    val charges: StateFlow<List<SpottedCharge>> =
        inbox.pending(mode).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val customCategories: StateFlow<List<CustomCategory>> =
        categories.getCustom().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _picked = MutableStateFlow<Map<String, String>>(emptyMap())

    /** Categories the user picked on a card, by detection id; Log it uses them instead of the proposal. */
    val picked: StateFlow<Map<String, String>> = _picked.asStateFlow()

    fun pick(
        charge: SpottedCharge,
        categoryKey: String,
    ) = _picked.update { it + (charge.detection.id to categoryKey) }

    /** Creates a category from the card and picks it. */
    fun createAndPick(
        charge: SpottedCharge,
        name: String,
        colorHex: String,
    ) {
        val kind = if (charge.detection.kind == DetectedKind.INCOME) TransactionType.INCOME else TransactionType.EXPENSE
        val category = CustomCategory(UUID.randomUUID().toString(), name, kind, colorHex)
        viewModelScope.launch {
            categories.add(category)
            pick(charge, category.key)
        }
    }

    fun log(charge: SpottedCharge) {
        viewModelScope.launch {
            inbox.log(charge, _picked.value[charge.detection.id])
            onHandled(charge.detection.id)
        }
    }

    fun ignore(charge: SpottedCharge) {
        viewModelScope.launch {
            inbox.ignore(charge.detection.id)
            onHandled(charge.detection.id)
        }
    }

    class Factory(
        private val inbox: DetectionInbox,
        private val mode: Flow<AppMode?>,
        private val categories: CategoryRepository,
        private val onHandled: (String) -> Unit,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = SpottedChargesViewModel(inbox, mode, categories, onHandled) as T
    }
}

/**
 * The charges detection spotted and the user hasn't handled, newest first. Each card offers what fits: Log it (with a
 * category for transactions), Track it for a subscription the user doesn't track yet, and Ignore.
 */
@Composable
fun SpottedCharges(
    viewModel: SpottedChargesViewModel,
    onTrack: (detectionId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val charges by viewModel.charges.collectAsStateWithLifecycle()
    val customs by viewModel.customCategories.collectAsStateWithLifecycle()
    val picked by viewModel.picked.collectAsStateWithLifecycle()
    // Nothing at all when there is nothing to act on, so the host list keeps its normal spacing.
    if (charges.isEmpty()) return
    Column(
        modifier = modifier.fillMaxWidth().animateContentSize(MaterialTheme.motionScheme.defaultSpatialSpec()),
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        charges.forEach { charge ->
            SpottedChargeCard(
                charge = charge,
                categoryKey = picked[charge.detection.id] ?: charge.categoryKey,
                customs = customs,
                onPick = { viewModel.pick(charge, it) },
                onCreateCategory = { name, color -> viewModel.createAndPick(charge, name, color) },
                onLog = { viewModel.log(charge) },
                onTrack = { onTrack(charge.detection.id) },
                onIgnore = { viewModel.ignore(charge) },
            )
        }
    }
}

@Composable
private fun SpottedChargeCard(
    charge: SpottedCharge,
    categoryKey: String?,
    customs: List<CustomCategory>,
    onPick: (String) -> Unit,
    onCreateCategory: (name: String, colorHex: String) -> Unit,
    onLog: () -> Unit,
    onTrack: () -> Unit,
    onIgnore: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val res = LocalContext.current.resources
    // Color roles: tertiary is spending, primary is income.
    val income = charge.detection.kind == DetectedKind.INCOME
    val container = if (income) scheme.primaryContainer else scheme.tertiaryContainer
    val content = if (income) scheme.onPrimaryContainer else scheme.onTertiaryContainer
    val strong = if (income) scheme.primary else scheme.tertiary
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = container,
        contentColor = content,
    ) {
        Column(Modifier.padding(Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(charge.headline(res), style = MaterialTheme.typography.titleMediumEmphasized)
            Text(charge.question(res), style = MaterialTheme.typography.bodyMedium)
            if (categoryKey != null) {
                val type = if (income) TransactionType.INCOME else TransactionType.EXPENSE
                CategoryPicker(categoryKey, type, customs, onPick, onCreateCategory, Modifier.padding(top = Spacing.xs))
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.s),
                horizontalArrangement = Arrangement.spacedBy(Spacing.s, Alignment.End),
            ) {
                TextButton(onClick = onIgnore, colors = ButtonDefaults.textButtonColors(contentColor = content)) {
                    Text(stringResource(R.string.detection_action_ignore))
                }
                when {
                    charge.suggestion != null && charge.loggable -> {
                        OutlinedButton(onClick = onLog, colors = ButtonDefaults.outlinedButtonColors(contentColor = content)) {
                            Text(stringResource(R.string.detection_action_log))
                        }
                        Button(onClick = onTrack, colors = ButtonDefaults.buttonColors(containerColor = strong)) {
                            Text(stringResource(R.string.detection_action_track))
                        }
                    }
                    charge.suggestion != null ->
                        Button(onClick = onTrack, colors = ButtonDefaults.buttonColors(containerColor = strong)) {
                            Text(stringResource(R.string.detection_action_track))
                        }
                    else ->
                        Button(onClick = onLog, colors = ButtonDefaults.buttonColors(containerColor = strong)) {
                            Text(stringResource(R.string.detection_action_log))
                        }
                }
            }
        }
    }
}

/** The category Log it will use, changeable before logging; picking one also teaches merchant memory for next time. */
@Composable
private fun CategoryPicker(
    categoryKey: String,
    type: TransactionType,
    customs: List<CustomCategory>,
    onPick: (String) -> Unit,
    onCreate: (name: String, colorHex: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    var creating by rememberSaveable { mutableStateOf(false) }
    val byKey = customs.associateBy { it.key }
    val current = resolveCategory(categoryKey, type, byKey)
    val choices =
        SpendCategory.pickable(type).map { CategoryRef.Builtin(it) } + customs.filter { it.kind == type }.map { CategoryRef.Custom(it) }
    Box(modifier) {
        AssistChip(
            onClick = { open = true },
            label = { Text(current.label()) },
            leadingIcon = { CategoryBadge(current, size = AssistChipDefaults.IconSize) },
            trailingIcon = { Icon(Icons.Rounded.ArrowDropDown, contentDescription = null) },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            choices.forEach { choice ->
                DropdownMenuItem(
                    text = { Text(choice.label()) },
                    leadingIcon = { CategoryBadge(choice, size = AssistChipDefaults.IconSize) },
                    onClick = {
                        onPick(choice.key)
                        open = false
                    },
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.category_dialog_new)) },
                leadingIcon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                onClick = {
                    open = false
                    creating = true
                },
            )
        }
    }
    if (creating) {
        CategoryDialog(
            title = stringResource(R.string.category_dialog_new),
            initialName = "",
            initialColor = CustomCategoryColors.first(),
            onSave = { name, color ->
                onCreate(name, color)
                creating = false
            },
            onDismiss = { creating = false },
        )
    }
}
