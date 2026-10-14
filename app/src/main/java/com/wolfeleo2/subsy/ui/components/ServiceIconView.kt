package com.wolfeleo2.subsy.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.wolfeleo2.subsy.ui.util.BrandLogoResolver
import com.wolfeleo2.subsy.ui.util.parseColorHex

object ServiceIconDefaults {
    val ListSize = 40.dp
    val HeroSize = 56.dp

    /** The app's identity motif: every service sits in the same soft four-sided cookie. */
    val shape: Shape
        @Composable get() = MaterialShapes.Clover4Leaf.toShape()
}

/**
 * A service's logo, or its initial on a tint of its brand color while the logo loads or when none exists.
 * Decorative: the name is always printed next to it, so it has no content description.
 */
@Composable
fun ServiceIconView(
    name: String,
    modifier: Modifier = Modifier,
    colorHex: String? = null,
    cancellationUrl: String? = null,
    size: Dp = ServiceIconDefaults.ListSize,
    /** The clover everywhere except where a different silhouette is expected, like the hero's circular logo stack. */
    shape: Shape = ServiceIconDefaults.shape,
) {
    val colors = MaterialTheme.colorScheme
    val brand = parseColorHex(colorHex)
    val logoUrl = remember(name, cancellationUrl) { BrandLogoResolver.logoUrl(name, cancellationUrl) }
    var logoState by remember(logoUrl) { mutableStateOf(LogoState.Loading) }
    // Favicons are drawn for white pages: a dark glyph on a transparent background vanishes on a dark tint.
    // So a loaded logo sits on a white tile in both themes; the brand tint only backs the initial.
    val container =
        when {
            logoState == LogoState.Loaded -> Color.White
            brand != null -> brand.copy(alpha = 0.16f).compositeOver(colors.surfaceContainerHighest)
            else -> colors.secondaryContainer
        }

    Box(
        modifier =
            modifier
                .size(size)
                .clip(shape)
                .background(container),
        contentAlignment = Alignment.Center,
    ) {
        if (logoState != LogoState.Loaded) {
            val typography = MaterialTheme.typography
            Text(
                text = name.trim().firstOrNull()?.uppercase() ?: "",
                style = if (size >= ServiceIconDefaults.HeroSize) typography.titleLarge else typography.titleMedium,
                color = colors.onSecondaryContainer,
            )
        }
        if (logoUrl != null && logoState != LogoState.Failed) {
            AsyncImage(
                model =
                    ImageRequest
                        .Builder(LocalContext.current)
                        .data(logoUrl)
                        .crossfade(true)
                        .build(),
                contentDescription = null,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(size * 0.2f)
                        // Softens favicons that are filled squares so they don't look pasted onto the tile.
                        .clip(MaterialTheme.shapes.extraSmall),
                contentScale = ContentScale.Fit,
                onSuccess = { logoState = LogoState.Loaded },
                onError = { logoState = LogoState.Failed },
            )
        }
    }
}

private enum class LogoState { Loading, Loaded, Failed }
