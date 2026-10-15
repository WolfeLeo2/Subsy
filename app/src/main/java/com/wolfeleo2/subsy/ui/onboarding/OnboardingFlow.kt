package com.wolfeleo2.subsy.ui.onboarding

import android.provider.Settings
import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.RoundedPolygon
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wolfeleo2.subsy.R
import com.wolfeleo2.subsy.data.model.AppMode
import com.wolfeleo2.subsy.data.model.DetectedTransaction
import com.wolfeleo2.subsy.data.model.TransactionSource
import com.wolfeleo2.subsy.data.repository.SpottedCharge
import com.wolfeleo2.subsy.detection.DetectorRelease
import com.wolfeleo2.subsy.detection.DetectorState
import com.wolfeleo2.subsy.ui.detection.SpottedChargeCard
import com.wolfeleo2.subsy.ui.theme.Spacing
import com.wolfeleo2.subsy.ui.theme.SubsyWordmark
import com.wolfeleo2.subsy.ui.util.currencyChoices
import com.wolfeleo2.subsy.ui.util.currencyName
import com.wolfeleo2.subsy.ui.util.hasNotificationAccess
import com.wolfeleo2.subsy.ui.util.hasSmsAccess
import com.wolfeleo2.subsy.ui.util.openAppInfo
import com.wolfeleo2.subsy.ui.util.openNotificationAccess
import com.wolfeleo2.subsy.ui.util.rememberSmsRequest
import kotlinx.coroutines.delay

// The wavy bar covers Mode through Name; Welcome and the last step have none.
private val PROGRESS_STEPS = OnboardingStep.MODE..OnboardingStep.NAME
private const val SLIDE_DIVISOR = 4
private const val DEMO_SMS_MS = 2_200L

/** First run: a wizard of one step per screen. Each step saves its answer; [onFinish] gets what the last screen opens. */
@Composable
fun OnboardingFlow(
    viewModel: OnboardingViewModel,
    onFinish: (FirstAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    BackHandler(enabled = state.step != OnboardingStep.WELCOME) { viewModel.back() }
    val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<IntOffset>()
    val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    val progress by animateFloatAsState(
        state.step.ordinal / (PROGRESS_STEPS.endInclusive.ordinal + 1f),
        MaterialTheme.motionScheme.defaultEffectsSpec(),
    )
    Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.safeDrawingPadding()) {
            AnimatedVisibility(state.step in PROGRESS_STEPS) {
                LinearWavyProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.xl, vertical = Spacing.l),
                )
            }
            AnimatedContent(
                targetState = state.step,
                transitionSpec = {
                    // Shared axis X: the new step slides in from the side the user is heading.
                    val direction = if (targetState.ordinal > initialState.ordinal) 1 else -1
                    (slideInHorizontally(spatial) { it / SLIDE_DIVISOR * direction } + fadeIn(effects)) togetherWith
                        (slideOutHorizontally(spatial) { -it / SLIDE_DIVISOR * direction } + fadeOut(effects))
                },
                modifier = Modifier.weight(1f),
                label = "onboarding step",
            ) { step ->
                Step(step, state, viewModel, onFinish)
            }
        }
    }
}

