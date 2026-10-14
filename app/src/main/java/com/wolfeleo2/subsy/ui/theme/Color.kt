package com.wolfeleo2.subsy.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// Brand scheme, tuned for M3 Expressive's bolder color: high-chroma containers rather than the pastel
// tonal-spot defaults. Emerald primary (income, within budget), indigo secondary (totals, upcoming
// charges), orange tertiary (trials, spending), red error (over budget, urgent trial expiry). On-container
// colors hold 4.5:1 on their containers. DESIGN.md's accents map onto these roles, so screens
// use colorScheme roles and never raw hex values.

val SubsyLightColors =
    lightColorScheme(
        primary = Color(0xFF006C4E),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFF5DEBB6),
        onPrimaryContainer = Color(0xFF00402D),
        inversePrimary = Color(0xFF5FDBAA),
        secondary = Color(0xFF3F51C6),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFCBD2FF),
        onSecondaryContainer = Color(0xFF1F2F8F),
        tertiary = Color(0xFF8B5000),
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFFFCF94),
        onTertiaryContainer = Color(0xFF5A3300),
        error = Color(0xFFBA1A1A),
        onError = Color(0xFFFFFFFF),
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF93000A),
        background = Color(0xFFF5FBF6),
        onBackground = Color(0xFF171D1A),
        surface = Color(0xFFF5FBF6),
        onSurface = Color(0xFF171D1A),
        surfaceVariant = Color(0xFFDBE5DE),
        onSurfaceVariant = Color(0xFF404944),
        surfaceTint = Color(0xFF006C4E),
        inverseSurface = Color(0xFF2C322E),
        inverseOnSurface = Color(0xFFECF2ED),
        outline = Color(0xFF707973),
        outlineVariant = Color(0xFFBFC9C2),
        scrim = Color(0xFF000000),
        surfaceBright = Color(0xFFF5FBF6),
        surfaceDim = Color(0xFFD6DBD7),
        surfaceContainerLowest = Color(0xFFFFFFFF),
        surfaceContainerLow = Color(0xFFEFF5F0),
        surfaceContainer = Color(0xFFE9EFEA),
        surfaceContainerHigh = Color(0xFFE4EAE5),
        surfaceContainerHighest = Color(0xFFDEE4DF),
    )

val SubsyDarkColors =
    darkColorScheme(
        primary = Color(0xFF5FDBAA),
        onPrimary = Color(0xFF003826),
        primaryContainer = Color(0xFF00704F),
        onPrimaryContainer = Color(0xFFA6FCD4),
        inversePrimary = Color(0xFF006C4E),
        secondary = Color(0xFFBAC3FF),
        onSecondary = Color(0xFF0F2178),
        secondaryContainer = Color(0xFF3747B5),
        onSecondaryContainer = Color(0xFFE0E3FF),
        tertiary = Color(0xFFFFB870),
        onTertiary = Color(0xFF4A2800),
        tertiaryContainer = Color(0xFF8A4F00),
        onTertiaryContainer = Color(0xFFFFDDBF),
        error = Color(0xFFFFB4AB),
        onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A),
        onErrorContainer = Color(0xFFFFDAD6),
        background = Color(0xFF0F1512),
        onBackground = Color(0xFFDEE4DF),
        surface = Color(0xFF0F1512),
        onSurface = Color(0xFFDEE4DF),
        surfaceVariant = Color(0xFF404944),
        onSurfaceVariant = Color(0xFFBFC9C2),
        surfaceTint = Color(0xFF5FDBAA),
        inverseSurface = Color(0xFFDEE4DF),
        inverseOnSurface = Color(0xFF2C322E),
        outline = Color(0xFF89938D),
        outlineVariant = Color(0xFF404944),
        scrim = Color(0xFF000000),
        surfaceBright = Color(0xFF353B37),
        surfaceDim = Color(0xFF0F1512),
        surfaceContainerLowest = Color(0xFF0A0F0D),
        surfaceContainerLow = Color(0xFF171D1A),
        surfaceContainer = Color(0xFF1B211E),
        surfaceContainerHigh = Color(0xFF252B28),
        surfaceContainerHighest = Color(0xFF303633),
    )
