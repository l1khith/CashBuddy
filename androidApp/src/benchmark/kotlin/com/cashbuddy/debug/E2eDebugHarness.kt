// NO-NETWORK
package com.cashbuddy.debug

import androidx.compose.runtime.Composable

/**
 * Benchmark no-op stub for [E2eDebugHarness].
 *
 * Guaranteed to produce zero bytecode, zero UI, and zero synthetic notification
 */
@Composable
fun E2eDebugHarness() {
    // No-op in benchmark builds
}
