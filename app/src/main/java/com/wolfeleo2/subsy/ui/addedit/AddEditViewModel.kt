package com.wolfeleo2.subsy.ui.addedit

import android.database.sqlite.SQLiteException
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.wolfeleo2.subsy.R
import com.wolfeleo2.subsy.data.model.BillingCycle
import com.wolfeleo2.subsy.data.model.BillingCycleType
import com.wolfeleo2.subsy.data.model.Category
import com.wolfeleo2.subsy.data.model.PresetService
import com.wolfeleo2.subsy.data.model.Subscription
import com.wolfeleo2.subsy.data.model.SubscriptionStatus
import com.wolfeleo2.subsy.data.repository.DetectionInbox
import com.wolfeleo2.subsy.data.repository.ExchangeRates
import com.wolfeleo2.subsy.data.repository.SubscriptionRepository
import com.wolfeleo2.subsy.data.repository.TrackPrefill
import com.wolfeleo2.subsy.data.util.formatAmountForInput
import com.wolfeleo2.subsy.data.util.parseAmount
import com.wolfeleo2.subsy.data.util.toEpochMillis
import com.wolfeleo2.subsy.data.util.toLocalDate
import com.wolfeleo2.subsy.data.util.today
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.UUID

val TRIAL_DURATION_PRESETS = listOf(3, 7, 14, 30)
private const val DEFAULT_TRIAL_DAYS = 14

data class AddEditFormState(
    val name: String = "",
    val amount: String = "",
    val currency: String = "USD",
    val category: Category = Category.ENTERTAINMENT,
    val billingCycleType: BillingCycleType = BillingCycleType.MONTHLY,
    val customCycleDays: String = "30",
    val isFreeTrial: Boolean = false,
    /** Trial length used when the trial switch is turned on; a preset may suggest its own. */
    val suggestedTrialDays: Int = DEFAULT_TRIAL_DAYS,
    /** Next charge, or the day a trial ends and the first charge lands. */
    val firstChargeDate: LocalDate = today().plusMonths(1),
    val cancellationUrl: String = "",
    val colorHex: String? = null,
    /** The catalog's (US) price, shown as a hint when the form's currency differs. */
    val catalogPrice: Double? = null,
    val catalogCurrency: String = "USD",
    val notes: String = "",
    /** The payee as bank messages print it; detection matches payments to it ("ACME PROPERTIES" for "Rent"). */
    val bankName: String = "",
    /** Where payments count in All spending (a category key); null is Subscriptions. */
    val spendCategory: String? = null,
    val isEditing: Boolean = false,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    /** Editing an id that no longer exists (deleted elsewhere). */
    val isNotFound: Boolean = false,
    @param:StringRes val nameError: Int? = null,
    @param:StringRes val amountError: Int? = null,
    @param:StringRes val customDaysError: Int? = null,
    @param:StringRes val saveError: Int? = null,
) {
    /** Trial length matching [firstChargeDate], when it is one of the presets. */
    val trialDurationDays: Int?
        get() =
            ChronoUnit.DAYS
                .between(today(), firstChargeDate)
                .toInt()
                .takeIf { it in TRIAL_DURATION_PRESETS }

    /** Fields the user can edit, used to detect unsaved changes. */
    internal fun content() =
        copy(
            isEditing = false,
            isLoading = false,
            isSaving = false,
            nameError = null,
            amountError = null,
            customDaysError = null,
            saveError = null,
        )
}

sealed interface AddEditEvent {
    data object Saved : AddEditEvent
}

