package com.cashbuddy.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = TrustBlueLight,
    onPrimary = TrustBlueDark,
    primaryContainer = TrustBlueDark,
    onPrimaryContainer = TrustBlueLight,
    secondary = TealMintLight,
    onSecondary = TealMintDark,
    secondaryContainer = TealMintDark,
    onSecondaryContainer = TealMintLight,
    surface = SurfaceDark,
    onSurface = OnSurfaceDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = OnSurfaceVariantDark,
    background = BackgroundDark,
    onBackground = OnSurfaceDark,
    outline = OutlineDark,
    error = ExpenseCrimson
)

private val LightColorScheme = lightColorScheme(
    primary = TrustBluePrimary,
    onPrimary = SurfaceLight,
    primaryContainer = TrustBlueLight.copy(alpha = 0.2f),
    onPrimaryContainer = TrustBlueDark,
    secondary = TealMintSecondary,
    onSecondary = SurfaceLight,
    secondaryContainer = TealMintLight.copy(alpha = 0.2f),
    onSecondaryContainer = TealMintDark,
    surface = SurfaceLight,
    onSurface = OnSurfaceLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = OnSurfaceVariantLight,
    background = BackgroundLight,
    onBackground = OnSurfaceLight,
    outline = OutlineLight,
    error = ExpenseCrimson
)

@Composable
fun CashBuddyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = CashBuddyTypography,
        shapes = CashBuddyShapes,
        content = content
    )
}
