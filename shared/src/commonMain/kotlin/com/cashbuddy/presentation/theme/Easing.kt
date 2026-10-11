package com.cashbuddy.presentation.theme

import androidx.compose.animation.core.CubicBezierEasing

object CashBuddyEasing {
    val EaseOutQuart = CubicBezierEasing(0.25f, 1f, 0.5f, 1f)
    val EaseInOutQuart = CubicBezierEasing(0.76f, 0f, 0.24f, 1f)
}
