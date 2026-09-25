// NO-NETWORK
package com.cashbuddy.data.repository

import com.cashbuddy.db.AppDatabase
import com.cashbuddy.domain.repository.SignalObservation
import com.cashbuddy.domain.repository.SignalObservationRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SignalObservationRepositoryImpl(
    private val database: AppDatabase,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) : SignalObservationRepository {

    private val queries = database.signal_observationsQueries

    override suspend fun get(signal: String): SignalObservation? = withContext(dispatcher) {
        queries.getSignal(signal).executeAsOneOrNull()?.let {
            SignalObservation(
                signal = it.signal,
                truePositives = it.true_positives,
                falsePositives = it.false_positives,
                trueNegatives = it.true_negatives,
                falseNegatives = it.false_negatives
            )
        }
    }

    override suspend fun getAll(): List<SignalObservation> = withContext(dispatcher) {
        queries.getAll().executeAsList().map {
            SignalObservation(
                signal = it.signal,
                truePositives = it.true_positives,
                falsePositives = it.false_positives,
                trueNegatives = it.true_negatives,
                falseNegatives = it.false_negatives
            )
        }
    }

    override suspend fun updateObservation(
        signal: String,
        wasCorrect: Boolean,
        wasPositive: Boolean
    ): Unit = withContext(dispatcher) {
        val current = queries.getSignal(signal).executeAsOneOrNull()
        var tp = current?.true_positives ?: 0L
        var fp = current?.false_positives ?: 0L
        var tn = current?.true_negatives ?: 0L
        var fn = current?.false_negatives ?: 0L

        if (wasCorrect && wasPositive) {
            tp++
        } else if (!wasCorrect && wasPositive) {
            fp++
        } else if (wasCorrect && !wasPositive) {
            tn++
        } else {
            fn++
        }

        queries.upsertObservation(
            signal = signal,
            true_positives = tp,
            false_positives = fp,
            true_negatives = tn,
            false_negatives = fn
        )
    }

    override suspend fun reset(): Unit = withContext(dispatcher) {
        queries.deleteAll()
    }
}
