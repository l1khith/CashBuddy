package com.cashbuddy.presentation.theme

import androidx.compose.ui.graphics.Color

// Base Dark & Light Tokens
val BackgroundDark = Color(0xFF0F0F0F)
val SurfaceDark = Color(0xFF1A1A1A)
val SurfaceElevatedDark = Color(0xFF242424)

val BackgroundLight = Color(0xFFFAFAFA)
val SurfaceLight = Color(0xFFFFFFFF)
val SurfaceElevatedLight = Color(0xFFF5F5F5)

// Primary Palette
val PrimaryIndigo = Color(0xFF6366F1)
val PrimaryMuted = Color(0xFF4F46E5)

val AccentEmerald = Color(0xFF10B981)
val AccentMuted = Color(0xFF059669)

val DangerRed = Color(0xFFEF4444)
val DangerMuted = Color(0xFFDC2626)

val WarningAmber = Color(0xFFF59E0B)

// Text Tokens
val TextPrimaryDark = Color(0xFFFAFAFA)
val TextSecondaryDark = Color(0xFFA3A3A3)
val TextTertiaryDark = Color(0xFF737373)

val TextPrimaryLight = Color(0xFF171717)
val TextSecondaryLight = Color(0xFF525252)
val TextTertiaryLight = Color(0xFF737373)

val DividerDark = Color(0xFF262626)
val DividerLight = Color(0xFFE5E5E5)
val OverlayColor = Color(0xCC000000)

// Category Colors (Consistent across app)
val CategoryFood = Color(0xFFF97066)
val CategoryTransport = Color(0xFF2DD4BF)
val CategoryShopping = Color(0xFF38BDF8)
val CategoryBills = Color(0xFF86EFAC)
val CategoryEntertainment = Color(0xFFC4B5FD)
val CategoryHealthcare = Color(0xFFFCD34D)
val CategoryEducation = Color(0xFFA78BFA)
val CategoryHousing = Color(0xFFFB7185)
val CategoryInsurance = Color(0xFF60A5FA)
val CategoryInvestments = Color(0xFF34D399)
val CategorySalary = Color(0xFF10B981)
val CategoryRefund = Color(0xFFFB923C)
val CategoryGift = Color(0xFFF472B6)
val CategoryUnknown = Color(0xFF9CA3AF)

// Backwards-compatible aliases for existing codebase
val TrustBluePrimary = PrimaryIndigo
val TrustBlueDark = PrimaryMuted
val TrustBlueLight = Color(0xFF818CF8)
val TealMintSecondary = AccentEmerald
val TealMintDark = AccentMuted
val TealMintLight = Color(0xFF34D399)
val ExpenseCrimson = DangerRed
val IncomeEmerald = AccentEmerald
val ConfidenceHigh = AccentEmerald
val ConfidenceMedium = WarningAmber
val ConfidenceLow = DangerRed
val SurfaceVariantDark = SurfaceElevatedDark
val SurfaceVariantLight = SurfaceElevatedLight
val OnSurfaceDark = TextPrimaryDark
val OnSurfaceVariantDark = TextSecondaryDark
val OnSurfaceLight = TextPrimaryLight
val OnSurfaceVariantLight = TextSecondaryLight
val OutlineDark = DividerDark
val OutlineLight = DividerLight

fun getCategoryColor(name: String?): Color {
    val lower = name?.lowercase() ?: ""
    return when {
        lower.contains("food") || lower.contains("dining") || lower.contains("restaurant") -> CategoryFood
        lower.contains("transport") || lower.contains("cab") || lower.contains("fuel") || lower.contains("metro") -> CategoryTransport
        lower.contains("shop") || lower.contains("ecommerce") -> CategoryShopping
        lower.contains("bill") || lower.contains("utilit") || lower.contains("electric") -> CategoryBills
        lower.contains("entertain") || lower.contains("movie") || lower.contains("ott") -> CategoryEntertainment
        lower.contains("health") || lower.contains("medical") || lower.contains("pharmacy") -> CategoryHealthcare
        lower.contains("educat") || lower.contains("school") || lower.contains("college") -> CategoryEducation
        lower.contains("hous") || lower.contains("rent") -> CategoryHousing
        lower.contains("insur") -> CategoryInsurance
        lower.contains("invest") || lower.contains("mutual") || lower.contains("stock") -> CategoryInvestments
        lower.contains("salary") -> CategorySalary
        lower.contains("refund") -> CategoryRefund
        lower.contains("gift") -> CategoryGift
        else -> CategoryUnknown
    }
}
