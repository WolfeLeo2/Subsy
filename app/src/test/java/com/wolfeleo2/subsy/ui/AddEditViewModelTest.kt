package com.wolfeleo2.subsy.ui

import com.wolfeleo2.subsy.R
import com.wolfeleo2.subsy.data.catalog.PresetServiceCatalog
import com.wolfeleo2.subsy.data.model.BillingCycle
import com.wolfeleo2.subsy.data.model.BillingCycleType
import com.wolfeleo2.subsy.data.model.Category
import com.wolfeleo2.subsy.data.model.PresetService
import com.wolfeleo2.subsy.data.model.Subscription
import com.wolfeleo2.subsy.data.model.SubscriptionStatus
import com.wolfeleo2.subsy.ui.addedit.AddEditEvent
import com.wolfeleo2.subsy.ui.addedit.AddEditViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.Locale

class AddEditViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var repository: FakeSubscriptionRepository

    @Before
    fun setup() {
        repository = FakeSubscriptionRepository()
    }

    @Test
    fun applyingPresetPopulatesFormState() =
        runTest {
            val viewModel = AddEditViewModel(repository)
            val preset =
                PresetService(
                    id = "preset-netflix",
                    name = "Netflix",
                    category = Category.ENTERTAINMENT,
                    defaultAmount = 15.49,
                    defaultBillingCycle = BillingCycle.MONTHLY,
                    defaultTrialDays = null,
                    iconName = "netflix",
                    colorHex = "#E50914",
                    cancellationUrl = "https://www.netflix.com/youraccount",
                    keywords = listOf("netflix"),
                )

            viewModel.applyPreset(preset)

            val state = viewModel.formState.value
            assertEquals("Netflix", state.name)
            assertEquals("15.49", state.amount)
            assertEquals(Category.ENTERTAINMENT, state.category)
            assertEquals(BillingCycleType.MONTHLY, state.billingCycleType)
            assertEquals("https://www.netflix.com/youraccount", state.cancellationUrl)
            assertEquals("#E50914", state.colorHex)
        }

    @Test
    fun presetInAnotherCurrencyLeavesAmountBlankWithUsPriceHint() =
        runTest {
            val kes = AddEditViewModel(repository, primaryCurrency = flowOf("KES"))
            kes.applyPreset(PresetServiceCatalog.presets.first { it.id == "netflix" })

            val state = kes.formState.value
            assertEquals("KES", state.currency)
            assertEquals("", state.amount)
            assertEquals(15.49, state.catalogPrice!!, 0.001)
        }

    @Test
    fun validationFailsWhenNameIsBlank() =
        runTest {
            val viewModel = AddEditViewModel(repository)
            viewModel.onAmountChange("10.00")
            viewModel.save()

            assertEquals(R.string.error_name_required, viewModel.formState.value.nameError)
        }

    @Test
    fun validationFailsWhenAmountIsInvalid() =
        runTest {
            val viewModel = AddEditViewModel(repository)
            viewModel.onNameChange("Gym")
            viewModel.onAmountChange("not-a-number")
            viewModel.save()

            assertEquals(R.string.error_amount_invalid, viewModel.formState.value.amountError)
        }

    @Test
    fun savingValidSubscriptionInsertsAndEmitsSavedEvent() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = AddEditViewModel(repository)
            viewModel.onNameChange("ChatGPT Plus")
            viewModel.onAmountChange("20.00")
            viewModel.onCategoryChange(Category.WORK_SAAS)
            viewModel.onBillingCycleChange(BillingCycleType.MONTHLY)

            var eventReceived: AddEditEvent? = null
            backgroundScope.launch {
                viewModel.eventFlow.collect { eventReceived = it }
            }

            viewModel.save()

            assertEquals(AddEditEvent.Saved, eventReceived)
            val allSubs = repository.getAllSubscriptions().first()
            assertEquals(1, allSubs.size)
            val saved = allSubs.first()
            assertEquals("ChatGPT Plus", saved.name)
            assertEquals(20.00, saved.amount, 0.001)
            assertEquals(Category.WORK_SAAS, saved.category)
        }

    @Test
    fun editingExistingSubscriptionLoadsAndUpdates() =
        runTest {
            val existing =
                Subscription(
                    id = "existing-1",
                    name = "Disney+",
                    category = Category.ENTERTAINMENT,
                    amount = 7.99,
                    billingCycle = BillingCycle.MONTHLY,
                    startDate = 1000L,
                    nextBillingDate = 2000L,
                    status = SubscriptionStatus.ACTIVE,
                )
            repository.insertSubscription(existing)

            val viewModel = AddEditViewModel(repository, subscriptionIdToEdit = "existing-1")

            val state = viewModel.formState.value
            assertEquals("Disney+", state.name)
            assertEquals("7.99", state.amount)
            assertTrue(state.isEditing)

            viewModel.onAmountChange("13.99")
            viewModel.save()

            val updated = repository.getSubscriptionByIdDirect("existing-1")
            assertNotNull(updated)
            assertEquals(13.99, updated?.amount ?: 0.0, 0.001)
        }

    @Test
    fun savingWithFreeTrialSetsTrialStatusAndCalculatesTrialEndDate() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = AddEditViewModel(repository)
            viewModel.onNameChange("Audible")
            viewModel.onAmountChange("14.95")
            viewModel.onFreeTrialToggle(true)
            viewModel.onTrialDurationDaysChange(30)

            var eventReceived: AddEditEvent? = null
            backgroundScope.launch {
                viewModel.eventFlow.collect { eventReceived = it }
            }

            viewModel.save()

            assertEquals(AddEditEvent.Saved, eventReceived)
            val saved = repository.getAllSubscriptions().first().first()
            assertTrue(saved.isFreeTrial)
            assertEquals(SubscriptionStatus.TRIAL, saved.status)
            assertNotNull(saved.trialEndDate)
        }

    @Test
    fun changingBillingCyclesUpdatesFormState() =
        runTest {
            val viewModel = AddEditViewModel(repository)
            viewModel.onBillingCycleChange(BillingCycleType.YEARLY)
            assertEquals(BillingCycleType.YEARLY, viewModel.formState.value.billingCycleType)

            viewModel.onBillingCycleChange(BillingCycleType.WEEKLY)
            assertEquals(BillingCycleType.WEEKLY, viewModel.formState.value.billingCycleType)
        }

    @Test
    fun typingNameOrAmountClearsValidationError() =
        runTest {
            val viewModel = AddEditViewModel(repository)
            viewModel.save()
            assertNotNull(viewModel.formState.value.nameError)
            assertNotNull(viewModel.formState.value.amountError)

            viewModel.onNameChange("Notion")
            assertNull(viewModel.formState.value.nameError)

            viewModel.onAmountChange("10")
            assertNull(viewModel.formState.value.amountError)
        }

    @Test
    fun editingPausedSubscriptionKeepsStatusStartDateAndCreatedAt() =
        runTest {
            val existing =
                Subscription(
                    id = "paused-1",
                    name = "Gym",
                    category = Category.HEALTH_FITNESS,
                    amount = 40.0,
                    billingCycle = BillingCycle.MONTHLY,
                    startDate = 1000L,
                    nextBillingDate = 2000L,
                    status = SubscriptionStatus.PAUSED,
                    createdAt = 500L,
                )
            repository.insertSubscription(existing)

            val viewModel = AddEditViewModel(repository, subscriptionIdToEdit = "paused-1")
            viewModel.onAmountChange("45")
            viewModel.save()

            val updated = repository.getSubscriptionByIdDirect("paused-1")
            assertEquals(existing.copy(amount = 45.0), updated)
        }

    @Test
    fun doubleSaveInsertsOnce() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = AddEditViewModel(repository)
            viewModel.onNameChange("Notion")
            viewModel.onAmountChange("10")

            viewModel.save()
            viewModel.save()

            assertEquals(1, repository.getAllSubscriptions().first().size)
        }

    @Test
    fun commaDecimalAmountSavesInGermanLocale() =
        runTest(mainDispatcherRule.testDispatcher) {
            val previous = Locale.getDefault()
            Locale.setDefault(Locale.GERMANY)
            try {
                val viewModel = AddEditViewModel(repository)
                viewModel.onNameChange("Spotify")
                viewModel.onAmountChange("9,99")
                viewModel.save()

                assertEquals(
                    9.99,
                    repository
                        .getAllSubscriptions()
                        .first()
                        .single()
                        .amount,
                    0.0001,
                )
            } finally {
                Locale.setDefault(previous)
            }
        }
}
