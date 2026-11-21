// NO-NETWORK
package com.cashbuddy.core.prob.gate

import cashbuddy.shared.generated.resources.Res
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

/**
 * Loads and caches the trained gate artifact.
 *
 * Loading is a suspend function and is triggered lazily from the classifier, which already
 * runs off the main thread inside the ingest coroutine. [warmUp] lets the app start the load
 * at launch so the first notification does not pay for it, and the cached model is reused
 * for the process lifetime (the artifact is immutable, it ships inside the APK).
 *
 * Failure policy: a missing, truncated or contract-violating artifact yields `null` and the
 * classifier falls back to the legacy [com.cashbuddy.core.prob.LikelihoodRatios] table.
 * A readable weights file with an unreadable n-gram table degrades to feature-only scoring,
 * because the 20 deterministic signals are the more robust half of the model.
 */
class GateModelProvider(
    private val readBytes: suspend (String) -> ByteArray = ::readComposeResourceBytes
) {
    private val mutex = Mutex()
    private var attempted = false
    private var cached: GateModel? = null
    private var loadFailure: String? = null

    /** Cached model, or null when the artifact is unavailable/unusable. */
    suspend fun model(): GateModel? = mutex.withLock {
        ensureLoaded()
        cached
    }

    /** Loads the artifact now. Safe to call from an app-scope coroutine at startup. */
    suspend fun warmUp(): GateModel? = model()

    /** Human-readable reason the artifact was rejected, for diagnostics screens only. */
    suspend fun failureReason(): String? = mutex.withLock {
        ensureLoaded()
        loadFailure
    }

    private suspend fun ensureLoaded() {
        if (attempted) return
        val loaded = try {
            load()
        } catch (cancellation: CancellationException) {
            // Leave `attempted == false`: the next ingest must retry instead of silently
            // running on the legacy table for the rest of the process.
            throw cancellation
        } catch (error: Exception) {
            loadFailure = error.message ?: error::class.simpleName
            null
        }
        cached = loaded
        attempted = true
    }

    private suspend fun load(): GateModel? {
        return try {
            val weights = json.decodeFromString<GateWeights>(
                readBytes(WEIGHTS_PATH).decodeToString()
            )

            var ngrams: GateNgrams? = null
            var degraded: String? = null
            if (weights.ngrams.enabled) {
                val path = "files/" + weights.ngrams.file.ifBlank { NGRAM_FILE_NAME }
                ngrams = try {
                    GateNgrams.parse(readBytes(path), weights.ngrams.buckets)
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (error: Exception) {
                    null
                }
                if (ngrams == null) {
                    degraded = "n-gram table unreadable at $path; scoring on deterministic signals only"
                }
            }

            GateModel(weights = weights, ngrams = ngrams, degradedReason = degraded)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            loadFailure = error.message ?: error::class.simpleName
            null
        }
    }

    companion object {
        const val WEIGHTS_PATH = "files/gate_weights.json"
        const val NGRAM_FILE_NAME = "gate_ngrams.bin"

        private val json = Json {
            ignoreUnknownKeys = true
            isLenient = false
        }
    }
}

private suspend fun readComposeResourceBytes(path: String): ByteArray = Res.readBytes(path)
