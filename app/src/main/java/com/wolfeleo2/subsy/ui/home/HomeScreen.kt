package com.wolfeleo2.subsy.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.ViewAgenda
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wolfeleo2.subsy.R
import com.wolfeleo2.subsy.data.model.BillingCycle
import com.wolfeleo2.subsy.data.model.Category
import com.wolfeleo2.subsy.data.model.Subscription
import com.wolfeleo2.subsy.data.model.SubscriptionStatus
import com.wolfeleo2.subsy.ui.components.ConnectedButtonGroup
import com.wolfeleo2.subsy.ui.components.EmptyState
import com.wolfeleo2.subsy.ui.components.LoadingState
import com.wolfeleo2.subsy.ui.components.MoneyText
import com.wolfeleo2.subsy.ui.components.SectionHeader
import com.wolfeleo2.subsy.ui.components.ServiceIconView
import com.wolfeleo2.subsy.ui.components.StatusBadge
import com.wolfeleo2.subsy.ui.components.SubscriptionsHero
import com.wolfeleo2.subsy.ui.components.isUrgent
import com.wolfeleo2.subsy.ui.components.label
import com.wolfeleo2.subsy.ui.components.nextEventLabel
import com.wolfeleo2.subsy.ui.components.suffix
import com.wolfeleo2.subsy.ui.navigation.ORIGIN_HOME
import com.wolfeleo2.subsy.ui.navigation.detailContainer
import com.wolfeleo2.subsy.ui.navigation.detailIcon
import com.wolfeleo2.subsy.ui.navigation.detailTransitionKey
import com.wolfeleo2.subsy.ui.navigation.rootToolbarScroll
import com.wolfeleo2.subsy.ui.theme.AppTheme
import com.wolfeleo2.subsy.ui.theme.Spacing
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onNavigateToAddSubscription: () -> Unit,
    onNavigateToSubscriptionDetail: (Subscription) -> Unit,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    HomeScreenContent(
        state = state,
        onFilterSelected = viewModel::onFilterSelected,
        onDeleteSubscription = viewModel::requestDelete,
        onUndoDeleteSubscription = viewModel::undoDelete,
        onResumeSubscription = viewModel::resumeSubscription,
        onPauseSubscription = viewModel::pauseSubscription,
        onNavigateToAddSubscription = onNavigateToAddSubscription,
        onNavigateToSubscriptionDetail = onNavigateToSubscriptionDetail,
        onNavigateToSettings = onNavigateToSettings,
        modifier = modifier,
    )
}

@Composable
fun HomeScreenContent(
    state: HomeUiState,
    onFilterSelected: (SubscriptionFilter) -> Unit,
    onDeleteSubscription: (String) -> Unit,
    onUndoDeleteSubscription: (String) -> Unit,
    onResumeSubscription: (String) -> Unit,
    onPauseSubscription: (String) -> Unit,
    onNavigateToAddSubscription: () -> Unit,
    onNavigateToSubscriptionDetail: (Subscription) -> Unit,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val deletedMessage = stringResource(R.string.home_deleted)
    val undoLabel = stringResource(R.string.action_undo)

    val onDelete: (Subscription) -> Unit = { sub ->
        onDeleteSubscription(sub.id)
        scope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            val result = snackbarHostState.showSnackbar(deletedMessage.format(sub.name), undoLabel, duration = SnackbarDuration.Long)
            if (result == SnackbarResult.ActionPerformed) onUndoDeleteSubscription(sub.id)
        }
    }
    val onTogglePause: (Subscription) -> Unit = { sub ->
        if (sub.status == SubscriptionStatus.PAUSED) onResumeSubscription(sub.id) else onPauseSubscription(sub.id)
    }

    val topBarScroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = modifier.nestedScroll(topBarScroll.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.nav_home)) },
                scrollBehavior = topBarScroll,
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Rounded.Settings, contentDescription = stringResource(R.string.nav_settings))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState, Modifier.padding(bottom = Spacing.toolbarClearance)) },
    ) { innerPadding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when {
                state.isLoading -> LoadingState()
                !state.hasAnySubscriptions ->
                    EmptyState(
                        icon = Icons.Rounded.ViewAgenda,
                        title = stringResource(R.string.home_empty_title),
                        body = stringResource(R.string.home_empty_body),
                        modifier = Modifier.align(Alignment.Center),
                        action = {
                            Button(onClick = onNavigateToAddSubscription) { Text(stringResource(R.string.action_add_subscription)) }
                        },
                    )
                else ->
                    HomeList(
                        state = state,
                        onFilterSelected = onFilterSelected,
                        onOpen = onNavigateToSubscriptionDetail,
                        onDelete = onDelete,
                        onTogglePause = onTogglePause,
                        modifier = Modifier.rootToolbarScroll(),
                    )
            }
        }
    }
}

