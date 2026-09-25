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

    override suspend fun calibratedLR(signal: String, baseLR: Double): Double {
        val stats = repository.get(signal) ?: return baseLR
        val total = stats.truePositives + stats.falsePositives +
                stats.trueNegatives + stats.falseNegatives

        if (total < 50) return baseLR

        val sensitivity = stats.truePositives.toDouble() /
                max(1.0, (stats.truePositives + stats.falseNegatives).toDouble())
        val fpr = stats.falsePositives.toDouble() /
                max(1.0, (stats.falsePositives + stats.trueNegatives).toDouble())

        val empirical = min(100.0, max(0.01, sensitivity / max(0.001, fpr)))
        return 0.3 * baseLR + 0.7 * empirical
    }

    override suspend fun observe(signal: String, wasCorrect: Boolean, wasPositive: Boolean) {
        repository.updateObservation(signal, wasCorrect, wasPositive)
    }
}
