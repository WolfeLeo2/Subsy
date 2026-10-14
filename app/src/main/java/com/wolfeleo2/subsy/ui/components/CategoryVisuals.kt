package com.wolfeleo2.subsy.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Autorenew
import androidx.compose.material.icons.rounded.CardGiftcard
import androidx.compose.material.icons.rounded.DirectionsBus
import androidx.compose.material.icons.rounded.Flight
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Laptop
import androidx.compose.material.icons.rounded.LocalHospital
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Redeem
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material.icons.rounded.ShoppingBasket
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.Work
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.wolfeleo2.subsy.R
import com.wolfeleo2.subsy.data.model.CategoryRef
import com.wolfeleo2.subsy.data.model.SpendCategory
import com.wolfeleo2.subsy.ui.util.parseColorHex

@Composable
fun SpendCategory.label(): String =
    stringResource(
        when (this) {
            SpendCategory.GROCERIES -> R.string.spend_groceries
            SpendCategory.DINING -> R.string.spend_dining
            SpendCategory.TRANSPORT -> R.string.spend_transport
            SpendCategory.HOUSING -> R.string.spend_housing
            SpendCategory.MOBILE -> R.string.spend_mobile
            SpendCategory.SHOPPING -> R.string.spend_shopping
            SpendCategory.HEALTH -> R.string.spend_health
            SpendCategory.ENTERTAINMENT -> R.string.spend_entertainment
            SpendCategory.TRAVEL -> R.string.spend_travel
            SpendCategory.EDUCATION -> R.string.spend_education
            SpendCategory.GIFTS -> R.string.spend_gifts
            SpendCategory.OTHER -> R.string.spend_other
            SpendCategory.SUBSCRIPTIONS -> R.string.spend_subscriptions
            SpendCategory.SALARY -> R.string.spend_salary
            SpendCategory.FREELANCE -> R.string.spend_freelance
            SpendCategory.REFUNDS -> R.string.spend_refunds
            SpendCategory.GIFTS_RECEIVED -> R.string.spend_gifts_received
            SpendCategory.INVESTMENTS -> R.string.spend_investments
            SpendCategory.OTHER_INCOME -> R.string.spend_other_income
        },
    )

val SpendCategory.icon: ImageVector
    get() =
        when (this) {
            SpendCategory.GROCERIES -> Icons.Rounded.ShoppingBasket
            SpendCategory.DINING -> Icons.Rounded.Restaurant
            SpendCategory.TRANSPORT -> Icons.Rounded.DirectionsBus
            SpendCategory.HOUSING -> Icons.Rounded.Home
            SpendCategory.MOBILE -> Icons.Rounded.Smartphone
            SpendCategory.SHOPPING -> Icons.Rounded.ShoppingBag
            SpendCategory.HEALTH -> Icons.Rounded.LocalHospital
            SpendCategory.ENTERTAINMENT -> Icons.Rounded.Movie
            SpendCategory.TRAVEL -> Icons.Rounded.Flight
            SpendCategory.EDUCATION -> Icons.Rounded.School
            SpendCategory.GIFTS -> Icons.Rounded.CardGiftcard
            SpendCategory.OTHER -> Icons.Rounded.MoreHoriz
            SpendCategory.SUBSCRIPTIONS -> Icons.Rounded.Autorenew
            SpendCategory.SALARY -> Icons.Rounded.Work
            SpendCategory.FREELANCE -> Icons.Rounded.Laptop
            SpendCategory.REFUNDS -> Icons.AutoMirrored.Rounded.Undo
            SpendCategory.GIFTS_RECEIVED -> Icons.Rounded.Redeem
            SpendCategory.INVESTMENTS -> Icons.AutoMirrored.Rounded.TrendingUp
            SpendCategory.OTHER_INCOME -> Icons.Rounded.Payments
        }

@Composable
fun CategoryRef.label(): String =
    when (this) {
        is CategoryRef.Builtin -> category.label()
        is CategoryRef.Custom -> category.name
    }

@Composable
fun CategoryRef.color(): Color = parseColorHex(colorHex) ?: MaterialTheme.colorScheme.outline

/** The category's icon (or a custom category's initial) in white on its color, in a cookie shape. */
@Composable
fun CategoryBadge(
    category: CategoryRef,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
) {
    Box(
        modifier = modifier.size(size).background(category.color(), MaterialShapes.Cookie9Sided.toShape()),
        contentAlignment = Alignment.Center,
    ) {
        when (category) {
            is CategoryRef.Builtin ->
                Icon(
                    category.category.icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier =
                        Modifier.size(
                            size / 2,
                        ),
                )
            is CategoryRef.Custom ->
                Text(
                    text =
                        category.category.name
                            .take(1)
                            .uppercase(),
                    style = MaterialTheme.typography.titleMediumEmphasized,
                    color = Color.White,
                )
        }
    }
}
