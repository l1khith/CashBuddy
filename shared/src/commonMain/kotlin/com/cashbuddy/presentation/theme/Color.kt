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

// Category Colors (JSON-driven via CategoryRegistry)
val CategoryFood get() = com.cashbuddy.domain.model.CategoryRegistry.getColor("Food & Dining")
val CategoryTransport get() = com.cashbuddy.domain.model.CategoryRegistry.getColor("Transportation")
val CategoryShopping get() = com.cashbuddy.domain.model.CategoryRegistry.getColor("Shopping")
val CategoryBills get() = com.cashbuddy.domain.model.CategoryRegistry.getColor("Bills & Utilities")
val CategoryEntertainment get() = com.cashbuddy.domain.model.CategoryRegistry.getColor("Entertainment")
val CategoryHealthcare get() = com.cashbuddy.domain.model.CategoryRegistry.getColor("Healthcare")
val CategoryEducation get() = com.cashbuddy.domain.model.CategoryRegistry.getColor("Education")
val CategoryHousing get() = com.cashbuddy.domain.model.CategoryRegistry.getColor("Housing")
val CategoryInsurance get() = com.cashbuddy.domain.model.CategoryRegistry.getColor("Insurance")
val CategoryInvestments get() = com.cashbuddy.domain.model.CategoryRegistry.getColor("Investments")
val CategorySalary get() = com.cashbuddy.domain.model.CategoryRegistry.getColor("Salary")
val CategoryRefund get() = com.cashbuddy.domain.model.CategoryRegistry.getColor("Refund")
val CategoryGift get() = com.cashbuddy.domain.model.CategoryRegistry.getColor("Gift")
val CategoryUnknown get() = com.cashbuddy.domain.model.CategoryRegistry.getColor("Uncategorized")

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

fun getCategoryColor(name: String?): Color = com.cashbuddy.domain.model.CategoryRegistry.getColor(name)
