package com.wolfeleo2.subsy.ui.onboarding

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Autorenew
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.RoundedPolygon
import com.wolfeleo2.subsy.R
import com.wolfeleo2.subsy.data.catalog.PresetServiceCatalog
import kotlinx.coroutines.delay

private val CollageHeight = 300.dp
private const val STAGGER_MS = 70L

private enum class Role { PRIMARY, SECONDARY, TERTIARY }

/** One tile of the burst: a service logo on its brand colour, or a plain icon in a theme colour. */
private class Tile(
    val x: Float,
    val y: Float,
    val size: Dp,
    val shape: RoundedPolygon,
    val logo: Pair<Int, String>? = null,
    val icon: ImageVector? = null,
    val role: Role = Role.SECONDARY,
)

// Positions are fractions of the width and height. Logos are bundled so the first launch needs no network.
private val Tiles =
    listOf(
        Tile(0.33f, 0.26f, 128.dp, MaterialShapes.Cookie9Sided, logo = R.drawable.onboarding_logo_netflix to "netflix"),
        Tile(0.02f, 0.06f, 84.dp, MaterialShapes.Circle, logo = R.drawable.onboarding_logo_spotify to "spotify"),
        Tile(0.68f, 0.02f, 80.dp, MaterialShapes.Clover4Leaf, logo = R.drawable.onboarding_logo_youtube to "youtube_premium"),
        Tile(0.70f, 0.56f, 76.dp, MaterialShapes.Sunny, logo = R.drawable.onboarding_logo_chatgpt to "chatgpt_plus"),
        Tile(0.04f, 0.58f, 56.dp, MaterialShapes.Cookie6Sided, icon = Icons.Rounded.Autorenew, role = Role.SECONDARY),
        Tile(0.42f, 0.00f, 44.dp, MaterialShapes.Circle, icon = Icons.Rounded.Notifications, role = Role.TERTIARY),
        Tile(0.86f, 0.32f, 48.dp, MaterialShapes.SoftBurst, icon = Icons.Rounded.Payments, role = Role.PRIMARY),
        Tile(0.30f, 0.84f, 44.dp, MaterialShapes.Clover4Leaf, icon = Icons.Rounded.Lock, role = Role.PRIMARY),
        Tile(0.60f, 0.88f, 36.dp, MaterialShapes.Circle, icon = Icons.Rounded.Notifications, role = Role.SECONDARY),
    )

/** A scatter of service logos and icons in M3 Expressive shapes, popping in one after another. */
@Composable
fun WelcomeCollage(modifier: Modifier = Modifier) {
    val still = reducedMotion()
    BoxWithConstraints(modifier.fillMaxWidth().height(CollageHeight)) {
        Tiles.forEachIndexed { index, tile ->
            CollageTile(tile, index, still, Modifier.offset(maxWidth * tile.x, maxHeight * tile.y))
        }
    }
}

@Composable
private fun CollageTile(
    tile: Tile,
    index: Int,
    still: Boolean,
    modifier: Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val scale = remember { Animatable(if (still) 1f else 0f) }
    val spring = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    LaunchedEffect(Unit) {
        if (!still) {
            delay(index * STAGGER_MS)
            scale.animateTo(1f, spring)
        }
    }
    val (container, content) =
        when (tile.role) {
            Role.PRIMARY -> scheme.primaryContainer to scheme.onPrimaryContainer
            Role.SECONDARY -> scheme.secondaryContainer to scheme.onSecondaryContainer
            Role.TERTIARY -> scheme.tertiaryContainer to scheme.onTertiaryContainer
        }
    // The brand colour is the catalog's, so the welcome screen matches the rest of the app.
    val background =
        tile.logo?.let { (_, id) -> Color(android.graphics.Color.parseColor(PresetServiceCatalog.presets.first { it.id == id }.colorHex)) }
            ?: container
    Box(
        modifier =
            modifier
                .size(tile.size)
                .graphicsLayer {
                    scaleX = scale.value
                    scaleY = scale.value
                }.background(background, tile.shape.toShape()),
        contentAlignment = Alignment.Center,
    ) {
        val inner = Modifier.size(tile.size / 2)
        tile.logo?.let { (drawable, _) -> Icon(painterResource(drawable), contentDescription = null, tint = Color.White, modifier = inner) }
        tile.icon?.let { Icon(it, contentDescription = null, tint = content, modifier = inner) }
    }
}
