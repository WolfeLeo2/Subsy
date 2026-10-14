package com.wolfeleo2.subsy.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.wolfeleo2.subsy.data.model.AppMode
import com.wolfeleo2.subsy.data.model.Money
import com.wolfeleo2.subsy.data.model.ThemeMode
import com.wolfeleo2.subsy.data.repository.ExchangeRates
import com.wolfeleo2.subsy.data.repository.SettingsRepository
import com.wolfeleo2.subsy.data.repository.TransactionDetectorRepository
import com.wolfeleo2.subsy.data.repository.UserSettings
import com.wolfeleo2.subsy.detection.DetectorState
import com.wolfeleo2.subsy.detection.ModelRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    val exchangeRates: StateFlow<ExchangeRates?>,
    private val modelRepository: ModelRepository,
    detectorRepository: TransactionDetectorRepository,
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

    val detectorState: StateFlow<DetectorState> =
        modelRepository.state.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DetectorState.Absent)

    /** Charges detection has stored so far; shown while the model is ready, until the confirm card exists. */
    val spottedCount: StateFlow<Int> = detectorRepository.count().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    /** Opting in downloads the model over Wi-Fi; opting out deletes it. Turning it on again retries a failed download. */
    fun setDetectionEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setDetectionEnabled(enabled)
            if (enabled) modelRepository.download() else modelRepository.remove()
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settingsRepository.setThemeMode(mode) }
    }

    class Factory(
        private val settingsRepository: SettingsRepository,
        private val exchangeRates: StateFlow<ExchangeRates?>,
        private val modelRepository: ModelRepository,
        private val detectorRepository: TransactionDetectorRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            SettingsViewModel(settingsRepository, exchangeRates, modelRepository, detectorRepository) as T
    }
}
