package com.cashbuddy.platform

import androidx.compose.runtime.Composable

@Composable
actual fun BackHandler(enabled: Boolean, onBack: () -> Unit) {
    // No-op on iOS: Predictive/interactive back gestures are managed natively by UIKit/Compose Scene
}