@Composable
private fun Step(
    step: OnboardingStep,
    state: OnboardingState,
    vm: OnboardingViewModel,
    onFinish: (FirstAction) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    when (step) {
        OnboardingStep.WELCOME ->
            StepLayout(
                title = stringResource(R.string.onboarding_welcome_title),
                body = stringResource(R.string.onboarding_welcome_body),
                hero = {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = SubsyWordmark,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.semantics { heading() },
                    )
                    WelcomeCollage()
                },
                actions = { PrimaryAction(R.string.onboarding_get_started, vm::next) },
            )
        OnboardingStep.MODE ->
            StepLayout(
                title = stringResource(R.string.onboarding_title),
                body = stringResource(R.string.onboarding_body),
                actions = { PrimaryAction(R.string.onboarding_continue, vm::next) },
            ) { ModeChoices(state.mode, vm::setMode) }
        OnboardingStep.CURRENCY ->
            StepLayout(
                title = stringResource(R.string.onboarding_currency_title),
                body = stringResource(R.string.onboarding_currency_body),
                hero = {
                    Hero(
                        Icons.Rounded.Payments,
                        MaterialShapes.Cookie9Sided,
                        scheme.secondaryContainer,
                        scheme.onSecondaryContainer,
                    )
                },
                pinHeader = true,
                actions = { PrimaryAction(R.string.onboarding_continue, vm::next) },
            ) {
                Column(Modifier.selectableGroup()) {
                    currencyChoices(state.currency).forEach { code ->
                        ListItem(
                            selected = code == state.currency,
                            onClick = { vm.setCurrency(code) },
                            leadingContent = { RadioButton(selected = code == state.currency, onClick = null) },
                            supportingContent = { Text(currencyName(code)) },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        ) {
                            Text(code)
                        }
                    }
                }
            }
        OnboardingStep.DEMO ->
            StepLayout(
                title = stringResource(R.string.onboarding_demo_title),
                body =
                    stringResource(
                        R.string.onboarding_demo_body,
                        Formatter.formatShortFileSize(LocalContext.current, DetectorRelease.totalBytes),
                    ),
                actions = {
                    if (state.detectionOn) {
                        PrimaryAction(R.string.onboarding_continue, vm::skip)
                        SecondaryAction(R.string.onboarding_turn_off, vm::skipDetection)
                    } else {
                        PrimaryAction(R.string.onboarding_turn_on, vm::turnOnDetection)
                        SecondaryAction(R.string.onboarding_maybe_later, vm::skipDetection)
                    }
                },
            ) {
                DetectionDemo()
                if (state.detectionOn) StatusLine(stringResource(R.string.onboarding_detection_on))
            }
        OnboardingStep.PRIVACY -> PrivacyStep(vm)
        OnboardingStep.NOTIFICATIONS -> NotificationsStep(vm)
        OnboardingStep.NAME ->
            StepLayout(
                title = stringResource(R.string.onboarding_name_title),
                body = stringResource(R.string.onboarding_name_body),
                hero = { Hero(Icons.Rounded.Person, MaterialShapes.Cookie6Sided, scheme.tertiaryContainer, scheme.onTertiaryContainer) },
                actions = {
                    PrimaryAction(R.string.onboarding_continue, vm::next)
                    SecondaryAction(R.string.onboarding_skip, vm::skip)
                },
            ) {
                OutlinedTextField(
                    value = state.name,
                    onValueChange = vm::setName,
                    label = { Text(stringResource(R.string.detection_holder)) },
                    supportingText = { Text(stringResource(R.string.detection_holder_hint)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { vm.next() }),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        OnboardingStep.FIRST_ACTION -> FirstActionStep(state.mode, onFinish)
    }
}

@Composable
private fun PrivacyStep(vm: OnboardingViewModel) {
    val context = LocalContext.current
    val modelState by vm.modelState.collectAsStateWithLifecycle()
    var denied by rememberSaveable { mutableStateOf(false) }
    var allowed by remember { mutableStateOf(hasSmsAccess(context)) }
    val sms =
        rememberSmsRequest { granted ->
            allowed = granted
            if (granted) vm.next() else denied = true
        }
    // Back from App info with the permission granted there: carry on.
    LifecycleResumeEffect(Unit) {
        allowed = hasSmsAccess(context)
        if (denied && allowed) vm.next()
        onPauseOrDispose {}
    }
    val scheme = MaterialTheme.colorScheme
    StepLayout(
        title = stringResource(R.string.onboarding_privacy_title),
        body = stringResource(R.string.onboarding_privacy_body),
        hero = { Hero(Icons.Rounded.Lock, MaterialShapes.Clover4Leaf, scheme.primaryContainer, scheme.onPrimaryContainer) },
        actions = {
            if (allowed) {
                PrimaryAction(R.string.onboarding_continue, vm::next)
            } else {
                PrimaryAction(if (sms.blocked) R.string.onboarding_open_app_info else R.string.onboarding_allow_sms, sms.launch)
                SecondaryAction(R.string.onboarding_skip, vm::skip)
            }
        },
    ) {
        if (allowed) StatusLine(stringResource(R.string.onboarding_access_allowed))
        modelStatus(modelState)?.let { StatusLine(it) }
        if (denied && !allowed) RestrictedHelp()
    }
}

@Composable
private fun NotificationsStep(vm: OnboardingViewModel) {
    val context = LocalContext.current
    var opened by rememberSaveable { mutableStateOf(false) }
    var denied by rememberSaveable { mutableStateOf(false) }
    var allowed by remember { mutableStateOf(hasNotificationAccess(context)) }
    // Android's settings page has no result, so check on coming back.
    LifecycleResumeEffect(Unit) {
        allowed = hasNotificationAccess(context)
        if (opened) {
            if (allowed) vm.next() else denied = true
        }
        onPauseOrDispose {}
    }
    val scheme = MaterialTheme.colorScheme
    StepLayout(
        title = stringResource(R.string.onboarding_notifications_title),
        body = stringResource(R.string.onboarding_notifications_body),
        hero = { Hero(Icons.Rounded.Notifications, MaterialShapes.Sunny, scheme.tertiaryContainer, scheme.onTertiaryContainer) },
        actions = {
            if (allowed) {
                PrimaryAction(R.string.onboarding_continue, vm::next)
            } else {
                PrimaryAction(
                    R.string.onboarding_open_settings,
                    {
                        opened = true
                        openNotificationAccess(context)
                    },
                )
                SecondaryAction(R.string.onboarding_skip, vm::skip)
            }
        },
    ) {
        if (allowed) StatusLine(stringResource(R.string.onboarding_access_allowed))
        if (denied && !allowed) RestrictedHelp()
    }
}

@Composable
private fun FirstActionStep(
    mode: AppMode,
    onFinish: (FirstAction) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val spending = mode == AppMode.ALL_SPENDING
    StepLayout(
        title = stringResource(R.string.onboarding_first_title),
        body = stringResource(if (spending) R.string.onboarding_first_body_spending else R.string.onboarding_first_body_subscriptions),
        hero = { Hero(Icons.Rounded.Check, MaterialShapes.SoftBurst, scheme.primaryContainer, scheme.onPrimaryContainer) },
        actions = {
            if (spending) {
                PrimaryAction(R.string.onboarding_first_log_expense) { onFinish(FirstAction.LOG_EXPENSE) }
                OutlinedButton(onClick = { onFinish(FirstAction.ADD_SUBSCRIPTION) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.onboarding_first_add_subscription_secondary))
                }
            } else {
                PrimaryAction(R.string.onboarding_first_add_subscription) { onFinish(FirstAction.ADD_SUBSCRIPTION) }
            }
            SecondaryAction(R.string.onboarding_first_later) { onFinish(FirstAction.NONE) }
        },
    )
}

/** One step: a scrolling title, body and content, with the actions pinned below. */
@Composable
private fun StepLayout(
    title: String,
    body: String,
    actions: @Composable ColumnScope.() -> Unit,
    modifier: Modifier = Modifier,
    hero: (@Composable () -> Unit)? = null,
    pinHeader: Boolean = false,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    val header: @Composable ColumnScope.() -> Unit = {
        hero?.invoke()
        Text(
            text = title,
            style = MaterialTheme.typography.displaySmallEmphasized,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = Spacing.s),
        )
    }
    Column(modifier.fillMaxSize().padding(horizontal = Spacing.xl, vertical = Spacing.l)) {
        // Pinned, the hero, title and body stay put while only the content scrolls.
        if (pinHeader) Column(verticalArrangement = Arrangement.spacedBy(Spacing.m), content = header)
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            if (!pinHeader) header()
            content()
        }
        Column(Modifier.padding(top = Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.xs), content = actions)
    }
}

