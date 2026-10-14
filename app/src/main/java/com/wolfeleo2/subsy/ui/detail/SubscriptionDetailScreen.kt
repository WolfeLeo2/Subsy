package com.wolfeleo2.subsy.ui.detail

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SplitButtonDefaults
import androidx.compose.material3.SplitButtonLayout
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wolfeleo2.subsy.R
import com.wolfeleo2.subsy.data.model.BillingCycle
import com.wolfeleo2.subsy.data.model.BillingCycleType
import com.wolfeleo2.subsy.data.model.Category
import com.wolfeleo2.subsy.data.model.PaymentRecord
import com.wolfeleo2.subsy.data.model.PaymentStatus
import com.wolfeleo2.subsy.data.model.Subscription
import com.wolfeleo2.subsy.data.model.SubscriptionStatus
import com.wolfeleo2.subsy.ui.components.EmptyState
import com.wolfeleo2.subsy.ui.components.LoadingState
import com.wolfeleo2.subsy.ui.components.MoneyText
import com.wolfeleo2.subsy.ui.components.SectionHeader
import com.wolfeleo2.subsy.ui.components.ServiceIconDefaults
import com.wolfeleo2.subsy.ui.components.ServiceIconView
import com.wolfeleo2.subsy.ui.components.StatusBadge
import com.wolfeleo2.subsy.ui.components.isUrgent
import com.wolfeleo2.subsy.ui.components.label
import com.wolfeleo2.subsy.ui.components.nextEventLabel
import com.wolfeleo2.subsy.ui.components.suffix
import com.wolfeleo2.subsy.ui.navigation.detailContainer
import com.wolfeleo2.subsy.ui.navigation.detailIcon
import com.wolfeleo2.subsy.ui.theme.AppTheme
import com.wolfeleo2.subsy.ui.theme.Spacing
import com.wolfeleo2.subsy.ui.theme.tabular
import com.wolfeleo2.subsy.ui.util.FormattingUtils
import com.wolfeleo2.subsy.ui.util.openUrl
import kotlinx.coroutines.launch

/** One row in the Details group. [onClick] is set only for rows that do something. */
private data class DetailRow(
    val key: String,
    val label: String,
    val value: String? = null,
    val supporting: String? = null,
    val onClick: (() -> Unit)? = null,
)

/** What the tapped row already knows, so the header draws before the subscription loads. */
data class DetailPreview(
    val transitionKey: String?,
    val name: String,
    val colorHex: String?,
    val cancellationUrl: String?,
)