class AddEditViewModel(
    private val subscriptionRepository: SubscriptionRepository,
    private val subscriptionIdToEdit: String? = null,
    primaryCurrency: Flow<String> = flowOf("USD"),
    /** Only used to show what a catalog (US) price comes to in the form's currency. */
    val exchangeRates: StateFlow<ExchangeRates?> = MutableStateFlow(null),
    /** Track it from a spotted charge: the form starts from that charge, and saving records it as the first payment. */
    private val trackDetectionId: String? = null,
    private val inbox: DetectionInbox? = null,
) : ViewModel() {
    private val _formState =
        MutableStateFlow(
            AddEditFormState(
                isEditing = subscriptionIdToEdit != null,
                isLoading =
                    subscriptionIdToEdit != null,
            ),
        )
    val formState: StateFlow<AddEditFormState> = _formState.asStateFlow()

    private val events = Channel<AddEditEvent>(Channel.BUFFERED)
    val eventFlow: Flow<AddEditEvent> = events.receiveAsFlow()

    /** The stored subscription being edited; saving copies onto it so untouched fields survive. */
    private var original: Subscription? = null
    private var pristine: AddEditFormState = _formState.value.content()

    /** The tracked charge's date: the next charge is one cycle after it rather than one cycle from today. */
    private var chargedAt: Long? = null

    val hasUnsavedChanges: Boolean
        get() = _formState.value.content() != pristine

    init {
        viewModelScope.launch {
            if (subscriptionIdToEdit != null) {
                loadExistingSubscription(subscriptionIdToEdit)
            } else {
                val currency = primaryCurrency.first()
                _formState.update { it.copy(currency = currency) }
                trackDetectionId?.let { id -> inbox?.trackPrefill(id) }?.let(::applyTrackPrefill)
                pristine = _formState.value.content()
            }
        }
    }

    private suspend fun loadExistingSubscription(id: String) {
        val existing = subscriptionRepository.getSubscriptionByIdDirect(id)
        original = existing
        if (existing == null) {
            _formState.update { it.copy(isLoading = false, isNotFound = true) }
            return
        }
        val isTrialing = existing.status == SubscriptionStatus.TRIAL
        _formState.value =
            AddEditFormState(
                name = existing.name,
                amount = formatAmountForInput(existing.amount),
                currency = existing.currency,
                category = existing.category,
                billingCycleType = existing.billingCycle.type,
                customCycleDays = (existing.billingCycle.customDays ?: 30).toString(),
                isFreeTrial = isTrialing,
                firstChargeDate = ((if (isTrialing) existing.trialEndDate else null) ?: existing.nextBillingDate).toLocalDate(),
                cancellationUrl = existing.cancellationUrl.orEmpty(),
                colorHex = existing.colorHex,
                notes = existing.notes.orEmpty(),
                bankName = existing.bankName.orEmpty(),
                spendCategory = existing.spendCategory,
                isEditing = true,
            )
        pristine = _formState.value.content()
    }

    private fun applyTrackPrefill(prefill: TrackPrefill) {
        chargedAt = prefill.chargedAt
        prefill.preset?.let(::applyPreset)
        // What the bank took, which beats the catalog's US price.
        _formState.update {
            it.copy(
                name = prefill.name,
                amount = formatAmountForInput(prefill.amount),
                currency = prefill.currency,
                bankName = prefill.bankName.orEmpty(),
                firstChargeDate = it.cycleOrNull()?.let(::nextCharge) ?: it.firstChargeDate,
            )
        }
    }

    /** In whole cycles from the tracked charge, so an older charge still lands on a future date; else from today. */
    private fun nextCharge(cycle: BillingCycle): LocalDate =
        chargedAt?.let { cycle.nextBillingAfter(it, today().toEpochMillis()).toLocalDate() } ?: cycle.firstChargeFromToday()

    /**
     * Fills the form from a catalog entry. Catalog prices are US prices; services price by country, so in
     * another currency the amount is left for the user and the US price is only a hint, never converted.
     */
    fun applyPreset(preset: PresetService) {
        _formState.update {
            val sameCurrency = it.currency == preset.defaultCurrency
            it.copy(
                name = preset.name,
                amount = if (sameCurrency) formatAmountForInput(preset.defaultAmount) else "",
                catalogPrice = preset.defaultAmount,
                catalogCurrency = preset.defaultCurrency,
                category = preset.category,
                billingCycleType = preset.defaultBillingCycle.type,
                customCycleDays = (preset.defaultBillingCycle.customDays ?: 30).toString(),
                // Most people adding a known service already pay for it; a trial is opt-in.
                isFreeTrial = false,
                firstChargeDate = nextCharge(preset.defaultBillingCycle),
                cancellationUrl = preset.cancellationUrl,
                suggestedTrialDays = preset.defaultTrialDays ?: DEFAULT_TRIAL_DAYS,
                colorHex = preset.colorHex,
                nameError = null,
                amountError = null,
            )
        }
    }

    fun onNameChange(name: String) = _formState.update { it.copy(name = name, nameError = null) }

    fun onAmountChange(amount: String) = _formState.update { it.copy(amount = amount, amountError = null) }

    fun onCurrencyChange(currency: String) = _formState.update { it.copy(currency = currency) }

    fun onCategoryChange(category: Category) = _formState.update { it.copy(category = category) }

    fun onBillingCycleChange(type: BillingCycleType) =
        _formState.update {
            val next = it.copy(billingCycleType = type, customDaysError = null)
            if (it.isFreeTrial) next else next.copy(firstChargeDate = next.cycleOrNull()?.let(::nextCharge) ?: it.firstChargeDate)
        }

    fun onCustomCycleDaysChange(days: String) =
        _formState.update {
            it.copy(customCycleDays = days.filter(Char::isDigit).take(3), customDaysError = null)
        }

    fun onFreeTrialToggle(isTrial: Boolean) =
        _formState.update {
            val date =
                if (isTrial) {
                    today().plusDays(it.suggestedTrialDays.toLong())
                } else {
                    it.cycleOrNull()?.let(::nextCharge)
                        ?: it.firstChargeDate
                }
            it.copy(isFreeTrial = isTrial, firstChargeDate = date)
        }

    fun onTrialDurationDaysChange(days: Int) = _formState.update { it.copy(firstChargeDate = today().plusDays(days.toLong())) }

    fun onFirstChargeDateChange(date: LocalDate) = _formState.update { it.copy(firstChargeDate = date) }

    fun onCancellationUrlChange(url: String) = _formState.update { it.copy(cancellationUrl = url) }

    fun onNotesChange(notes: String) = _formState.update { it.copy(notes = notes) }

    fun onBankNameChange(name: String) = _formState.update { it.copy(bankName = name) }

    fun onSpendCategoryChange(key: String?) = _formState.update { it.copy(spendCategory = key) }

    fun save() {
        val state = _formState.value
        if (state.isSaving || state.isLoading || state.isNotFound) return

        val name = state.name.trim()
        val amount = parseAmount(state.amount)
        val cycle = state.cycleOrNull()
        val withErrors =
            state.copy(
                nameError = if (name.isBlank()) R.string.error_name_required else null,
                amountError = if (amount == null) R.string.error_amount_invalid else null,
                customDaysError = if (cycle == null) R.string.error_custom_days_invalid else null,
            )
        if (name.isBlank() || amount == null || cycle == null) {
            _formState.value = withErrors
            return
        }

        _formState.value = state.copy(isSaving = true, saveError = null)
        viewModelScope.launch {
            val chargeMillis = state.firstChargeDate.toEpochMillis()
            val existing = original
            val scheduleChanged =
                existing == null ||
                    existing.billingCycle != cycle ||
                    pristine.firstChargeDate != state.firstChargeDate ||
                    (existing.status == SubscriptionStatus.TRIAL) != state.isFreeTrial
            val status =
                when {
                    existing?.status == SubscriptionStatus.PAUSED || existing?.status == SubscriptionStatus.CANCELLED -> existing.status
                    state.isFreeTrial && !state.firstChargeDate.isBefore(today()) -> SubscriptionStatus.TRIAL
                    else -> SubscriptionStatus.ACTIVE
                }
            val edited =
                (existing ?: newSubscription(chargeMillis)).copy(
                    name = name,
                    category = state.category,
                    amount = amount,
                    currency = state.currency,
                    billingCycle = cycle,
                    isFreeTrial = (existing?.isFreeTrial ?: false) || state.isFreeTrial,
                    trialEndDate =
                        if (state.isFreeTrial) {
                            chargeMillis
                        } else {
                            existing?.trialEndDate?.takeIf {
                                status !=
                                    SubscriptionStatus.TRIAL
                            }
                        },
                    status = status,
                    cancellationUrl = state.cancellationUrl.trim().ifEmpty { null },
                    colorHex = state.colorHex,
                    notes = state.notes.trim().ifEmpty { null },
                    bankName = state.bankName.trim().ifEmpty { null },
                    spendCategory = state.spendCategory,
                )
            // Re-anchor only when the schedule changed, so month-end anchors survive unrelated edits.
            val subscription = if (scheduleChanged) edited.copy(startDate = chargeMillis, nextBillingDate = chargeMillis) else edited

            try {
                if (existing == null) {
                    subscriptionRepository.insertSubscription(subscription)
                    trackDetectionId?.let { inbox?.recordFirstCharge(it, subscription) }
                } else {
                    subscriptionRepository.updateSubscription(subscription)
                }
            } catch (_: SQLiteException) {
                _formState.update { it.copy(isSaving = false, saveError = R.string.error_save_failed) }
                return@launch
            }
            pristine = _formState.value.content()
            events.send(AddEditEvent.Saved)
        }
    }

    private fun newSubscription(chargeMillis: Long) =
        Subscription(
            id = UUID.randomUUID().toString(),
            name = "",
            category = Category.OTHER,
            amount = 0.0,
            startDate = chargeMillis,
            nextBillingDate = chargeMillis,
        )

    class Factory(
        private val repository: SubscriptionRepository,
        private val subscriptionIdToEdit: String? = null,
        private val primaryCurrency: Flow<String> = flowOf("USD"),
        private val exchangeRates: StateFlow<ExchangeRates?> = MutableStateFlow(null),
        private val trackDetectionId: String? = null,
        private val inbox: DetectionInbox? = null,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            AddEditViewModel(repository, subscriptionIdToEdit, primaryCurrency, exchangeRates, trackDetectionId, inbox) as T
    }
}

/** The billing cycle the form describes, or null when the custom interval is not a positive number. */
fun AddEditFormState.cycleOrNull(): BillingCycle? =
    if (billingCycleType == BillingCycleType.CUSTOM) {
        customCycleDays.toIntOrNull()?.takeIf { it > 0 }?.let(BillingCycle::custom)
    } else {
        BillingCycle(billingCycleType)
    }

private fun BillingCycle.firstChargeFromToday(): LocalDate = calculateNextBillingDate(today().toEpochMillis()).toLocalDate()
