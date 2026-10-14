package com.wolfeleo2.subsy.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.FloatingToolbarExitDirection
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.wolfeleo2.subsy.R
import com.wolfeleo2.subsy.SubsyApplication
import com.wolfeleo2.subsy.data.model.AppMode
import com.wolfeleo2.subsy.data.model.Subscription
import com.wolfeleo2.subsy.ui.addedit.AddEditSubscriptionScreen
import com.wolfeleo2.subsy.ui.addedit.AddEditViewModel
import com.wolfeleo2.subsy.ui.analytics.AnalyticsScreen
import com.wolfeleo2.subsy.ui.analytics.AnalyticsViewModel
import com.wolfeleo2.subsy.ui.detail.DetailPreview
import com.wolfeleo2.subsy.ui.detail.SubscriptionDetailScreen
import com.wolfeleo2.subsy.ui.detail.SubscriptionDetailViewModel
import com.wolfeleo2.subsy.ui.detection.SpottedCharges
import com.wolfeleo2.subsy.ui.detection.SpottedChargesViewModel
import com.wolfeleo2.subsy.ui.home.HomeScreen
import com.wolfeleo2.subsy.ui.home.HomeViewModel
import com.wolfeleo2.subsy.ui.settings.SettingsScreen
import com.wolfeleo2.subsy.ui.settings.SettingsViewModel
import com.wolfeleo2.subsy.ui.theme.Spacing
import com.wolfeleo2.subsy.ui.transactions.CategoriesScreen
import com.wolfeleo2.subsy.ui.transactions.CategoriesViewModel
import com.wolfeleo2.subsy.ui.transactions.TransactionSheet
import com.wolfeleo2.subsy.ui.transactions.TransactionSheetViewModel
import com.wolfeleo2.subsy.ui.transactions.TransactionsScreen
import com.wolfeleo2.subsy.ui.transactions.TransactionsViewModel
import com.wolfeleo2.subsy.worker.DetectionNotifier
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

const val ORIGIN_HOME = "home"
const val ORIGIN_ANALYTICS = "analytics"

// Spatial spring matching MotionScheme.expressive()'s default spatial spec. NavHost transition
// lambdas aren't composable, so the theme's motionScheme can't be read here.
private val spatialSpring = spring<IntOffset>(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)
private val effectsSpring = spring<Float>(stiffness = Spring.StiffnessMedium)

// Material fade-through for switching between root screens: the old one fades out in the first 90ms,
// then the new one fades in while growing from 92%. Fixed durations, as the pattern is specified.
private const val FADE_THROUGH_OUT_MS = 90
private const val FADE_THROUGH_IN_MS = 210
private const val FADE_THROUGH_SCALE = 0.92f

private fun NavBackStackEntry.isRoot(): Boolean =
    destination.hasRoute<Screen.Home>() || destination.hasRoute<Screen.Transactions>() || destination.hasRoute<Screen.Analytics>()

private fun NavBackStackEntry.isDetail(): Boolean = destination.hasRoute<Screen.SubscriptionDetail>()

private fun AnimatedContentTransitionScope<NavBackStackEntry>.isRootSwitch(): Boolean = initialState.isRoot() && targetState.isRoot()

/** A root screen opening Detail or Detail returning to it: the container transform does the motion. */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.isContainerTransform(): Boolean =
    (initialState.isRoot() && targetState.isDetail()) || (initialState.isDetail() && targetState.isRoot())

private val fadeThroughIn =
    fadeIn(tween(FADE_THROUGH_IN_MS, delayMillis = FADE_THROUGH_OUT_MS, easing = LinearOutSlowInEasing)) +
        scaleIn(tween(FADE_THROUGH_IN_MS, delayMillis = FADE_THROUGH_OUT_MS, easing = LinearOutSlowInEasing), FADE_THROUGH_SCALE)
private val fadeThroughOut = fadeOut(tween(FADE_THROUGH_OUT_MS, easing = FastOutLinearInEasing))