@Composable
private fun HomeList(
    state: HomeUiState,
    onFilterSelected: (SubscriptionFilter) -> Unit,
    onOpen: (Subscription) -> Unit,
    onDelete: (Subscription) -> Unit,
    onTogglePause: (Subscription) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = Spacing.gutter, end = Spacing.gutter, top = Spacing.s, bottom = Spacing.toolbarClearance),
    ) {
        item(key = "hero") {
            SubscriptionsHero(
                monthly = state.monthlyBurnRate,
                yearly = state.yearlyProjected,
                currency = state.currency,
                logos = state.heroLogos,
                moreCount = state.heroMoreCount,
                activeCount = state.activeCount,
                trialCount = state.activeTrialsCount,
                trialCost = state.trialConversionCost,
                nextCharge = state.nextCharge,
                onOpenNext = onOpen,
                currencyNote = state.currencyNote,
            )
        }
        item(key = "filters") {
            val labels = SubscriptionFilter.entries.associateWith { stringResource(it.label) }
            ConnectedButtonGroup(
                options = SubscriptionFilter.entries,
                selectedOption = state.selectedFilter,
                onOptionSelected = onFilterSelected,
                labelProvider = { labels.getValue(it) },
                modifier = Modifier.padding(top = Spacing.xl),
            )
        }

        if (state.trials.isNotEmpty()) {
            item(key = "trials-header") { SectionHeader(stringResource(R.string.home_section_trials), Modifier.animateItem()) }
            subscriptionGroup(state.trials, onOpen, onDelete, onTogglePause)
        }
        if (state.subscriptions.isNotEmpty()) {
            item(key = "subs-header") {
                SectionHeader(
                    text = stringResource(R.string.home_section_subscriptions),
                    modifier = Modifier.animateItem(),
                    trailing = {
                        Text(
                            text = pluralStringResource(R.plurals.home_count, state.subscriptions.size, state.subscriptions.size),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                )
            }
            subscriptionGroup(state.subscriptions, onOpen, onDelete, onTogglePause)
        }
        if (state.trials.isEmpty() && state.subscriptions.isEmpty()) {
            item(key = "filter-empty") {
                Text(
                    text = stringResource(R.string.home_filter_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier =
                        Modifier
                            .animateItem()
                            .padding(top = Spacing.xxl)
                            .fillMaxWidth(),
                )
            }
        }
    }
}

private fun LazyListScope.subscriptionGroup(
    subscriptions: List<Subscription>,
    onOpen: (Subscription) -> Unit,
    onDelete: (Subscription) -> Unit,
    onTogglePause: (Subscription) -> Unit,
) {
    itemsIndexed(subscriptions, key = { _, sub -> sub.id }) { index, sub ->
        Column(Modifier.animateItem()) {
            if (index > 0) Spacer(Modifier.height(ListItemDefaults.SegmentedGap))
            SwipeableSubscriptionRow(
                subscription = sub,
                shapes = ListItemDefaults.segmentedShapes(index = index, count = subscriptions.size),
                onOpen = { onOpen(sub) },
                onDelete = { onDelete(sub) },
                onTogglePause = { onTogglePause(sub) },
            )
        }
    }
}

/**
 * Swipe toward the start to delete (undo via snackbar), toward the end to pause or resume.
 * The same actions are exposed as accessibility actions, since swipes aren't discoverable.
 */
@Composable
private fun SwipeableSubscriptionRow(
    subscription: Subscription,
    shapes: ListItemShapes,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
    onTogglePause: () -> Unit,
) {
    val swipeState = rememberSwipeToDismissBoxState()
    val scope = rememberCoroutineScope()
    val isPaused = subscription.status == SubscriptionStatus.PAUSED
    val pauseLabel = stringResource(if (isPaused) R.string.home_action_resume else R.string.home_action_pause)
    val deleteLabel = stringResource(R.string.action_delete)

    SwipeToDismissBox(
        state = swipeState,
        enableDismissFromStartToEnd = subscription.status != SubscriptionStatus.CANCELLED,
        onDismiss = { direction ->
            if (direction == SwipeToDismissBoxValue.EndToStart) {
                onDelete()
            } else {
                onTogglePause()
                scope.launch { swipeState.reset() }
            }
        },
        backgroundContent = {
            val toStart = swipeState.dismissDirection == SwipeToDismissBoxValue.EndToStart
            SwipeBackground(
                shape = shapes.shape,
                color = if (toStart) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
                contentColor = if (toStart) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer,
                alignEnd = toStart,
                icon = {
                    when {
                        toStart -> Icon(Icons.Rounded.Delete, contentDescription = null)
                        isPaused -> Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                        else -> Icon(Icons.Rounded.Pause, contentDescription = null)
                    }
                },
            )
        },
        modifier =
            Modifier.semantics {
                customActions =
                    listOf(
                        CustomAccessibilityAction(pauseLabel) {
                            onTogglePause()
                            true
                        },
                        CustomAccessibilityAction(deleteLabel) {
                            onDelete()
                            true
                        },
                    )
            },
    ) {
        SubscriptionRow(subscription, shapes, onOpen)
    }
}

@Composable
internal fun SwipeBackground(
    shape: Shape,
    color: Color,
    contentColor: Color,
    alignEnd: Boolean,
    icon: @Composable () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .clip(shape)
                .background(color)
                .padding(horizontal = Spacing.xl),
        contentAlignment = if (alignEnd) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) { icon() }
    }
}

