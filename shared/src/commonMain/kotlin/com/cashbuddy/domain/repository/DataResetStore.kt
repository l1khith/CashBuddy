// NO-NETWORK
package com.cashbuddy.domain.repository

/**
 * Atomic write-side contract for "clear all data" (audit fix P1-C).
 *
 * Wiping the app spans four stores (transactions, training data, learned tokens,
 * account balances). Executing them as separate repository calls means each gets
 * its own SQLDelight transaction — a crash mid-wipe leaves a half-reset app
 * (e.g. empty transactions but balances/training vocabulary intact), which the
 * classifier then treats as ground truth. Implementations MUST commit the whole
 * wipe in a single driver-level transaction so the reset is all-or-nothing.
 */
interface DataResetStore {

    /**
     * Wipes transactions, raw training samples, corrections, learned tokens, and
     * zeroes every account balance — all in ONE commit. Returns the number of
     * accounts that were zeroed.
     */
    suspend fun clearAllData(): Int
}