@Composable
fun SubscriptionDetailScreen(
    viewModel: SubscriptionDetailViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToEdit: (String) -> Unit,
    modifier: Modifier = Modifier,
    preview: DetailPreview? = null,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val resources = LocalResources.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.eventFlow.collect { event ->
            when (event) {
                DetailEvent.Deleted -> onNavigateBack()
                is DetailEvent.PaymentLogged ->
                    launch {
                        snackbarHostState.showSnackbar(
                            resources.getString(R.string.detail_payment_logged, FormattingUtils.formatDate(event.nextBillingDate)),
                        )
                    }
            }
        }
    }

    SubscriptionDetailScreenContent(
        uiState = uiState,
        onNavigateBack = onNavigateBack,
        onNavigateToEdit = onNavigateToEdit,
        onDeleteSubscription = viewModel::deleteSubscription,
        onLogPayment = viewModel::logPaymentAndAdvanceCycle,
        onPauseSubscription = viewModel::pauseSubscription,
        onCancelSubscription = viewModel::cancelSubscription,
        onResumeSubscription = viewModel::resumeSubscription,
        snackbarHostState = snackbarHostState,
        modifier = modifier,
        preview = preview,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscriptionDetailScreenContent(
    uiState: DetailUiState,
    onNavigateBack: () -> Unit,
    onNavigateToEdit: (String) -> Unit,
    onDeleteSubscription: () -> Unit,
    onLogPayment: () -> Unit,
    onPauseSubscription: () -> Unit,
    onCancelSubscription: () -> Unit,
    onResumeSubscription: () -> Unit,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    modifier: Modifier = Modifier,
    preview: DetailPreview? = null,
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var overflowExpanded by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    val launchUrl: (String) -> Unit = { url ->
        if (!openUrl(context, url)) {
            scope.launch { snackbarHostState.showSnackbar(resources.getString(R.string.error_open_link)) }
        }
    }

    val subscription = uiState.subscription

    if (showDeleteDialog && subscription != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.detail_delete_title, subscription.name)) },
            text = { Text(stringResource(R.string.detail_delete_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        onDeleteSubscription()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }

    Scaffold(
        modifier =
            modifier
                .detailContainer(preview?.transitionKey, RectangleShape)
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            LargeFlexibleTopAppBar(
                title = {
                    Text(subscription?.name ?: preview?.name.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                subtitle =
                    if (subscription == null) {
                        null
                    } else {
                        {
                            Text(
                                stringResource(R.string.detail_subtitle, subscription.category.label(), subscription.billingCycle.label()),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    if (subscription != null) {
                        IconButton(onClick = { onNavigateToEdit(subscription.id) }) {
                            Icon(Icons.Rounded.Edit, contentDescription = stringResource(R.string.detail_edit))
                        }
                        Box {
                            IconButton(onClick = { overflowExpanded = true }) {
                                Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.action_more))
                            }
                            DropdownMenu(expanded = overflowExpanded, onDismissRequest = { overflowExpanded = false }) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.action_delete)) },
                                    onClick = {
                                        overflowExpanded = false
                                        showDeleteDialog = true
                                    },
                                )
                            }
                        }
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        when {
            uiState.isLoading && preview != null ->
                // Same spot as the loaded header's icon, so the icon flying in from the row lands in place.
                Box(Modifier.padding(innerPadding).padding(start = Spacing.gutter, top = Spacing.s)) {
                    ServiceIconView(
                        name = preview.name,
                        colorHex = preview.colorHex,
                        cancellationUrl = preview.cancellationUrl,
                        size = ServiceIconDefaults.HeroSize,
                        modifier = Modifier.detailIcon(preview.transitionKey),
                    )
                }
            uiState.isLoading -> LoadingState(Modifier.padding(innerPadding))
            subscription == null ->
                Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                    EmptyState(
                        icon = Icons.Rounded.SearchOff,
                        title = stringResource(R.string.detail_not_found_title),
                        body = stringResource(R.string.detail_not_found_body),
                        action = {
                            Button(onClick = onNavigateBack) { Text(stringResource(R.string.detail_back_to_list)) }
                        },
                    )
                }
            else ->
                DetailContent(
                    subscription = subscription,
                    uiState = uiState,
                    innerPadding = innerPadding,
                    onLogPayment = onLogPayment,
                    onPause = onPauseSubscription,
                    onCancel = onCancelSubscription,
                    onResume = onResumeSubscription,
                    onOpenUrl = launchUrl,
                    transitionKey = preview?.transitionKey,
                )
        }
    }
}

