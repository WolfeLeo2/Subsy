package com.wolfeleo2.subsy.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.wolfeleo2.subsy.R

// google_sans_flex.ttf is the variable font (wght 200..800); register one instance per weight
// the type scale uses so bold styles render real weights instead of synthesized ones.
@OptIn(ExperimentalTextApi::class)
private fun googlesansflex(weight: FontWeight) =
    Font(
        resId = R.font.google_sans_flex,
        weight = weight,
        variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
    )

val GoogleSansFlex =
    FontFamily(
        googlesansflex(FontWeight.Normal),
        googlesansflex(FontWeight.Medium),
        googlesansflex(FontWeight.SemiBold),
        googlesansflex(FontWeight.Bold),
        googlesansflex(FontWeight.ExtraBold),
    )

val SubsyTypography = Typography(fontFamily = GoogleSansFlex)

/** Tabular figures so amounts line up in lists and don't jitter while animating. */
val TextStyle.tabular: TextStyle get() = copy(fontFeatureSettings = "tnum")
