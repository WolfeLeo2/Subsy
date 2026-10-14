package com.wolfeleo2.subsy.ui.transactions

import com.wolfeleo2.subsy.R
import com.wolfeleo2.subsy.data.model.Category
import com.wolfeleo2.subsy.data.model.PaymentRecord
import com.wolfeleo2.subsy.data.model.SpendCategory
import com.wolfeleo2.subsy.data.model.Subscription
import com.wolfeleo2.subsy.data.model.Transaction
import com.wolfeleo2.subsy.data.model.TransactionType
import com.wolfeleo2.subsy.data.util.toEpochMillis
import com.wolfeleo2.subsy.data.util.today
import com.wolfeleo2.subsy.ui.FakeCategoryRepository
import com.wolfeleo2.subsy.ui.FakePaymentRecordRepository
import com.wolfeleo2.subsy.ui.FakeSubscriptionRepository
import com.wolfeleo2.subsy.ui.FakeTransactionRepository
import com.wolfeleo2.subsy.ui.MainDispatcherRule
import com.wolfeleo2.subsy.ui.transactions.TransactionsViewModel.Companion.UNDO_WINDOW_MS
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.YearMonth

class TransactionsViewModelsTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val transactions = FakeTransactionRepository()
    private val payments = FakePaymentRecordRepository()
    private val subscriptions = FakeSubscriptionRepository()
    private val categories = FakeCategoryRepository()
    private val now = today()

    private fun listViewModel() =
        TransactionsViewModel(transactions, payments.getAllPaymentRecords(), subscriptions.getAllSubscriptions(), categories.getCustom())

    private fun tx(
        id: String,
        amount: Double,
        type: TransactionType = TransactionType.EXPENSE,
        daysAgo: Long = 0,
    ) = Transaction(id, type, amount, "USD", now.minusDays(daysAgo).toEpochMillis(), SpendCategory.GROCERIES.name)

    @Test
    fun listsThisMonthWithPaymentsAndFilters() =
        runTest {
            transactions.insert(tx("a", 20.0))
            transactions.insert(tx("b", 500.0, TransactionType.INCOME))
            transactions.insert(Transaction("old", TransactionType.EXPENSE, 9.0, "USD", now.minusMonths(2).toEpochMillis(), "OTHER"))
            subscriptions.insertSubscription(
                Subscription("s", "Netflix", Category.ENTERTAINMENT, 10.0, startDate = 1L, nextBillingDate = 2L),
            )
            payments.recordPayment(PaymentRecord("p", "s", 10.0, "USD", now.toEpochMillis()))
            val vm = listViewModel()

            val state = vm.uiState.first { !it.isLoading }
            assertEquals(
                setOf("a", "b", "payment:p"),
                state.days
                    .flatMap { it.rows }
                    .map { it.id }
                    .toSet(),
            )
            assertEquals(500.0, state.summary!!.income, 0.001)
            assertEquals(30.0, state.summary.expenses, 0.001)
            assertFalse(state.canGoForward)

            vm.setFilter(TransactionFilter.IN)
            assertEquals(
                listOf("b"),
                vm.uiState
                    .first { it.filter == TransactionFilter.IN }
                    .days
                    .flatMap { it.rows }
                    .map { it.id },
            )

            vm.previousMonth()
            vm.previousMonth()
            val older = vm.uiState.first { it.month == YearMonth.from(now).minusMonths(2) }
            assertTrue(older.canGoForward)
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun deleteHidesAtOnceAndRemovesAfterTheUndoWindow() =
        runTest(mainDispatcherRule.testDispatcher) {
            transactions.insert(tx("a", 20.0))
            val vm = listViewModel()

            vm.requestDelete("a")
            assertTrue(
                vm.uiState
                    .first { !it.isLoading }
                    .days
                    .isEmpty(),
            )
            vm.undoDelete("a")
            assertEquals(
                1,
                vm.uiState
                    .first { it.days.isNotEmpty() }
                    .days.size,
            )

            vm.requestDelete("a")
            advanceTimeBy(UNDO_WINDOW_MS + 1)
            assertNull(transactions.getById("a"))
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun sheetValidatesThenSaves() =
        runTest(mainDispatcherRule.testDispatcher) {
            val vm = TransactionSheetViewModel(null, transactions, categories, flowOf("EUR"))
            advanceUntilIdle()
            assertEquals("EUR", vm.state.value.currency)

            vm.save()
            assertEquals(R.string.error_amount_invalid, vm.state.value.amountError)
            assertEquals(R.string.error_category_required, vm.state.value.categoryError)

            vm.setAmount("12.50")
            vm.setCategory(SpendCategory.DINING.name)
            vm.save()
            advanceUntilIdle()
            val saved = transactions.getAll().first().single()
            assertEquals(12.5, saved.amount, 0.001)
            assertEquals("EUR", saved.currency)
            assertEquals(SpendCategory.DINING.name, saved.categoryKey)
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun switchingTypeClearsACategoryOfTheOtherKindAndNewCategoriesGetSelected() =
        runTest(mainDispatcherRule.testDispatcher) {
            val vm = TransactionSheetViewModel(null, transactions, categories)
            advanceUntilIdle()
            vm.setCategory(SpendCategory.DINING.name)
            vm.setType(TransactionType.INCOME)
            assertNull(vm.state.value.categoryKey)

            vm.addCategory("  Tips ", "#0F766E")
            advanceUntilIdle()
            val made = categories.items.value.single()
            assertEquals("Tips", made.name)
            assertEquals(TransactionType.INCOME, made.kind)
            assertEquals(made.key, vm.state.value.categoryKey)
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun editingLoadsAndUpdatesTheSameTransaction() =
        runTest(mainDispatcherRule.testDispatcher) {
            transactions.insert(tx("a", 20.0))
            val vm = TransactionSheetViewModel("a", transactions, categories)
            advanceUntilIdle()
            assertTrue(vm.state.value.isEditing)
            assertEquals("20", vm.state.value.amount)

            vm.setAmount("25")
            vm.save()
            advanceUntilIdle()
            assertEquals(25.0, transactions.getById("a")!!.amount, 0.001)
            assertEquals(1, transactions.getAll().first().size)
        }
}
