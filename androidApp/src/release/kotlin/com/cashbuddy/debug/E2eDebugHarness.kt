// NO-NETWORK
package com.cashbuddy.debug

import androidx.compose.runtime.Composable

/**
 * Release no-op stub for [E2eDebugHarness].
 *
 * Guaranteed to produce zero bytecode, zero UI, and zero synthetic notification
 * overhead in production Play Store release APKs.
 */
@Composable
fun E2eDebugHarness() {
    // No-op in release builds
}
