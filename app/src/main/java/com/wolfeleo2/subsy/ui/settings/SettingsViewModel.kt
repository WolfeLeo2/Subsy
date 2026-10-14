package com.wolfeleo2.subsy.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.wolfeleo2.subsy.data.model.AppMode
import com.wolfeleo2.subsy.data.model.Money
import com.wolfeleo2.subsy.data.model.ThemeMode
import com.wolfeleo2.subsy.data.repository.ExchangeRates
import com.wolfeleo2.subsy.data.repository.SettingsRepository
import com.wolfeleo2.subsy.data.repository.UserSettings
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    val exchangeRates: StateFlow<ExchangeRates?>,
) : ViewModel() {
    val settings: StateFlow<UserSettings?> =
        settingsRepository.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun setPrimaryCurrency(code: String) {
        viewModelScope.launch { settingsRepository.setPrimaryCurrency(code) }
    }

    fun setAppMode(mode: AppMode) {
        viewModelScope.launch { settingsRepository.setAppMode(mode) }
    }

    /** [amount] is in the primary currency and stored with it; null removes the budget. */
    fun setMonthlyBudget(amount: Double?) {
        viewModelScope.launch {
            val currency = settingsRepository.primaryCurrency()
            settingsRepository.setMonthlyBudget(amount?.let { Money(it, currency) })
        }
    }

    fun setUseDynamicColor(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setUseDynamicColor(enabled) }
    }

    fun setSpreadLongCharges(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setSpreadLongCharges(enabled) }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settingsRepository.setThemeMode(mode) }
    }

    class Factory(
        private val settingsRepository: SettingsRepository,
        private val exchangeRates: StateFlow<ExchangeRates?>,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = SettingsViewModel(settingsRepository, exchangeRates) as T
    }
}