@Composable
private fun DetailContent(
    subscription: Subscription,
    uiState: DetailUiState,
    innerPadding: PaddingValues,
    onLogPayment: () -> Unit,
    onPause: () -> Unit,
    onCancel: () -> Unit,
    onResume: () -> Unit,
    onOpenUrl: (String) -> Unit,
    transitionKey: String?,
) {
    val layoutDirection = LocalLayoutDirection.current
    val detailRows = detailRows(subscription, onOpenUrl)
    val payments = uiState.paymentHistory

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding =
            PaddingValues(
                start = innerPadding.calculateStartPadding(layoutDirection) + Spacing.gutter,
                end = innerPadding.calculateEndPadding(layoutDirection) + Spacing.gutter,
                top = innerPadding.calculateTopPadding() + Spacing.s,
                // Scaffold's inner padding already includes the navigation bar inset.
                bottom = innerPadding.calculateBottomPadding() + Spacing.xl,
            ),
        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
    ) {
        item(key = "hero") { Hero(subscription, transitionKey) }

        item(key = "primary_action") {
            PrimaryAction(
                status = subscription.status,
                isLoggingPayment = uiState.isLoggingPayment,
                onLogPayment = onLogPayment,
                onPause = onPause,
                onCancel = onCancel,
                onResume = onResume,
                modifier = Modifier.animateItem().padding(top = Spacing.l),
            )
        }

        if (subscription.status == SubscriptionStatus.TRIAL) {
            item(key = "trial") {
                TrialCallout(subscription, onOpenUrl, Modifier.animateItem().padding(top = Spacing.l))
            }
        }

        item(key = "details_header") { SectionHeader(stringResource(R.string.detail_section_details)) }
        itemsIndexed(detailRows, key = { _, row -> row.key }) { index, row ->
            DetailRowItem(row, index, detailRows.size, Modifier.animateItem())
        }

        item(key = "payments_header") {
            SectionHeader(
                text = stringResource(R.string.detail_section_payments),
                trailing =
                    if (payments.isEmpty()) {
                        null
                    } else {
                        { MoneyText(uiState.totalSpent, subscription.currency, style = MaterialTheme.typography.labelLarge) }
                    },
            )
        }
        if (payments.isEmpty()) {
            item(key = "payments_empty") {
                Text(
                    text = stringResource(R.string.detail_payments_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Spacing.xs),
                )
            }
        } else {
            itemsIndexed(payments, key = { _, record -> record.id }) { index, record ->
                PaymentRow(record, index, payments.size, Modifier.animateItem())
            }
        }
    }
}

@Composable
private fun Hero(
    subscription: Subscription,
    transitionKey: String?,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
            ServiceIconView(
                name = subscription.name,
                colorHex = subscription.colorHex,
                cancellationUrl = subscription.cancellationUrl,
                size = ServiceIconDefaults.HeroSize,
                modifier = Modifier.detailIcon(transitionKey),
            )
            StatusBadge(subscription.status)
        }
        MoneyText(
            amount = subscription.amount,
            currency = subscription.currency,
            suffix = subscription.billingCycle.suffix(),
            style = MaterialTheme.typography.displaySmallEmphasized,
        )
        Text(
            text = subscription.nextEventLabel(),
            style = MaterialTheme.typography.titleMedium,
            color = if (subscription.isUrgent) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PrimaryAction(
    status: SubscriptionStatus,
    isLoggingPayment: Boolean,
    onLogPayment: () -> Unit,
    onPause: () -> Unit,
    onCancel: () -> Unit,
    onResume: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val height = SplitButtonDefaults.MediumContainerHeight
    when (status) {
        SubscriptionStatus.ACTIVE, SubscriptionStatus.TRIAL -> {
            var menuExpanded by remember { mutableStateOf(false) }
            val rotation by animateFloatAsState(
                targetValue = if (menuExpanded) 180f else 0f,
                animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                label = "chevron",
            )
            SplitButtonLayout(
                modifier = modifier.fillMaxWidth(),
                leadingButton = {
                    SplitButtonDefaults.LeadingButton(
                        onClick = onLogPayment,
                        enabled = !isLoggingPayment,
                        modifier = Modifier.fillMaxWidth().heightIn(min = height),
                        shapes = SplitButtonDefaults.leadingButtonShapesFor(height),
                        contentPadding = SplitButtonDefaults.leadingButtonContentPaddingFor(height),
                    ) {
                        Text(stringResource(R.string.detail_log_payment), style = ButtonDefaults.textStyleFor(height))
                    }
                },
                trailingButton = {
                    Box {
                        SplitButtonDefaults.TrailingButton(
                            checked = menuExpanded,
                            onCheckedChange = { menuExpanded = it },
                            modifier = Modifier.heightIn(min = height),
                            shapes = SplitButtonDefaults.trailingButtonShapesFor(height),
                            contentPadding = SplitButtonDefaults.trailingButtonContentPaddingFor(height),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.KeyboardArrowDown,
                                contentDescription = stringResource(R.string.detail_status_actions),
                                modifier =
                                    Modifier
                                        .size(SplitButtonDefaults.trailingButtonIconSizeFor(height))
                                        .graphicsLayer { rotationZ = rotation },
                            )
                        }
                        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.detail_pause)) },
                                onClick = {
                                    menuExpanded = false
                                    onPause()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.detail_mark_cancelled)) },
                                onClick = {
                                    menuExpanded = false
                                    onCancel()
                                },
                            )
                        }
                    }
                },
            )
        }
        SubscriptionStatus.PAUSED ->
            Button(
                onClick = onResume,
                shapes = ButtonDefaults.shapesFor(height),
                modifier = modifier.fillMaxWidth().heightIn(min = height),
                contentPadding = ButtonDefaults.contentPaddingFor(height),
            ) { Text(stringResource(R.string.detail_resume), style = ButtonDefaults.textStyleFor(height)) }
        SubscriptionStatus.CANCELLED ->
            FilledTonalButton(
                onClick = onResume,
                shapes = ButtonDefaults.shapesFor(height),
                modifier = modifier.fillMaxWidth().heightIn(min = height),
                contentPadding = ButtonDefaults.contentPaddingFor(height),
            ) { Text(stringResource(R.string.detail_reactivate), style = ButtonDefaults.textStyleFor(height)) }
    }
}

