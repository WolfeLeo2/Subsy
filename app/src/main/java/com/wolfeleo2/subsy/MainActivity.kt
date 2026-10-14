package com.wolfeleo2.subsy

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.wolfeleo2.subsy.data.model.ThemeMode
import com.wolfeleo2.subsy.ui.navigation.SubsyNavGraph
import com.wolfeleo2.subsy.ui.onboarding.ModeChooserScreen
import com.wolfeleo2.subsy.ui.theme.AppTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.enableEdgeToEdge(window)
        // Reminders need it; the system stops asking after repeated denials.
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {}.launch(Manifest.permission.POST_NOTIFICATIONS)
        val app = application as SubsyApplication
        val settingsRepository = app.settingsRepository
        // A no-op once every budget has its currency; runs before the user can change the primary currency.
        lifecycleScope.launch {
            settingsRepository.pinLegacyBudgetCurrency()
            app.budgetRepository.pinUnsetCurrency(settingsRepository.primaryCurrency())
        }
        setContent {
            val settings by settingsRepository.settings.collectAsStateWithLifecycle(null)
            val current = settings
            AppTheme(
                themeMode = current?.themeMode ?: ThemeMode.SYSTEM,
                useDynamicColor = current?.useDynamicColor ?: false,
            ) {
                // Paint the theme's background under every screen. The window background comes from the system's
                // night setting, so with the app's theme forced the other way it would show through screen fades.
                Surface(color = MaterialTheme.colorScheme.background) {
                    when {
                        // Settings load in a few milliseconds; drawing nothing avoids flashing the chooser at returning users.
                        current == null -> Unit
                        current.appMode == null ->
                            ModeChooserScreen(
                                onChoose = { lifecycleScope.launch { settingsRepository.setAppMode(it) } },
                            )
                        else -> SubsyNavGraph()
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // A no-op while the cached rates are under a day old.
        lifecycleScope.launch { (application as SubsyApplication).exchangeRateRepository.refreshIfStale() }
    }
}
