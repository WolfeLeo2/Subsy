package com.wolfeleo2.subsy.data.repository

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.wolfeleo2.subsy.data.model.AppMode
import com.wolfeleo2.subsy.data.model.BudgetPlan
import com.wolfeleo2.subsy.data.model.Money
import com.wolfeleo2.subsy.data.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.YearMonth
import java.util.Currency
import java.util.Locale

data class UserSettings(
    val primaryCurrency: String = defaultCurrency(),
    val useDynamicColor: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    /** Null until the user picks one on first launch. */
    val appMode: AppMode? = null,
    /** Monthly budget in the currency it was set in; null when not set. */
    val monthlyBudget: Money? = null,
    /** Budgets for single months, replacing [monthlyBudget] in those months. */
    val budgetOverrides: Map<YearMonth, Money> = emptyMap(),
    /** Count quarterly and yearly subscription charges as a share in each month they cover, in budgets and charts. */
    val spreadLongCharges: Boolean = true,
    /** The user opted in to reading bank messages on the phone; the model downloads while this is on. */
    val detectionEnabled: Boolean = false,
) {
    val budgetPlan: BudgetPlan get() = BudgetPlan(monthlyBudget, budgetOverrides)
}

private fun defaultCurrency(): String = runCatching { Currency.getInstance(Locale.getDefault()).currencyCode }.getOrDefault("USD")

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsRepository(
    context: Context,
) {
    private val store = context.dataStore

    val settings: Flow<UserSettings> =
        store.data.map { prefs ->
            UserSettings(
                primaryCurrency = prefs[PRIMARY_CURRENCY] ?: defaultCurrency(),
                useDynamicColor = prefs[DYNAMIC_COLOR] ?: false,
                themeMode = ThemeMode.fromString(prefs[THEME_MODE]),
                appMode = AppMode.fromString(prefs[APP_MODE]),
                monthlyBudget =
                    prefs[MONTHLY_BUDGET]?.let { amount ->
                        Money(amount, prefs[MONTHLY_BUDGET_CURRENCY] ?: prefs[PRIMARY_CURRENCY] ?: defaultCurrency())
                    },
                budgetOverrides = prefs[BUDGET_OVERRIDES].orEmpty().mapNotNull(::parseOverride).toMap(),
                spreadLongCharges = prefs[SPREAD_LONG_CHARGES] ?: true,
                detectionEnabled = prefs[DETECTION_ENABLED] ?: false,
            )
        }

    suspend fun setPrimaryCurrency(code: String) {
        store.edit { it[PRIMARY_CURRENCY] = code }
    }

    suspend fun setUseDynamicColor(enabled: Boolean) {
        store.edit { it[DYNAMIC_COLOR] = enabled }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        store.edit { it[THEME_MODE] = mode.name }
    }

    suspend fun setAppMode(mode: AppMode) {
        store.edit { it[APP_MODE] = mode.name }
    }

    /** Sets the budget for one [month]; null removes the override so the usual budget applies again. */
    suspend fun setMonthBudget(
        month: YearMonth,
        budget: Money?,
    ) {
        store.edit { prefs ->
            val others = prefs[BUDGET_OVERRIDES].orEmpty().filterNot { parseOverride(it)?.first == month }.toSet()
            prefs[BUDGET_OVERRIDES] = if (budget == null) others else others + "$month|${budget.amount}|${budget.currency}"
        }
    }

    suspend fun setSpreadLongCharges(enabled: Boolean) {
        store.edit { it[SPREAD_LONG_CHARGES] = enabled }
    }

    suspend fun setDetectionEnabled(enabled: Boolean) {
        store.edit { it[DETECTION_ENABLED] = enabled }
    }

    suspend fun setMonthlyBudget(budget: Money?) {
        store.edit {
            if (budget == null) {
                it.remove(MONTHLY_BUDGET)
                it.remove(MONTHLY_BUDGET_CURRENCY)
            } else {
                it[MONTHLY_BUDGET] = budget.amount
                it[MONTHLY_BUDGET_CURRENCY] = budget.currency
            }
        }
    }

    /**
     * Budgets saved before they carried a currency were in the primary currency of the time. Pin them to it once,
     * so a later currency change converts them instead of reading the same number in the new currency.
     */
    suspend fun pinLegacyBudgetCurrency() {
        store.edit {
            if (it[MONTHLY_BUDGET] != null && it[MONTHLY_BUDGET_CURRENCY] == null) {
                it[MONTHLY_BUDGET_CURRENCY] = it[PRIMARY_CURRENCY] ?: defaultCurrency()
            }
        }
    }

    /** The primary currency as stored now, for one-off jobs outside the UI. */
    suspend fun primaryCurrency(): String = settings.first().primaryCurrency

    /** Keys of reminders already shown, so each alert fires once. */
    suspend fun remindersSent(): Set<String> = store.data.first()[REMINDERS_SENT] ?: emptySet()

    suspend fun setRemindersSent(keys: Set<String>) {
        store.edit { it[REMINDERS_SENT] = keys }
    }

    private companion object {
        val BUDGET_OVERRIDES = stringSetPreferencesKey("budget_overrides")
        val SPREAD_LONG_CHARGES = booleanPreferencesKey("spread_long_charges")
        val DETECTION_ENABLED = booleanPreferencesKey("detection_enabled")
        val REMINDERS_SENT = stringSetPreferencesKey("reminders_sent")
        val PRIMARY_CURRENCY = stringPreferencesKey("primary_currency")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val APP_MODE = stringPreferencesKey("app_mode")
        val MONTHLY_BUDGET = doublePreferencesKey("monthly_budget")
        val MONTHLY_BUDGET_CURRENCY = stringPreferencesKey("monthly_budget_currency")
    }
}

/** One stored override, "2026-12|35000.0|KES"; null if it can't be read. */
private fun parseOverride(entry: String): Pair<YearMonth, Money>? {
    val (month, amount, currency) = entry.split('|').takeIf { it.size == OVERRIDE_PARTS } ?: return null
    val parsedMonth = runCatching { YearMonth.parse(month) }.getOrNull() ?: return null
    return amount.toDoubleOrNull()?.let { parsedMonth to Money(it, currency) }
}

private const val OVERRIDE_PARTS = 3
