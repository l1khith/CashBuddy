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
     */
    suspend fun extract(rawText: String): TokenEvidence {
        val significant = learnedTokenRepository.getAllSignificant()
        return evaluate(rawText, significant)
    }

    /**
     * Synchronously computes [TokenEvidence] for [rawText].
     */
    fun extractSync(rawText: String): TokenEvidence {
        val significant = learnedTokenRepository.getAllSignificantSync()
        return evaluate(rawText, significant)
    }

    private fun evaluate(rawText: String, significant: Map<String, TokenStats>): TokenEvidence {
        if (rawText.isBlank() || significant.isEmpty()) {
            return TokenEvidence(
                tokenLogOdds = 0.0,
                tokenCount = 0,
                strongestToken = null,
                strongestLR = 1.0
            )
        }

        val tokens = Tokenizer.tokenize(rawText)
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
