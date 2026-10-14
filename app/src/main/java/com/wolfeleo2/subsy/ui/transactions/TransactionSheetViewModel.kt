package com.wolfeleo2.subsy.ui.transactions

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.wolfeleo2.subsy.R
import com.wolfeleo2.subsy.data.model.CustomCategory
import com.wolfeleo2.subsy.data.model.SpendCategory
import com.wolfeleo2.subsy.data.model.Transaction
import com.wolfeleo2.subsy.data.model.TransactionSource
import com.wolfeleo2.subsy.data.model.TransactionType
import com.wolfeleo2.subsy.data.repository.CategoryRepository
import com.wolfeleo2.subsy.data.repository.TransactionRepository
import com.wolfeleo2.subsy.data.util.formatAmountForInput
import com.wolfeleo2.subsy.data.util.parseAmount
import com.wolfeleo2.subsy.data.util.toEpochMillis
import com.wolfeleo2.subsy.data.util.toLocalDate
import com.wolfeleo2.subsy.data.util.today
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID

data class TransactionFormState(
    val type: TransactionType = TransactionType.EXPENSE,
    val amount: String = "",
    val currency: String = "USD",
    val date: LocalDate = today(),
    val categoryKey: String? = null,
    val note: String = "",
    @StringRes val amountError: Int? = null,
    @StringRes val categoryError: Int? = null,
    val isEditing: Boolean = false,
    val isLoaded: Boolean = false,
    val isSaving: Boolean = false,
)

/** Add or edit one transaction. [transactionId] is null for a new one. */
class TransactionSheetViewModel(
    private val transactionId: String?,
    private val transactions: TransactionRepository,
    private val categories: CategoryRepository,
    primaryCurrency: Flow<String> = flowOf("USD"),
    initialType: TransactionType = TransactionType.EXPENSE,
) : ViewModel() {
    private val form = MutableStateFlow(TransactionFormState(type = initialType))
    val state: StateFlow<TransactionFormState> = form.asStateFlow()
    val customCategories: StateFlow<List<CustomCategory>> =
        categories.getCustom().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val events = Channel<Unit>(Channel.BUFFERED)

    /** Emits once the transaction is saved; the sheet closes on it. */
    val saved = events.receiveAsFlow()
    private var existing: Transaction? = null

    init {
        viewModelScope.launch {
            val loaded = transactionId?.let { transactions.getById(it) }
            existing = loaded
            form.value =
                if (loaded == null) {
                    form.value.copy(currency = primaryCurrency.first(), isLoaded = true)
                } else {
                    TransactionFormState(
                        type = loaded.type,
                        amount = formatAmountForInput(loaded.amount),
                        currency = loaded.currency,
                        date = loaded.date.toLocalDate(),
                        categoryKey = loaded.categoryKey,
                        note = loaded.note.orEmpty(),
                        isEditing = true,
                        isLoaded = true,
                    )
                }
        }
    }

    fun setType(type: TransactionType) {
        form.update {
            if (it.type == type) return@update it
            // A category of the other kind no longer fits.
            it.copy(type = type, categoryKey = null, categoryError = null)
        }
    }

    fun setAmount(text: String) = form.update { it.copy(amount = text, amountError = null) }

    fun setCurrency(code: String) = form.update { it.copy(currency = code) }

    fun setDate(date: LocalDate) = form.update { it.copy(date = date) }

    fun setCategory(key: String) = form.update { it.copy(categoryKey = key, categoryError = null) }

    fun setNote(text: String) = form.update { it.copy(note = text) }

    /** Creates a category of the current kind and selects it. */
    fun addCategory(
        name: String,
        colorHex: String,
    ) {
        val category = CustomCategory(UUID.randomUUID().toString(), name.trim(), form.value.type, colorHex)
        viewModelScope.launch {
            categories.add(category)
            setCategory(category.key)
        }
    }

    fun save() {
        val current = form.value
        if (current.isSaving || !current.isLoaded) return
        val amount = parseAmount(current.amount)?.takeIf { it > 0.0 }
        val key = current.categoryKey
        if (amount == null || key == null) {
            form.update {
                it.copy(
                    amountError = if (amount == null) R.string.error_amount_invalid else null,
                    categoryError = if (key == null) R.string.error_category_required else null,
                )
            }
            return
        }
        form.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            try {
                val transaction =
                    Transaction(
                        id = existing?.id ?: UUID.randomUUID().toString(),
                        type = current.type,
                        amount = amount,
                        currency = current.currency,
                        date = current.date.toEpochMillis(),
                        categoryKey = key,
                        note = current.note.trim().ifEmpty { null },
                        merchant = existing?.merchant,
                        createdAt = existing?.createdAt ?: System.currentTimeMillis(),
                        source = existing?.source ?: TransactionSource.MANUAL,
                    )
                if (existing == null) transactions.insert(transaction) else transactions.update(transaction)
                events.send(Unit)
            } finally {
                form.update { it.copy(isSaving = false) }
            }
        }
    }

    class Factory(
        private val transactionId: String?,
        private val transactions: TransactionRepository,
        private val categories: CategoryRepository,
        private val primaryCurrency: Flow<String>,
        private val initialType: TransactionType = TransactionType.EXPENSE,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            TransactionSheetViewModel(transactionId, transactions, categories, primaryCurrency, initialType) as T
    }
}

/** Built-in categories of [type] that can be picked, then the user's own, as stored keys. */
fun pickableKeys(
    type: TransactionType,
    customs: List<CustomCategory>,
): List<String> = SpendCategory.pickable(type).map { it.name } + customs.filter { it.kind == type }.map { it.key }
