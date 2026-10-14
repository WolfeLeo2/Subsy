package com.wolfeleo2.subsy.ui.components

import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import com.wolfeleo2.subsy.ui.theme.tabular
import com.wolfeleo2.subsy.ui.util.FormattingUtils

/** Every amount on screen goes through here: locale currency format, tabular figures, optional "/mo" suffix. */
@Composable
fun MoneyText(
    amount: Double,
    currency: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    suffix: String? = null,
    prefix: String? = null,
    autoSize: TextAutoSize? = null,
) {
    val formatted = FormattingUtils.formatCurrency(amount, currency)
    Text(
        text = (prefix ?: "") + formatted + (suffix ?: ""),
        modifier = modifier,
        style = style.tabular,
        color = color,
        maxLines = 1,
        autoSize = autoSize,
    )
}
