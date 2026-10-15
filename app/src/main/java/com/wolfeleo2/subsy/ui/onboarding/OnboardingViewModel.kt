package com.wolfeleo2.subsy.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.wolfeleo2.subsy.data.model.AppMode
import com.wolfeleo2.subsy.data.repository.SettingsRepository
import com.wolfeleo2.subsy.data.repository.UserSettings
import com.wolfeleo2.subsy.detection.DetectorState
import com.wolfeleo2.subsy.detection.ModelRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class OnboardingStep {
    WELCOME,
    MODE,
    CURRENCY,
    DEMO,
    PRIVACY,
    NOTIFICATIONS,
    NAME,
    FIRST_ACTION,
}

/** What the last step opens once the flow is done. */
enum class FirstAction { NONE, ADD_SUBSCRIPTION, LOG_EXPENSE }

/** The three permission and name steps only exist when detection was turned on at [OnboardingStep.DEMO]. */
fun nextStep(
    step: OnboardingStep,
    detectionOn: Boolean,
): OnboardingStep? =
    when (step) {
        OnboardingStep.WELCOME -> OnboardingStep.MODE
        OnboardingStep.MODE -> OnboardingStep.CURRENCY
        OnboardingStep.CURRENCY -> OnboardingStep.DEMO
        OnboardingStep.DEMO -> if (detectionOn) OnboardingStep.PRIVACY else OnboardingStep.FIRST_ACTION
        OnboardingStep.PRIVACY -> OnboardingStep.NOTIFICATIONS
        OnboardingStep.NOTIFICATIONS -> OnboardingStep.NAME
        OnboardingStep.NAME -> OnboardingStep.FIRST_ACTION
        OnboardingStep.FIRST_ACTION -> null
    }

/** The step Back returns to; null from the first, which leaves the app. */
fun previousStep(
    step: OnboardingStep,
    detectionOn: Boolean,
): OnboardingStep? =
    when (step) {
        OnboardingStep.WELCOME -> null
        OnboardingStep.MODE -> OnboardingStep.WELCOME
        OnboardingStep.CURRENCY -> OnboardingStep.MODE
        OnboardingStep.DEMO -> OnboardingStep.CURRENCY
        OnboardingStep.PRIVACY -> OnboardingStep.DEMO
        OnboardingStep.NOTIFICATIONS -> OnboardingStep.PRIVACY
        OnboardingStep.NAME -> OnboardingStep.NOTIFICATIONS
        OnboardingStep.FIRST_ACTION -> if (detectionOn) OnboardingStep.NAME else OnboardingStep.DEMO
    }

data class OnboardingState(
    val step: OnboardingStep,
    val mode: AppMode,
    val currency: String,
    val detectionOn: Boolean,
    val name: String,
)

/**
 * Holds the answers so Back keeps them, and writes each one to settings as its step is confirmed, so leaving midway
 * keeps what was answered. An install that already has a mode (an upgrade) resumes at the detection demo.
 */
class OnboardingViewModel(
    private val settings: SettingsRepository,
    private val models: ModelRepository,
    initial: UserSettings,
) : ViewModel() {
    private val _state =
        MutableStateFlow(
            OnboardingState(
                step = if (initial.appMode != null) OnboardingStep.DEMO else OnboardingStep.WELCOME,
                mode = initial.appMode ?: AppMode.SUBSCRIPTIONS,
                currency = initial.primaryCurrency,
                detectionOn = initial.detectionEnabled,
                name = initial.accountHolderName,
            ),
        )
    val state: StateFlow<OnboardingState> = _state.asStateFlow()

    val modelState: StateFlow<DetectorState> =
        models.state.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            DetectorState.Absent,
        )

    fun setMode(mode: AppMode) = _state.update { it.copy(mode = mode) }

    fun setCurrency(code: String) = _state.update { it.copy(currency = code) }

    fun setName(name: String) = _state.update { it.copy(name = name) }

    /** Saves this step's answer, then moves on. */
    fun next() {
        val s = _state.value
        viewModelScope.launch {
            when (s.step) {
                OnboardingStep.MODE -> settings.setAppMode(s.mode)
                OnboardingStep.CURRENCY -> settings.setPrimaryCurrency(s.currency)
                OnboardingStep.NAME -> if (s.name.isNotBlank()) settings.setAccountHolderName(s.name)
                else -> Unit
            }
        }
        skip()
    }

    /** Moves on without saving anything. */
    fun skip() = _state.update { s -> nextStep(s.step, s.detectionOn)?.let { s.copy(step = it) } ?: s }

    /** Turns detection on and starts the model download, as Settings' switch does. */
    fun turnOnDetection() {
        viewModelScope.launch {
            settings.setDetectionEnabled(true)
            models.download()
        }
        _state.update { it.copy(detectionOn = true) }
        skip()
    }

    /** "Maybe later" after having turned it on and gone back: undo that. */
    fun skipDetection() {
        if (_state.value.detectionOn) {
            viewModelScope.launch {
                settings.setDetectionEnabled(false)
                models.remove()
            }
        }
        _state.update { it.copy(detectionOn = false) }
        skip()
    }

    /** False at the first step, so the system Back can leave the app. */
    fun back(): Boolean {
        val s = _state.value
        val previous = previousStep(s.step, s.detectionOn) ?: return false
        _state.value = s.copy(step = previous)
        return true
    }

    class Factory(
        private val settings: SettingsRepository,
        private val models: ModelRepository,
        private val initial: UserSettings,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = OnboardingViewModel(settings, models, initial) as T
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