private val enter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
    when {
        isRootSwitch() -> fadeThroughIn
        isContainerTransform() -> fadeIn(effectsSpring)
        else -> slideInHorizontally(spatialSpring) { it / 4 } + fadeIn(effectsSpring)
    }
}
private val exit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
    when {
        isRootSwitch() -> fadeThroughOut
        isContainerTransform() -> fadeOut(effectsSpring)
        else -> slideOutHorizontally(spatialSpring) { -it / 8 } + fadeOut(effectsSpring)
    }
}
private val popEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
    when {
        isRootSwitch() -> fadeThroughIn
        isContainerTransform() -> fadeIn(effectsSpring)
        else -> slideInHorizontally(spatialSpring) { -it / 8 } + fadeIn(effectsSpring)
    }
}
private val popExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
    when {
        isRootSwitch() -> fadeThroughOut
        isContainerTransform() -> fadeOut(effectsSpring)
        else -> slideOutHorizontally(spatialSpring) { it / 4 } + fadeOut(effectsSpring)
    }
}

@Composable
fun SubsyNavGraph(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    val app = LocalContext.current.applicationContext as SubsyApplication
    val primaryCurrency = app.settingsRepository.settings.map { it.primaryCurrency }
    val mode by remember { app.settingsRepository.settings.map { it.appMode } }.collectAsStateWithLifecycle(null)
    val allSpending = mode == AppMode.ALL_SPENDING
    val tabs = if (allSpending) RootTab.entries else listOf(RootTab.HOME, RootTab.ANALYTICS)
    // The open transaction sheet: null when closed. Each opening gets its own ViewModel through [SheetTarget.session].
    var sheet by remember { mutableStateOf<SheetTarget?>(null) }

    val selectTab: (RootTab) -> Unit = { tab ->
        val route: Screen =
            when (tab) {
                RootTab.HOME -> Screen.Home
                RootTab.TRANSACTIONS -> Screen.Transactions
                RootTab.ANALYTICS -> Screen.Analytics
            }
        navController.navigate(route) {
            popUpTo(Screen.Home) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }
    val openAdd = { navController.navigate(Screen.AddSubscription) }
    val openDetail = { origin: String ->
        { sub: Subscription ->
            navController.navigate(Screen.SubscriptionDetail(sub.id, origin, sub.name, sub.colorHex, sub.cancellationUrl))
        }
    }

    val destination = navController.currentBackStackEntryAsState().value?.destination
    val currentTab =
        when {
            destination == null -> null
            destination.hasRoute<Screen.Home>() -> RootTab.HOME
            destination.hasRoute<Screen.Transactions>() -> RootTab.TRANSACTIONS
            destination.hasRoute<Screen.Analytics>() -> RootTab.ANALYTICS
            else -> null
        }
    // Switching back to Subscriptions mode in Settings hides the Transactions tab; don't strand the user on it.
    LaunchedEffect(allSpending, currentTab) {
        if (mode != null && !allSpending && currentTab == RootTab.TRANSACTIONS) selectTab(RootTab.HOME)
    }
    val openSheet = { id: String? -> sheet = SheetTarget(id) }
    // Keeps the last tab highlighted while the toolbar animates out to a non-root screen.
    var shownTab by remember { mutableStateOf(RootTab.HOME) }
    if (currentTab != null) shownTab = currentTab
    // Home adds subscriptions; in All spending mode the other tabs add transactions.
    val addsTransaction = allSpending && shownTab != RootTab.HOME
    val toolbarScroll = FloatingToolbarDefaults.exitAlwaysScrollBehavior(exitDirection = FloatingToolbarExitDirection.Bottom)

    SharedTransitionLayout(modifier) {
        Box(Modifier.fillMaxSize()) {
            CompositionLocalProvider(
                LocalSharedTransitionScope provides this@SharedTransitionLayout,
                LocalRootToolbarScroll provides toolbarScroll,
            ) {
                SubsyNavHost(navController, app, primaryCurrency, openAdd, openDetail, openSheet)
            }
            AnimatedVisibility(
                visible = currentTab != null,
                enter = fadeIn(effectsSpring),
                exit = fadeOut(effectsSpring),
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(bottom = Spacing.l),
            ) {
                RootToolbar(
                    tabs = tabs,
                    current = shownTab,
                    onSelect = selectTab,
                    addLabel = stringResource(if (addsTransaction) R.string.action_add_transaction else R.string.action_add_subscription),
                    onAdd = { if (addsTransaction) openSheet(null) else openAdd() },
                    scrollBehavior = toolbarScroll,
                )
            }
        }
    }

    sheet?.let { target ->
        TransactionSheet(
            viewModel =
                viewModel(
                    key = "transaction_sheet_${target.session}",
                    factory =
                        TransactionSheetViewModel.Factory(
                            target.transactionId,
                            app.transactionRepository,
                            app.categoryRepository,
                            primaryCurrency,
                        ),
                ),
            onDismiss = { sheet = null },
        )
    }
}

/** What the transaction sheet is showing; [session] keeps a closed sheet's ViewModel from being reused. */
private data class SheetTarget(
    val transactionId: String?,
    val session: String = UUID.randomUUID().toString(),
)

@Composable
private fun SubsyNavHost(
    navController: NavHostController,
    app: SubsyApplication,
    primaryCurrency: Flow<String>,
    openAdd: () -> Unit,
    openDetail: (origin: String) -> (Subscription) -> Unit,
    openSheet: (transactionId: String?) -> Unit,
) {
    // Home and Transactions each show the charges detection spotted, with one view model per screen.
    val spotted: @Composable (Modifier) -> Unit = { modifier ->
        SpottedCharges(
            onTrack = { navController.navigate(Screen.TrackSpottedCharge(it)) },
            viewModel =
                viewModel(
                    factory =
                        SpottedChargesViewModel.Factory(
                            app.detectionInbox,
                            app.settingsRepository.settings.map { it.appMode },
                            app.categoryRepository,
                        ) {
                            DetectionNotifier(app).cancel(it)
                        },
                ),
            modifier = modifier,
        )
    }
    NavHost(
        navController = navController,
        startDestination = Screen.Home,
        enterTransition = enter,
        exitTransition = exit,
        popEnterTransition = popEnter,
        popExitTransition = popExit,
    ) {
        composable<Screen.Home> {
            CompositionLocalProvider(LocalNavAnimatedScope provides this) {
                HomeScreen(
                    viewModel =
                        viewModel(
                            factory = HomeViewModel.Factory(app.subscriptionRepository, primaryCurrency, app.exchangeRateRepository.rates),
                        ),
                    onNavigateToAddSubscription = openAdd,
                    onNavigateToSubscriptionDetail = openDetail(ORIGIN_HOME),
                    onNavigateToSettings = { navController.navigate(Screen.Settings) },
                    spotted = spotted,
                )
            }
        }

        composable<Screen.Analytics> {
            CompositionLocalProvider(LocalNavAnimatedScope provides this) {
                AnalyticsScreen(
                    viewModel =
                        viewModel(
                            factory =
                                AnalyticsViewModel.Factory(
                                    app.subscriptionRepository,
                                    primaryCurrency,
                                    app.exchangeRateRepository.rates,
                                    app.paymentRecordRepository.getAllPaymentRecords(),
                                    app.settingsRepository.settings.map { it.budgetPlan },
                                    app.settingsRepository.settings.map { it.spreadLongCharges },
                                    app.settingsRepository.settings.map { it.appMode },
                                    app.transactionRepository.getAll(),
                                    app.categoryRepository.getCustom(),
                                    app.budgetRepository,
                                    app.settingsRepository::setMonthBudget,
                                ),
                        ),
                    onNavigateToDetail = openDetail(ORIGIN_ANALYTICS),
                    onAdd = openAdd,
                )
            }
        }

        composable<Screen.AddSubscription> {
            AddEditSubscriptionScreen(
                viewModel =
                    viewModel(
                        factory =
                            AddEditViewModel.Factory(
                                app.subscriptionRepository,
                                null,
                                primaryCurrency,
                                app.exchangeRateRepository.rates,
                            ),
                    ),
                onNavigateBack = { navController.popBackStack() },
            )
        }

        composable<Screen.TrackSpottedCharge> { backStackEntry ->
            val route = backStackEntry.toRoute<Screen.TrackSpottedCharge>()
            AddEditSubscriptionScreen(
                viewModel =
                    viewModel(
                        factory =
                            AddEditViewModel.Factory(
                                app.subscriptionRepository,
                                null,
                                primaryCurrency,
                                app.exchangeRateRepository.rates,
                                trackDetectionId = route.detectionId,
                                inbox = app.detectionInbox,
                            ),
                    ),
                onNavigateBack = { navController.popBackStack() },
            )
        }

        composable<Screen.EditSubscription> { backStackEntry ->
            val route: Screen.EditSubscription = backStackEntry.toRoute()
            AddEditSubscriptionScreen(
                viewModel =
                    viewModel(
                        key = "edit_${route.subscriptionId}",
                        factory =
                            AddEditViewModel.Factory(
                                app.subscriptionRepository,
                                route.subscriptionId,
                                primaryCurrency,
                                app.exchangeRateRepository.rates,
                            ),
                    ),
                onNavigateBack = { navController.popBackStack() },
            )
        }

        composable<Screen.SubscriptionDetail> { backStackEntry ->
            val route: Screen.SubscriptionDetail = backStackEntry.toRoute()
            CompositionLocalProvider(LocalNavAnimatedScope provides this) {
                SubscriptionDetailScreen(
                    viewModel =
                        viewModel(
                            key = "detail_${route.subscriptionId}",
                            factory =
                                SubscriptionDetailViewModel.Factory(
                                    route.subscriptionId,
                                    app.subscriptionRepository,
                                    app.paymentRecordRepository,
                                ),
                        ),
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToEdit = { id -> navController.navigate(Screen.EditSubscription(id)) },
                    preview =
                        DetailPreview(
                            transitionKey = route.origin.takeIf { it.isNotEmpty() }?.let { detailTransitionKey(it, route.subscriptionId) },
                            name = route.name,
                            colorHex = route.colorHex,
                            cancellationUrl = route.cancellationUrl,
                        ),
                )
            }
        }

        composable<Screen.Settings> {
            SettingsScreen(
                viewModel =
                    viewModel(
                        factory =
                            SettingsViewModel.Factory(
                                app.settingsRepository,
                                app.exchangeRateRepository.rates,
                                app.modelRepository,
                                app.detectorRepository,
                            ),
                    ),
                onNavigateBack = { navController.popBackStack() },
                onOpenCategories = { navController.navigate(Screen.Categories) },
            )
        }

        composable<Screen.Transactions> {
            TransactionsScreen(
                viewModel =
                    viewModel(
                        factory =
                            TransactionsViewModel.Factory(
                                app.transactionRepository,
                                app.paymentRecordRepository,
                                app.subscriptionRepository,
                                app.categoryRepository,
                                primaryCurrency,
                                app.exchangeRateRepository.rates,
                                app.settingsRepository.settings.map { it.budgetPlan },
                            ),
                    ),
                onEdit = { openSheet(it.id) },
                // No shared element on these rows, so Detail opens without a container transform.
                onOpenSubscription = openDetail(""),
                onAdd = { openSheet(null) },
                spotted = spotted,
            )
        }

        composable<Screen.Categories> {
            CategoriesScreen(
                viewModel = viewModel(factory = CategoriesViewModel.Factory(app.categoryRepository)),
                onNavigateBack = { navController.popBackStack() },
            )
        }
    }
}
