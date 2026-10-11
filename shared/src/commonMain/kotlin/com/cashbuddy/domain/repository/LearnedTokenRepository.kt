// NO-NETWORK
package com.cashbuddy.domain.repository

/**
 * Repository for recording and querying learned token frequencies
 * used by the on-device Naive Bayes token evidence learner.
 */
interface LearnedTokenRepository {
    /**
     * Records a set of tokens observed in a confirmed ([isTransaction] = true)
     * or rejected ([isTransaction] = false) message.
     */
    suspend fun record(isTransaction: Boolean, tokens: Set<String>)

    /**
     * Returns all tokens that have at least 5 total observations,
     * mapped by token string to their [TokenStats].
     */
    suspend fun getAllSignificant(): Map<String, TokenStats>

    /**
     * Synchronous snapshot of significant tokens for non-suspending extraction paths.
     */
    fun getAllSignificantSync(): Map<String, TokenStats> = emptyMap()

    /**
     * Clears all learned token records.
     */
    suspend fun clear()
}

/**
 * Observation counts and Laplace-smoothed likelihood ratio for a learned token.
 */
data class TokenStats(
    val token: String,
    val transactionCount: Int,
    val nonTransactionCount: Int
) {
    /**
     * Computes Laplace-smoothed likelihood ratio: (tx + smooth) / (ntx + smooth).
     */
    fun likelihoodRatio(smooth: Double = 1.0): Double {
        return (transactionCount.toDouble() + smooth) / (nonTransactionCount.toDouble() + smooth)
    }
}
