// NO-NETWORK
package com.cashbuddy.core.prob

import com.cashbuddy.domain.repository.LearnedTokenRepository
import com.cashbuddy.domain.repository.TokenStats
import kotlin.math.abs
import kotlin.math.ln

/**
 * Aggregated Naive Bayes log-odds evidence computed from learned user tokens
 * present in a raw notification or screenshot text.
 */
data class TokenEvidence(
    val tokenLogOdds: Double,
    val tokenCount: Int,
    val strongestToken: String?,
    val strongestLR: Double
)

/**
 * Extracts learned token log-odds evidence from raw message text by querying
 * [LearnedTokenRepository] for tokens with at least 5 observations.
 */
class TokenEvidenceExtractor(
    private val learnedTokenRepository: LearnedTokenRepository
) {
    /**
     * Asynchronously computes [TokenEvidence] for [rawText].
     * Extracts tokens from the incoming message and requests only those specific tokens
     * via the L1 in-memory cache and indexed lookup.
     */
    suspend fun extract(rawText: String): TokenEvidence {
        if (rawText.isBlank()) {
            return TokenEvidence(tokenLogOdds = 0.0, tokenCount = 0, strongestToken = null, strongestLR = 1.0)
        }
        val messageTokens = Tokenizer.tokenize(rawText)
        if (messageTokens.isEmpty()) {
            return TokenEvidence(tokenLogOdds = 0.0, tokenCount = 0, strongestToken = null, strongestLR = 1.0)
        }
        val significant = learnedTokenRepository.getTokenStats(messageTokens)
        return evaluateWithTokens(messageTokens, significant)
    }

    /**
     * Synchronously computes [TokenEvidence] for [rawText].
     */
    fun extractSync(rawText: String): TokenEvidence {
        if (rawText.isBlank()) {
            return TokenEvidence(tokenLogOdds = 0.0, tokenCount = 0, strongestToken = null, strongestLR = 1.0)
        }
        val messageTokens = Tokenizer.tokenize(rawText)
        if (messageTokens.isEmpty()) {
            return TokenEvidence(tokenLogOdds = 0.0, tokenCount = 0, strongestToken = null, strongestLR = 1.0)
        }
        val significant = learnedTokenRepository.getTokenStatsSync(messageTokens)
        return evaluateWithTokens(messageTokens, significant)
    }

    private fun evaluateWithTokens(tokens: Set<String>, significant: Map<String, TokenStats>): TokenEvidence {
        if (tokens.isEmpty() || significant.isEmpty()) {
            return TokenEvidence(
                tokenLogOdds = 0.0,
                tokenCount = 0,
                strongestToken = null,
                strongestLR = 1.0
            )
        }

        var sumLogOdds = 0.0
        var count = 0
        var strongestToken: String? = null
        var strongestLR = 1.0
        var maxAbsLogOdds = -1.0

        for (token in tokens) {
            val stats = significant[token] ?: continue
            val lr = stats.likelihoodRatio()
            val logLr = ln(lr)
            sumLogOdds += logLr
            count++

            val absLogLr = abs(logLr)
            if (absLogLr > maxAbsLogOdds) {
                maxAbsLogOdds = absLogLr
                strongestToken = token
                strongestLR = lr
            }
        }

        return TokenEvidence(
            tokenLogOdds = sumLogOdds,
            tokenCount = count,
            strongestToken = strongestToken,
            strongestLR = strongestLR
        )
    }
}
