package com.cashbuddy.presentation.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Standard 4dp/8dp grid spacing tokens for PaisaPal UI.
 */
data class Spacing(
    val xxs: Dp = 2.dp,
    val xs: Dp = 4.dp,
    val sm: Dp = 8.dp,
    val md: Dp = 12.dp,
    val lg: Dp = 16.dp,
    val xl: Dp = 20.dp,
    val xxl: Dp = 24.dp,
    val xxxl: Dp = 32.dp,
    val hero: Dp = 48.dp
)

val LocalSpacing = staticCompositionLocalOf { Spacing() }

val MaterialSpacing: Spacing
    @Composable
    @ReadOnlyComposable
    get() = LocalSpacing.current