/**
 * Trials sit on the tertiary (amber) container, or the error container in their last three days, so
 * they read differently from paid subscriptions at a glance. Paid rows stay neutral.
 */
@Composable
private fun SubscriptionRow(
    subscription: Subscription,
    shapes: ListItemShapes,
    onOpen: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val isTrial = subscription.status == SubscriptionStatus.TRIAL
    val urgent = subscription.isUrgent
    val container =
        when {
            isTrial && urgent -> colors.errorContainer
            isTrial -> colors.tertiaryContainer
            else -> colors.surfaceContainer
        }
    val onContainer =
        when {
            isTrial && urgent -> colors.onErrorContainer
            isTrial -> colors.onTertiaryContainer
            else -> colors.onSurface
        }
    val supportingColor =
        when {
            isTrial -> onContainer
            urgent -> colors.error
            else -> colors.onSurfaceVariant
        }

    val transitionKey = detailTransitionKey(ORIGIN_HOME, subscription.id)
    SegmentedListItem(
        onClick = onOpen,
        shapes = shapes,
        modifier = Modifier.detailContainer(transitionKey, shapes.shape),
        colors =
            ListItemDefaults.segmentedColors(
                containerColor = container,
                contentColor = onContainer,
                supportingContentColor = supportingColor,
                trailingContentColor = onContainer,
            ),
        leadingContent = {
            ServiceIconView(
                name = subscription.name,
                colorHex = subscription.colorHex,
                cancellationUrl = subscription.cancellationUrl,
                modifier = Modifier.detailIcon(transitionKey),
            )
        },
        supportingContent = {
            Text(
                text =
                    if (isTrial) {
                        subscription.nextEventLabel()
                    } else {
                        stringResource(R.string.home_row_supporting, subscription.nextEventLabel(), subscription.category.label())
                    },
                maxLines = 1,
            )
        },
        trailingContent = {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                MoneyText(
                    amount = subscription.amount,
                    currency = subscription.currency,
                    suffix = subscription.billingCycle.suffix(),
                    style = MaterialTheme.typography.titleSmall,
                )
                if (!isTrial) StatusBadge(subscription.status)
            }
        },
    ) {
        Text(subscription.name, maxLines = 1)
    }
}

@Preview(showBackground = true)
@Composable
fun HomeScreenPreview() {
    val sampleTrial =
        Subscription(
            id = "1",
            name = "Apple Music",
            category = Category.ENTERTAINMENT,
            amount = 10.99,
            currency = "USD",
            billingCycle = BillingCycle.MONTHLY,
            startDate = System.currentTimeMillis() - 10L * 24 * 60 * 60 * 1000,
            nextBillingDate = System.currentTimeMillis() + 3L * 24 * 60 * 60 * 1000,
            isFreeTrial = true,
            trialEndDate = System.currentTimeMillis() + 3L * 24 * 60 * 60 * 1000,
            status = SubscriptionStatus.TRIAL,
            colorHex = "#FA243C",
        )
    val sampleSub1 =
        Subscription(
            id = "2",
            name = "Netflix",
            category = Category.ENTERTAINMENT,
            amount = 15.99,
            currency = "USD",
            billingCycle = BillingCycle.MONTHLY,
            startDate = System.currentTimeMillis() - 60L * 24 * 60 * 60 * 1000,
            nextBillingDate = System.currentTimeMillis() + 12L * 24 * 60 * 60 * 1000,
            isFreeTrial = false,
            status = SubscriptionStatus.ACTIVE,
            colorHex = "#E50914",
        )
    val sampleSub2 =
        Subscription(
            id = "3",
            name = "GitHub Pro",
            category = Category.WORK_SAAS,
            amount = 4.00,
            currency = "USD",
            billingCycle = BillingCycle.MONTHLY,
            startDate = System.currentTimeMillis() - 90L * 24 * 60 * 60 * 1000,
            nextBillingDate = System.currentTimeMillis() + 18L * 24 * 60 * 60 * 1000,
            isFreeTrial = false,
            status = SubscriptionStatus.ACTIVE,
            colorHex = "#2DBA4E",
        )

    val sampleUiState =
        HomeUiState(
            trials = listOf(sampleTrial),
            subscriptions = listOf(sampleSub1, sampleSub2),
            selectedFilter = SubscriptionFilter.ALL,
            currency = "USD",
            monthlyBurnRate = 19.99,
            yearlyProjected = 239.88,
            trialConversionCost = 10.99,
            activeTrialsCount = 1,
            hasAnySubscriptions = true,
            isLoading = false,
        )

    AppTheme {
        HomeScreenContent(
            state = sampleUiState,
            onFilterSelected = {},
            onDeleteSubscription = {},
            onUndoDeleteSubscription = {},
            onResumeSubscription = {},
            onPauseSubscription = {},
            onNavigateToAddSubscription = {},
            onNavigateToSubscriptionDetail = {},
            onNavigateToSettings = {},
        )
    }
}
