package com.wolfeleo2.subsy.ui.analytics.charts

import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.core.content.res.ResourcesCompat
import com.wolfeleo2.subsy.R
import kotlin.math.PI

// Compose has no text-on-a-path, so curved ring labels and axis labels use the platform Paint, set up from the
// theme's text style and the app font so they match the rest of the screen.

private const val MEDIUM_WEIGHT = 500

/** A Paint for [style] in the app font at medium weight, centered on the point it is drawn at. */
@Composable
fun rememberChartPaint(
    style: TextStyle,
    color: Color,
): Paint {
    val context = LocalContext.current
    val density = LocalDensity.current
    val sizePx = with(density) { style.fontSize.toPx() }
    val argb = color.toArgb()
    return remember(sizePx, argb) {
        val font = runCatching { ResourcesCompat.getFont(context, R.font.google_sans_flex) }.getOrNull()
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(font ?: Typeface.DEFAULT, MEDIUM_WEIGHT, false)
            textSize = sizePx
            this.color = argb
            textAlign = Paint.Align.CENTER
        }
    }
}

/**
 * Draws [text] centered along the top (glyphs pointing outward) or bottom (glyphs pointing inward) half of a
 * circle of [radius] around the canvas center, shrinking it to fit [maxSweepDegrees] of arc and skipping it
 * when even the smallest size does not fit.
 */
fun DrawScope.drawArcText(
    text: String,
    paint: Paint,
    radius: Float,
    top: Boolean,
    maxSweepDegrees: Float = 120f,
) {
    if (text.isEmpty() || radius <= 0f) return
    val room = (2 * PI * radius * maxSweepDegrees / 360f).toFloat()
    val size = paint.textSize
    var scale = 1f
    while (paint.measureText(text) * scale > room && scale > MIN_TEXT_SCALE) scale -= TEXT_SCALE_STEP
    if (paint.measureText(text) * scale > room) return
    paint.textSize = size * scale
    // Top text sits outside its baseline, so its baseline goes one cap height further in.
    val baseline = if (top) radius - paint.textSize * CAP_HEIGHT else radius
    val rect = RectF(center.x - baseline, center.y - baseline, center.x + baseline, center.y + baseline)
    val path = Path().apply { addArc(rect, 180f, if (top) 180f else -180f) }
    // drawTextOnPath ignores Paint.Align, so center by hand: start half the text's width before the arc's middle.
    val start = (PI * baseline / 2).toFloat() - paint.measureText(text) / 2f
    val align = paint.textAlign
    paint.textAlign = Paint.Align.LEFT
    drawIntoCanvas { it.nativeCanvas.drawTextOnPath(text, path, start, 0f, paint) }
    paint.textAlign = align
    paint.textSize = size
}

/** Draws [text] with its center at ([x], [y] baseline). */
fun DrawScope.drawLabel(
    text: String,
    paint: Paint,
    x: Float,
    y: Float,
) {
    drawIntoCanvas { it.nativeCanvas.drawText(text, x, y, paint) }
}

private const val MIN_TEXT_SCALE = 0.75f
private const val TEXT_SCALE_STEP = 0.05f
private const val CAP_HEIGHT = 0.72f
