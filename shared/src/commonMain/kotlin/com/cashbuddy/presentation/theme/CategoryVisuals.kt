package com.cashbuddy.presentation.theme

import androidx.compose.ui.graphics.Color
import cashbuddy.shared.generated.resources.Res
import cashbuddy.shared.generated.resources.ic_account_balance_wallet
import cashbuddy.shared.generated.resources.ic_attach_money
import cashbuddy.shared.generated.resources.ic_category
import cashbuddy.shared.generated.resources.ic_directions_bus
import cashbuddy.shared.generated.resources.ic_home
import cashbuddy.shared.generated.resources.ic_payments
import cashbuddy.shared.generated.resources.ic_receipt_long
import cashbuddy.shared.generated.resources.ic_refresh
import cashbuddy.shared.generated.resources.ic_restaurant
import cashbuddy.shared.generated.resources.ic_shield
import cashbuddy.shared.generated.resources.ic_shopping_bag
import cashbuddy.shared.generated.resources.ic_trending_down
import cashbuddy.shared.generated.resources.ic_trending_up
import cashbuddy.shared.generated.resources.ic_two_wheeler
import com.cashbuddy.domain.model.CategoryRegistry
import org.jetbrains.compose.resources.DrawableResource

/**
 * Single centralized visual provider for Category colors, icons, and preset palettes.
 * Guarantees consistent icon and color resolution across Transaction cards,
 * Budget lists, Stats charts, and Custom Domain management.
 */
object CategoryVisuals {

    val PRESET_COLORS = listOf(
        "#10B981", // Emerald
        "#14B8A6", // Teal
        "#06B6D4", // Cyan
        "#0EA5E9", // Sky
        "#6366F1", // Indigo
        "#8B5CF6", // Violet
        "#F43F5E", // Rose
        "#EF4444", // Crimson
        "#F97316", // Coral
        "#F59E0B", // Amber
        "#84CC16", // Lime
        "#64748B"  // Slate
    )

    val PRESET_ICONS = listOf(
        "category" to Res.drawable.ic_category,
        "shopping_bag" to Res.drawable.ic_shopping_bag,
        "restaurant" to Res.drawable.ic_restaurant,
        "directions_bus" to Res.drawable.ic_directions_bus,
        "two_wheeler" to Res.drawable.ic_two_wheeler,
        "receipt" to Res.drawable.ic_receipt_long,
        "home" to Res.drawable.ic_home,
        "shield" to Res.drawable.ic_shield,
        "trending_up" to Res.drawable.ic_trending_up,
        "account_balance" to Res.drawable.ic_account_balance_wallet,
        "payments" to Res.drawable.ic_payments,
        "attach_money" to Res.drawable.ic_attach_money
    )

    fun getDrawableForIconName(iconName: String?): DrawableResource {
        if (iconName.isNullOrBlank()) return Res.drawable.ic_category
        return when (iconName.lowercase().trim()) {
            "shopping_bag" -> Res.drawable.ic_shopping_bag
            "restaurant" -> Res.drawable.ic_restaurant
            "directions_bus", "directions_car" -> Res.drawable.ic_directions_bus
            "two_wheeler" -> Res.drawable.ic_two_wheeler
            "receipt" -> Res.drawable.ic_receipt_long
            "home" -> Res.drawable.ic_home
            "shield" -> Res.drawable.ic_shield
            "trending_up" -> Res.drawable.ic_trending_up
            "account_balance" -> Res.drawable.ic_account_balance_wallet
            "payments" -> Res.drawable.ic_payments
            "attach_money" -> Res.drawable.ic_attach_money
            "replay" -> Res.drawable.ic_refresh
            else -> Res.drawable.ic_category
        }
    }

    fun getDrawable(categoryName: String?, isDebit: Boolean = true): DrawableResource {
        if (categoryName.isNullOrBlank()) {
            return if (isDebit) Res.drawable.ic_trending_down else Res.drawable.ic_trending_up
        }

        // 1. Dynamic/Registered icon resolution from CategoryRegistry
        val registeredIcon = CategoryRegistry.getIcon(categoryName)
        if (registeredIcon.isNotBlank() && registeredIcon != "help_outline" && registeredIcon != "category") {
            return getDrawableForIconName(registeredIcon)
        }

        // 2. Keyword fallback matching
        val lower = categoryName.lowercase()
        return when {
            lower.contains("food") || lower.contains("dining") || lower.contains("restaurant") -> Res.drawable.ic_restaurant
            lower.contains("two wheeler") || lower.contains("bike") || lower.contains("motorcycle") -> Res.drawable.ic_two_wheeler
            lower.contains("transport") || lower.contains("bus") || lower.contains("travel") || lower.contains("cab") || lower.contains("fuel") || lower.contains("auto") || lower.contains("car") -> Res.drawable.ic_directions_bus
            lower.contains("shopping") || lower.contains("groceries") || lower.contains("retail") || lower.contains("store") -> Res.drawable.ic_shopping_bag
            lower.contains("bill") || lower.contains("utilit") || lower.contains("recharge") -> Res.drawable.ic_receipt_long
            lower.contains("housing") || lower.contains("rent") -> Res.drawable.ic_home
            lower.contains("invest") || lower.contains("stock") || lower.contains("mutual") -> Res.drawable.ic_trending_up
            lower.contains("salary") || lower.contains("account") || lower.contains("wallet") -> Res.drawable.ic_account_balance_wallet
            lower.contains("refund") -> Res.drawable.ic_refresh
            lower.contains("payment") || lower.contains("upi") -> Res.drawable.ic_payments
            lower.contains("cash") || lower.contains("money") -> Res.drawable.ic_attach_money
            categoryName.isNotBlank() && categoryName != "Unknown" -> Res.drawable.ic_category
            isDebit -> Res.drawable.ic_trending_down
            else -> Res.drawable.ic_trending_up
        }
    }

    fun getColor(categoryName: String?): Color = CategoryRegistry.getColor(categoryName)
}