@Composable
private fun StatusLine(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun Hero(
    icon: ImageVector,
    shape: RoundedPolygon,
    container: Color,
    content: Color,
) {
    Box(
        modifier = Modifier.size(Spacing.xxl * 3).background(container, shape.toShape()),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(Spacing.xxl))
    }
}

@Composable
private fun PrimaryAction(
    label: Int,
    onClick: () -> Unit,
) {
    Button(onClick = onClick, modifier = Modifier.fillMaxWidth()) { Text(stringResource(label)) }
}

@Composable
private fun SecondaryAction(
    label: Int,
    onClick: () -> Unit,
) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) { Text(stringResource(label)) }
}

@Composable
private fun modelStatus(state: DetectorState): String? =
    when (state) {
        DetectorState.Absent -> null
        DetectorState.Waiting -> stringResource(R.string.detection_waiting)
        is DetectorState.Downloading ->
            stringResource(
                R.string.detection_downloading,
                (state.doneBytes * 100 / DetectorRelease.totalBytes).toInt(),
            )
        DetectorState.Ready -> stringResource(R.string.onboarding_model_ready)
        DetectorState.Failed -> stringResource(R.string.detection_failed)
    }

/** Shown after a grant didn't go through: apps installed outside Play have SMS and notification access restricted. */
@Composable
private fun RestrictedHelp() {
    val context = LocalContext.current
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Text(stringResource(R.string.onboarding_restricted_help), style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(onClick = { openAppInfo(context) }) { Text(stringResource(R.string.onboarding_open_app_info)) }
        }
    }
}

