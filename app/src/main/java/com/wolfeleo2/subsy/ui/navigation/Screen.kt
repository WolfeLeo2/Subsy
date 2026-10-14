package com.wolfeleo2.subsy.ui.navigation

import kotlinx.serialization.Serializable

sealed interface Screen {
    @Serializable
    data object Home : Screen

    /**
     * [origin], [name], [colorHex] and [cancellationUrl] come from the tapped row so the header can draw
     * on the first frame, before the subscription loads, and the container transform has a target.
     */
    @Serializable
    data class SubscriptionDetail(
        val subscriptionId: String,
        val origin: String = "",
        val name: String = "",
        val colorHex: String? = null,
        val cancellationUrl: String? = null,
    ) : Screen

    @Serializable
    data object AddSubscription : Screen

    /** The add form started from a spotted charge (Track it); saving records that charge as the first payment. */
    @Serializable
    data class TrackSpottedCharge(
        val detectionId: String,
    ) : Screen

    @Serializable
    data class EditSubscription(
        val subscriptionId: String,
    ) : Screen

    @Serializable
    data object Analytics : Screen

    @Serializable
    data object Settings : Screen

    @Serializable
    data object Transactions : Screen

    @Serializable
    data object Categories : Screen
}
