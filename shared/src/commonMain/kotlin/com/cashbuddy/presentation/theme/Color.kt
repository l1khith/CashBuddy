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

val WarningAmber = Color(0xFFF59E0B)

// Text Tokens
val TextPrimaryDark = Color(0xFFFAFAFA)
val TextSecondaryDark = Color(0xFFA3A3A3)

val TextPrimaryLight = Color(0xFF171717)
val TextSecondaryLight = Color(0xFF525252)

val DividerDark = Color(0xFF262626)
val DividerLight = Color(0xFFE5E5E5)

// Backwards-compatible aliases for existing codebase
val TrustBluePrimary = PrimaryIndigo
val TrustBlueDark = PrimaryMuted
val TealMintSecondary = AccentEmerald
val ExpenseCrimson = DangerRed
val IncomeEmerald = AccentEmerald

fun getCategoryColor(name: String?): Color = com.cashbuddy.domain.model.CategoryRegistry.getColor(name)
