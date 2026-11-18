// NO-NETWORK
package com.cashbuddy.core.prob.gate

import com.cashbuddy.core.prob.Evidence
import kotlinx.serialization.Serializable
import kotlin.math.ln

/**
 * Contract between `trainingdata/train_gate.py` and the runtime gate.
 *
 * The trainer is the ONLY place where weights are produced. Nothing in this package
 * contains a hand-tuned likelihood ratio: every number comes from the JSON artifact in
 * `composeResources/files/gate_weights.json`, and the legacy `LikelihoodRatios` constants
 * are used only when that artifact is missing or unreadable.
 *
 * The order of [GateSignal] is published by the trainer (`FEATURES` in train_gate.py).
 * [GateModelProvider] refuses the artifact when the two disagree, so a stale APK can
 * never silently score a model it does not understand.
 */
@Serializable
data class GateWeights(
    val modelVersion: String = "",
    @kotlinx.serialization.SerialName("schema_version") val schemaVersion: Int = 0,
    @kotlinx.serialization.SerialName("trained_at_utc") val trainedAtUtc: String = "",
    @kotlinx.serialization.SerialName("dataset_manifest_sha256")
    val datasetManifestSha256: String? = null,
    /** P(transaction) at serving time; the score is calibrated for this prior. */
    val prior: Double = 0.0,
    /** Shipped for verification only: must equal priorLogit + biasAdjust. */
    val intercept: Double = 0.0,
    /** Trained intercept minus the deployment-prior logit. */
    @kotlinx.serialization.SerialName("bias_adjust") val biasAdjust: Double = 0.0,
    /** Operating point selected on the validation split at the target FPR. */
    val threshold: Double = 0.5,
    /** Signal name -> likelihood ratio P(signal|txn) / P(signal|noise). */
    val features: Map<String, Double> = emptyMap(),
    val ngrams: GateNgramSpec = GateNgramSpec()
) {
    val priorLogit: Double
        get() = ln(prior / (1.0 - prior))
}

@Serializable
data class GateNgramSpec(
    val enabled: Boolean = false,
    /** Hash space size; also the number of n-gram weight columns at train time. */
    val buckets: Int = 0,
    val hash: String = "fnv1a32",
    /** Fixed-point divisor: stored int16 weight / scale == logit weight. */
    val scale: Int = 0,
    val file: String = "",
    val entries: Int = 0,
    val bytes: Long = 0,
    val sha256: String = ""
)

/**
 * The 20 runtime signals, in trainer order. Each entry declares how it is derived from
 * [Evidence]; the trainer mirrors this list exactly in `FEATURES`.
 *
 * Two entries are crosses rather than raw booleans (`amount`, `failure_in_context`).
 * They exist because a linear model cannot represent "amount present BUT not in a
 * transaction sentence" or "failure word AND transaction-shaped" without an explicit
 * interaction column.
 */
enum class GateSignal(
    val key: String,
    val present: (Evidence) -> Boolean
) {
    AMOUNT_IN_CONTEXT("amount_in_context", { it.amountInTransactionContext }),
    AMOUNT_DEBIT_SENT("amount_debit_sent", { it.amountAndDebitSameSentence }),
    AMOUNT_CREDIT_SENT("amount_credit_sent", { it.amountAndCreditSameSentence }),
    AMOUNT_FORBIDDEN_SHAPE("amount_forbidden_shape", { it.amountHasForbiddenShape }),
    AMOUNT_NOT_IN_CONTEXT("amount", { it.hasAmount && !it.amountInTransactionContext }),
    ACCOUNT("account", { it.hasAccount }),
    UTR("utr", { it.hasUtr }),
    BANK_SENDER("bank_sender", { it.senderLooksBank }),
    UPI_HANDLE("upi_handle", { it.hasUpiHandle }),
    BALANCE("balance", { it.hasBalanceMention }),
    MERCHANT_PACKAGE("merchant_pkg", { it.fromMerchantPackage }),
    SUCCESS("success", { it.hasSuccessWord }),
    OTP("OTP", { it.hasOtp }),
    PROMO("promo", { it.hasPromo }),
    OFFER("offer", { it.hasOffer }),
    RECENT_SIMILAR_AMOUNT("recent_similar_amount", { it.recentSimilarAmount }),
    RECENT_SAME_MERCHANT("recent_same_merchant", { it.recentSameMerchant }),
    BURST_DETECTED("burst_detected", { it.burstDetected }),
    FAILURE("failure", { it.hasFailureWord }),
    FAILURE_IN_CONTEXT("failure_in_context", { it.hasFailureWord && it.amountInTransactionContext });

    companion object {
        val keys: Set<String> = GateSignal.entries.mapTo(LinkedHashSet()) { it.key }
    }
}