@Composable
private fun TrialCallout(
    subscription: Subscription,
    onOpenUrl: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val urgent = subscription.isUrgent
    val chargeDate = subscription.trialEndDate ?: subscription.nextBillingDate
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = if (urgent) colors.errorContainer else colors.tertiaryContainer,
        contentColor = if (urgent) colors.onErrorContainer else colors.onTertiaryContainer,
    ) {
        Column(Modifier.padding(Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
            Text(
                text =
                    stringResource(
                        R.string.detail_trial_charge,
                        FormattingUtils.formatCurrency(subscription.amount, subscription.currency),
                        FormattingUtils.formatDate(chargeDate),
                    ),
                style = MaterialTheme.typography.bodyLarge,
            )
            val url = subscription.cancellationUrl
            if (!url.isNullOrBlank()) {
                Button(
                    onClick = { onOpenUrl(url) },
                    colors =
                        if (urgent) {
                            ButtonDefaults.buttonColors(containerColor = colors.error, contentColor = colors.onError)
                        } else {
                            ButtonDefaults.buttonColors(containerColor = colors.tertiary, contentColor = colors.onTertiary)
                        },
                ) { Text(stringResource(R.string.detail_cancel_on_website)) }
            }
        }
    }
}

@Composable
private fun detailRows(
    subscription: Subscription,
    onOpenUrl: (String) -> Unit,
): List<DetailRow> {
    val rows = mutableListOf<DetailRow>()
    if (subscription.status != SubscriptionStatus.CANCELLED) {
        rows += DetailRow("next", stringResource(R.string.detail_next_charge), FormattingUtils.formatDate(subscription.nextBillingDate))
    }
    rows += DetailRow("billing", stringResource(R.string.detail_billing), subscription.billingCycle.label())
    if (subscription.billingCycle.type != BillingCycleType.MONTHLY) {
        rows +=
            DetailRow(
                "monthly",
                stringResource(R.string.detail_per_month),
                FormattingUtils.formatCurrency(subscription.monthlyCost, subscription.currency),
            )
    }
    rows += DetailRow("since", stringResource(R.string.detail_tracking_since), FormattingUtils.formatDate(subscription.createdAt))
    val url = subscription.cancellationUrl
    if (subscription.status != SubscriptionStatus.TRIAL && !url.isNullOrBlank()) {
        rows +=
            DetailRow(
                key = "manage",
                label = stringResource(R.string.detail_manage_or_cancel),
                supporting = domainOf(url),
                onClick = { onOpenUrl(url) },
            )
    }
    val notes = subscription.notes
    if (!notes.isNullOrBlank()) {
        rows += DetailRow(key = "notes", label = stringResource(R.string.detail_notes), supporting = notes)
    }
    return rows
}

