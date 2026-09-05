package com.cashbuddy.presentation.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val RadiusNone = RoundedCornerShape(0.dp)
val RadiusSmall = RoundedCornerShape(4.dp)
val RadiusMedium = RoundedCornerShape(8.dp)
val RadiusLarge = RoundedCornerShape(16.dp)
val RadiusXLarge = RoundedCornerShape(24.dp)
val RadiusFull = CircleShape

val CashBuddyShapes = Shapes(
    extraSmall = RadiusSmall,
    small = RadiusMedium,
    medium = RadiusMedium,
    large = RadiusLarge,
    extraLarge = RadiusXLarge
)
