// NO-NETWORK
package com.cashbuddy.core.prob

import com.cashbuddy.domain.repository.SignalObservationRepository
import kotlin.math.max
import kotlin.math.min

interface Calibrator {
    suspend fun calibratedLR(signal: String, baseLR: Double): Double
    suspend fun observe(signal: String, wasCorrect: Boolean, wasPositive: Boolean)
}

object NoOpCalibrator : Calibrator {
    override suspend fun calibratedLR(signal: String, baseLR: Double): Double = baseLR
    override suspend fun observe(signal: String, wasCorrect: Boolean, wasPositive: Boolean) {}
}

class LikelihoodCalibrator(
    private val repository: SignalObservationRepository
) : Calibrator {

    companion object {
        /** Minimum observations per signal before empirical blending kicks in. */
        const val MIN_OBSERVATIONS = 50

        /** Blend weight of the hand-authored base LR vs the empirical LR. */
        const val BASE_LR_WEIGHT = 0.3
        const val EMPIRICAL_LR_WEIGHT = 0.7

        /** Safety clamp on the empirical LR (exp-space), symmetric-ish around 1.0. */
        const val EMPIRICAL_LR_MIN = 0.01
        const val EMPIRICAL_LR_MAX = 100.0
    }

    override suspend fun calibratedLR(signal: String, baseLR: Double): Double {
        val stats = repository.get(signal) ?: return baseLR
        val total = stats.truePositives + stats.falsePositives +
                stats.trueNegatives + stats.falseNegatives

        if (total < MIN_OBSERVATIONS) return baseLR

        val sensitivity = stats.truePositives.toDouble() /
                max(1.0, (stats.truePositives + stats.falseNegatives).toDouble())
        val fpr = stats.falsePositives.toDouble() /
                max(1.0, (stats.falsePositives + stats.trueNegatives).toDouble())

        val empirical = min(EMPIRICAL_LR_MAX, max(EMPIRICAL_LR_MIN, sensitivity / max(0.001, fpr)))
        return BASE_LR_WEIGHT * baseLR + EMPIRICAL_LR_WEIGHT * empirical
    }

    override suspend fun observe(signal: String, wasCorrect: Boolean, wasPositive: Boolean) {
        repository.updateObservation(signal, wasCorrect, wasPositive)
    }
}
