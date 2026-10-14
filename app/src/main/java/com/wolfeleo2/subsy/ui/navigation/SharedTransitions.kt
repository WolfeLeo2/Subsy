package com.wolfeleo2.subsy.ui.navigation

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Shape

/** Provided by [SubsyNavGraph]; null in previews, where the modifiers below do nothing. */
val LocalSharedTransitionScope = staticCompositionLocalOf<SharedTransitionScope?> { null }

/** The nav destination's enter/exit scope. Changes per destination, so not static. */
val LocalNavAnimatedScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

/**
 * Identifies one row on one screen. Home and Spending can both show a subscription, and both screens
 * are composed while switching tabs, so the screen name keeps their rows from matching each other.
 */
fun detailTransitionKey(
    origin: String,
    subscriptionId: String,
): String = "$origin/$subscriptionId"

// Same spatial spring as the NavHost transitions (MotionScheme.expressive's default spatial spec).
private val boundsSpring = BoundsTransform { _, _ -> spring<Rect>(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow) }

/** Container transform: a list row grows into the detail screen, and shrinks back on return. */
@Composable
fun Modifier.detailContainer(
    key: String?,
    shape: Shape,
): Modifier =
    sharedTransition { scope, visibility ->
        if (key == null) return@sharedTransition this
        with(scope) {
            sharedBounds(
                sharedContentState = rememberSharedContentState("container/$key"),
                animatedVisibilityScope = visibility,
                boundsTransform = boundsSpring,
                resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
                clipInOverlayDuringTransition = OverlayClip(shape),
            )
        }
    }

/** The service icon travels from the row into the detail header. */
@Composable
fun Modifier.detailIcon(key: String?): Modifier =
    sharedTransition { scope, visibility ->
        if (key == null) return@sharedTransition this
        with(scope) {
            sharedElement(rememberSharedContentState("icon/$key"), visibility, boundsTransform = boundsSpring)
        }
    }

@Composable
private inline fun Modifier.sharedTransition(
    block: @Composable Modifier.(SharedTransitionScope, AnimatedVisibilityScope) -> Modifier,
): Modifier {
    val scope = LocalSharedTransitionScope.current ?: return this
    val visibility = LocalNavAnimatedScope.current ?: return this
    return block(scope, visibility)
}