/** "https://www.netflix.com/cancel" -> "netflix.com". */
private fun domainOf(url: String): String =
    url
        .trim()
        .substringAfter("://")
        .substringBefore('/')
        .removePrefix("www.")

@Composable
private fun DetailRowItem(
    row: DetailRow,
    index: Int,
    count: Int,
    modifier: Modifier = Modifier,
) {
    val shapes = ListItemDefaults.segmentedShapes(index = index, count = count)
    val colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    val supporting: (@Composable () -> Unit)? = row.supporting?.let { { Text(it) } }
    val trailing: (@Composable () -> Unit)? =
        when {
            row.onClick != null -> {
                { Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null) }
            }
            row.value != null -> {
                { Text(row.value, style = MaterialTheme.typography.bodyLarge.tabular) }
            }
            else -> null
        }
    val headline: @Composable () -> Unit = { Text(row.label) }
    if (row.onClick != null) {
        SegmentedListItem(
            onClick = row.onClick,
            shapes = shapes,
            colors = colors,
            modifier = modifier,
            supportingContent = supporting,
            trailingContent = trailing,
            content = headline,
        )
    } else {
        SegmentedListItem(
            shapes = shapes,
            colors = colors,
            modifier = modifier,
            supportingContent = supporting,
            trailingContent = trailing,
            content = headline,
        )
    }
}

@Composable
private fun PaymentRow(
    record: PaymentRecord,
    index: Int,
    count: Int,
    modifier: Modifier = Modifier,
) {
    SegmentedListItem(
        modifier = modifier,
        shapes = ListItemDefaults.segmentedShapes(index = index, count = count),
        colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        supportingContent = {
            Text(
                stringResource(
                    when (record.status) {
                        PaymentStatus.MANUALLY_LOGGED -> R.string.detail_payment_manual
                        PaymentStatus.AUTO_LOGGED -> R.string.detail_payment_auto
                        PaymentStatus.CONFIRMED -> R.string.detail_payment_confirmed
                    },
                ),
            )
        },
        trailingContent = {
            MoneyText(record.amount, record.currency, style = MaterialTheme.typography.bodyLarge)
        },
        content = { Text(FormattingUtils.formatDate(record.paymentDate)) },
    )
}

@Preview(showBackground = true)
@Composable
fun SubscriptionDetailScreenPreview() {
    val sampleSubscription =
        Subscription(
            id = "1",
            name = "Netflix",
            category = Category.ENTERTAINMENT,
            amount = 15.99,
            currency = "USD",
            billingCycle = BillingCycle.MONTHLY,
            startDate = System.currentTimeMillis() - 180L * 24 * 60 * 60 * 1000,
            nextBillingDate = System.currentTimeMillis() + 15L * 24 * 60 * 60 * 1000,
            isFreeTrial = false,
            status = SubscriptionStatus.ACTIVE,
            cancellationUrl = "https://www.netflix.com/cancel",
            colorHex = "#E50914",
            notes = "Standard plan with 1080p video",
        )

    val samplePayments =
        listOf(
            PaymentRecord(
                id = "p1",
                subscriptionId = "1",
                amount = 15.99,
                currency = "USD",
                paymentDate = System.currentTimeMillis() - 15L * 24 * 60 * 60 * 1000,
                status = PaymentStatus.AUTO_LOGGED,
            ),
            PaymentRecord(
                id = "p2",
                subscriptionId = "1",
                amount = 15.99,
                currency = "USD",
                paymentDate = System.currentTimeMillis() - 45L * 24 * 60 * 60 * 1000,
                status = PaymentStatus.MANUALLY_LOGGED,
            ),
        )

    val sampleUiState =
        DetailUiState(
            subscription = sampleSubscription,
            paymentHistory = samplePayments,
            totalSpent = 31.98,
            isLoading = false,
        )

    AppTheme {
        SubscriptionDetailScreenContent(
            uiState = sampleUiState,
            onNavigateBack = {},
            onNavigateToEdit = {},
            onDeleteSubscription = {},
            onLogPayment = {},
            onPauseSubscription = {},
            onCancelSubscription = {},
            onResumeSubscription = {},
        )
    }
}
