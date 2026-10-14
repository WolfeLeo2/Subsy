package com.wolfeleo2.subsy.ui.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.wolfeleo2.subsy.data.model.CustomCategory
import com.wolfeleo2.subsy.data.model.TransactionType
import com.wolfeleo2.subsy.data.repository.CategoryRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

class CategoriesViewModel(
    private val repository: CategoryRepository,
) : ViewModel() {
    /** Null while loading. */
    val categories: StateFlow<List<CustomCategory>?> =
        repository.getCustom().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun add(
        name: String,
        kind: TransactionType,
        colorHex: String,
    ) {
        viewModelScope.launch { repository.add(CustomCategory(UUID.randomUUID().toString(), name, kind, colorHex)) }
    }

    fun update(category: CustomCategory) {
        viewModelScope.launch { repository.update(category) }
    }

    fun delete(category: CustomCategory) {
        viewModelScope.launch { repository.delete(category) }
    }

    class Factory(
        private val repository: CategoryRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = CategoriesViewModel(repository) as T
    }
}
