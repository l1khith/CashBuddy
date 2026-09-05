package com.cashbuddy.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryIndigo,
    onPrimary = TextPrimaryDark,
    primaryContainer = PrimaryMuted,
    onPrimaryContainer = TextPrimaryDark,
    secondary = AccentEmerald,
    onSecondary = BackgroundDark,
    secondaryContainer = AccentMuted,
    onSecondaryContainer = TextPrimaryDark,
    tertiary = WarningAmber,
    onTertiary = BackgroundDark,
    surface = SurfaceDark,
    onSurface = TextPrimaryDark,
    surfaceVariant = SurfaceElevatedDark,
    onSurfaceVariant = TextSecondaryDark,
    background = BackgroundDark,
    onBackground = TextPrimaryDark,
    outline = DividerDark,
    error = DangerRed,
    onError = TextPrimaryDark
)

private val LightColorScheme = lightColorScheme(
    primary = PrimaryIndigo,
    onPrimary = SurfaceLight,
    primaryContainer = PrimaryIndigo.copy(alpha = 0.12f),
    onPrimaryContainer = PrimaryMuted,
    secondary = AccentEmerald,
    onSecondary = SurfaceLight,
    secondaryContainer = AccentEmerald.copy(alpha = 0.12f),
    onSecondaryContainer = AccentMuted,
    tertiary = WarningAmber,
    onTertiary = SurfaceLight,
    surface = SurfaceLight,
    onSurface = TextPrimaryLight,
    surfaceVariant = SurfaceElevatedLight,
    onSurfaceVariant = TextSecondaryLight,
    background = BackgroundLight,
    onBackground = TextPrimaryLight,
    outline = DividerLight,
    error = DangerRed,
    onError = SurfaceLight
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