private enum class DemoStage { EMPTY, SMS, CARD }

/** A synthetic M-Pesa message slides in and turns into the Spotted card the real thing produces. Replays on entry. */
@Composable
private fun DetectionDemo() {
    val still = reducedMotion()
    var stage by remember { mutableStateOf(if (still) DemoStage.CARD else DemoStage.EMPTY) }
    LaunchedEffect(Unit) {
        if (!still) {
            stage = DemoStage.SMS
            delay(DEMO_SMS_MS)
            stage = DemoStage.CARD
        }
    }
    val offset = MaterialTheme.motionScheme.defaultSpatialSpec<IntOffset>()
    val size = MaterialTheme.motionScheme.defaultSpatialSpec<IntSize>()
    val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    AnimatedContent(
        targetState = stage,
        transitionSpec = {
            if (targetState == DemoStage.SMS) {
                (slideInVertically(offset) { -it / 2 } + fadeIn(effects)) togetherWith fadeOut(effects)
            } else {
                ContentTransform(fadeIn(effects), fadeOut(effects), sizeTransform = SizeTransform { _, _ -> size })
            }
        },
        modifier = Modifier.fillMaxWidth().heightIn(min = 180.dp),
        label = "detection demo",
    ) { shown ->
        when (shown) {
            DemoStage.EMPTY -> Unit
            DemoStage.SMS -> SmsBubble()
            DemoStage.CARD ->
                SpottedChargeCard(
                    charge =
                        SpottedCharge(
                            detection =
                                DetectedTransaction(
                                    id = "demo",
                                    merchantName = stringResource(R.string.onboarding_demo_merchant),
                                    amount = 15.49,
                                    rawMessage = "",
                                    source = TransactionSource.SMS,
                                    currency = "USD",
                                ),
                            subscription = null,
                        ),
                    categoryKey = null,
                    customs = emptyList(),
                    onPick = {},
                    onCreateCategory = { _, _ -> },
                    onLog = {},
                    onTrack = {},
                    onIgnore = {},
                )
        }
    }
}

@Composable
private fun SmsBubble() {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(stringResource(R.string.onboarding_demo_sender), style = MaterialTheme.typography.labelLarge)
            Text(stringResource(R.string.onboarding_demo_sms), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** With animations switched off in developer options the welcome and demo show their final, still layout. */
@Composable
internal fun reducedMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return remember { Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
}
