// NO-NETWORK
package com.cashbuddy.core.prob.gate

import com.cashbuddy.core.prob.Contribution
import com.cashbuddy.core.prob.Evidence
import kotlin.math.exp
import kotlin.math.ln

/**
 * Calibrated logit of one message under the trained gate.
 *
 * [logit] excludes the on-device learned-token channel (`Evidence.tokenLogOdds`), which the
 * classifier adds afterwards — the artifact models message content only, never user
 * feedback history.
 */
data class GateScore(
    val pTransaction: Double,
    val logit: Double,
    val ngramScore: Double,
    val contributions: List<Contribution>
)

/**
 * Pure scoring over a loaded artifact. No I/O, no coroutines, no hidden constants: the
 * bias, the per-signal likelihood ratios, the n-gram table and the operating threshold all
 * come from [weights]/[ngrams].
 *
 * logit = priorLogit + biasAdjust + sum(ln LR_i for present signals) + ngramScore
 *
 * `biasAdjust` is the trained intercept shifted from the balanced training prior to the
 * deployment prior, so `priorLogit + biasAdjust` reconstructs the trained intercept.
 */
class GateModel(
    val weights: GateWeights,
    private val ngrams: GateNgrams?,
    /** Non-null when the model loads but runs without its n-gram block. */
    val degradedReason: String? = null
) {
    private val featureLogits: DoubleArray
    private val bucketMask: Int
    private val baseLogit: Double

    init {
        require(weights.schemaVersion == SCHEMA_VERSION) {
            "unsupported gate schema ${weights.schemaVersion}"
        }
        require(weights.prior > 0.0 && weights.prior < 1.0) { "prior out of range" }
        require(weights.threshold > 0.0 && weights.threshold < 1.0) { "threshold out of range" }

        val declared = weights.features.keys
        require(declared == GateSignal.keys) {
            "artifact/runtime signal mismatch: missing=${GateSignal.keys - declared} " +
                "unexpected=${declared - GateSignal.keys}"
        }

        featureLogits = DoubleArray(GateSignal.entries.size)
        GateSignal.entries.forEachIndexed { index, signal ->
            val lr = weights.features.getValue(signal.key)
            require(lr > 0.0) { "signal ${signal.key} has non-positive likelihood ratio $lr" }
            featureLogits[index] = ln(lr)
        }

        // priorLogit + biasAdjust must reproduce the shipped intercept; a mismatch means the
        // trainer and runtime disagree about prior handling, which silently shifts every score.
        require(kotlin.math.abs(weights.priorLogit + weights.biasAdjust - weights.intercept) < 1e-4) {
            "prior/bias/intercept are inconsistent (${weights.priorLogit} + ${weights.biasAdjust} != ${weights.intercept})"
        }
        baseLogit = weights.priorLogit + weights.biasAdjust

        val spec = weights.ngrams
        bucketMask = if (spec.enabled) {
            require(spec.buckets > 0 && (spec.buckets and (spec.buckets - 1)) == 0) {
                "n-gram bucket count must be a power of two, was ${spec.buckets}"
            }
            spec.buckets - 1
        } else {
            0
        }
        if (spec.enabled) {
            require(ngrams != null || degradedReason != null) {
                "n-gram block declared enabled but no table was loaded"
            }
        }
    }

    fun score(evidence: Evidence, text: String): GateScore {
        var logit = baseLogit
        val contributions = ArrayList<Contribution>(GateSignal.entries.size + 1)
        GateSignal.entries.forEachIndexed { index, signal ->
            if (signal.present(evidence)) {
                val delta = featureLogits[index]
                logit += delta
                contributions.add(Contribution(signal.key, delta, exp(delta)))
            }
        }

        val ngramScore = if (ngrams != null && bucketMask > 0 && text.isNotEmpty()) {
            ngrams.score(GateTokenizer.buckets(text, bucketMask))
        } else {
            0.0
        }
        if (ngramScore != 0.0) {
            logit += ngramScore
            contributions.add(Contribution("ngrams", ngramScore, exp(ngramScore)))
        }

        // exp(-logit) overflows to Infinity for very negative logits; the limit of
        // 1/(1+exp(-x)) there is exactly 0, so short-circuit instead of returning NaN.
        val p = 1.0 / (1.0 + exp(-logit))
        return GateScore(
            pTransaction = p,
            logit = logit,
            ngramScore = ngramScore,
            contributions = contributions
        )
    }

    /**
     * Fast statistical qualification scoring on raw text alone (ZERO regexes, zero Evidence extraction).
     * Used as a fast pre-filter so everyday noise (Reddit, WhatsApp, social chats) never executes
     * regexes or sentence splitters.
     */
    fun scoreText(text: String): GateScore {
        var logit = baseLogit
        val contributions = ArrayList<Contribution>(1)
        val ngramScore = if (ngrams != null && bucketMask > 0 && text.isNotEmpty()) {
            ngrams.score(GateTokenizer.buckets(text, bucketMask))
        } else {
            0.0
        }
        if (ngramScore != 0.0) {
            logit += ngramScore
            contributions.add(Contribution("ngrams", ngramScore, exp(ngramScore)))
        }

        val p = 1.0 / (1.0 + exp(-logit))
        return GateScore(
            pTransaction = p,
            logit = logit,
            ngramScore = ngramScore,
            contributions = contributions
        )
    }

    companion object {
        const val SCHEMA_VERSION = 1
    }
}
