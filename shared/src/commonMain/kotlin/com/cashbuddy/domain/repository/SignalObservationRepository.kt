// NO-NETWORK
package com.cashbuddy.domain.repository

data class SignalObservation(
    val signal: String,
    val truePositives: Long,
    val falsePositives: Long,
    val trueNegatives: Long,
    val falseNegatives: Long
)

interface SignalObservationRepository {
    suspend fun get(signal: String): SignalObservation?
    suspend fun getAll(): List<SignalObservation>
    suspend fun updateObservation(signal: String, wasCorrect: Boolean, wasPositive: Boolean)
    suspend fun reset()
}
