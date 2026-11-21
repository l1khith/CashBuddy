// NO-NETWORK
package com.cashbuddy.core.prob

import com.cashbuddy.core.prob.gate.GateModelProvider
import com.cashbuddy.domain.model.TransactionType
import kotlin.math.exp
import kotlin.math.ln

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class Contribution(
    val signal: String,
    val delta: Double,
    val lr: Double
) {
    fun toJson(): String = jsonInstance.encodeToString(this)

    companion object {
        private val jsonInstance = Json { ignoreUnknownKeys = true; prettyPrint = false }
        fun fromJson(json: String): Contribution = jsonInstance.decodeFromString(json)
        fun listToJson(list: List<Contribution>): String = jsonInstance.encodeToString(list)
        fun listFromJson(json: String): List<Contribution> = jsonInstance.decodeFromString(json)
    }
}

data class ClassificationResult(
    val pTransaction: Double,
    val fieldConfidences: FieldConfidences,
    val evidence: Evidence,
    val contributions: List<Contribution>,
    val source: NotificationSource,
    val amount: Double?,
    val type: TransactionType?,
    val accountLast4: String?,
    val merchant: String?,
    val bankHint: String?,
    val utr: String? = null
)

class ProbabilisticClassifier(
    private val calibrator: Calibrator = NoOpCalibrator,
    private val evidenceExtractor: EvidenceExtractor = EvidenceExtractor(),
    private val fieldConfidenceEstimator: FieldConfidenceEstimator = FieldConfidenceEstimator(),
    /**
     * Trained gate artifact. When present (production), scores come from the artifact's
     * likelihood ratios + hashed n-grams. When null or unreachable (tests, corrupt bundle,
     * first launch before the file is extractable) the legacy [LikelihoodRatios] table keeps
     * the app functional instead of failing closed.
     */
    private val gateModelProvider: GateModelProvider? = null
) {

    /**
     * Synchronous convenience wrapper for TESTS and debug harnesses only (audit fix P3:
     * this used to duplicate the entire scoring body, so the calibrated path and the
     * sync path could silently drift — the body now lives ONLY in
     * [classifySuspending], and this delegates to it). Production code must call
     * [classifySuspending]; with the default [NoOpCalibrator] results are identical.
     */
    fun classify(
        evidence: Evidence,
        rawText: String = "",
        source: NotificationSource = NotificationSource.UNKNOWN,
        packageName: String? = null
    ): ClassificationResult = kotlinx.coroutines.runBlocking {
        classifySuspending(evidence, rawText, source, packageName)
    }

    /**
     * Qualification-first pipeline entry point:
     * 1. Evaluates the trained statistical text gate directly on [fullText] (ZERO regexes, microseconds).
     * 2. If non-financial noise (p < 0.30), returns immediately with [Evidence.EMPTY] and null fields.
     *    Bypasses sentence splitting, regex matching, and merchant resolution completely.
     * 3. Only if qualified (p >= 0.30), extracts evidence, amount, type, account, merchant, and UTR.
     * 4. If no trained gate is present (legacy tests), falls back to [classifySuspending].
     */
    suspend fun classifyRaw(
        raw: RawMessage,
        fullText: String = if (raw.title.isNotBlank()) "${raw.title} ${raw.text}" else raw.text,
        source: NotificationSource = NotificationSource.UNKNOWN
    ): ClassificationResult {
        val gate = gateModelProvider?.model()
        if (gate != null) {
            val gateScore = gate.scoreText(fullText)
            val p = gateScore.pTransaction
            if (p < 0.30) {
                return ClassificationResult(
                    pTransaction = p,
                    fieldConfidences = FieldConfidences(amount = 0.0, type = 0.0, accountLast4 = 0.0, merchant = 0.0),
                    evidence = Evidence.EMPTY,
                    contributions = gateScore.contributions,
                    source = source,
                    amount = null,
                    type = null,
                    accountLast4 = null,
                    merchant = null,
                    bankHint = null,
                    utr = null
                )
            }

            val evidence = evidenceExtractor.extract(raw, source)
            val amount = evidenceExtractor.extractAmount(fullText)
            val type = evidenceExtractor.extractType(fullText)
            val accountLast4 = evidenceExtractor.extractAccountLast4(fullText)
            val merchant = evidenceExtractor.extractMerchant(fullText, raw.packageName)
            val bankHint = if (evidence.senderLooksBank) "Bank" else null
            val utr = evidenceExtractor.extractUtr(fullText)

            val fieldConfidences = fieldConfidenceEstimator.estimate(
                rawText = fullText,
                amount = amount,
                type = type,
                accountLast4 = accountLast4,
                merchant = merchant
            )

            return ClassificationResult(
                pTransaction = p,
                fieldConfidences = fieldConfidences,
                evidence = evidence,
                contributions = gateScore.contributions,
                source = source,
                amount = amount,
                type = type,
                accountLast4 = accountLast4,
                merchant = merchant,
                bankHint = bankHint,
                utr = utr
            )
        } else {
            val evidence = evidenceExtractor.extract(raw, source)
            return classifySuspending(evidence, fullText, source, raw.packageName)
        }
    }

    /**
     * Synchronous convenience wrapper for classifyRaw (primarily for tests).
     */
    fun classifyRawSync(
        raw: RawMessage,
        fullText: String = if (raw.title.isNotBlank()) "${raw.title} ${raw.text}" else raw.text,
        source: NotificationSource = NotificationSource.UNKNOWN
    ): ClassificationResult = kotlinx.coroutines.runBlocking {
        classifyRaw(raw, fullText, source)
    }

    suspend fun classifySuspending(
        evidence: Evidence,
        rawText: String = "",
        source: NotificationSource = NotificationSource.UNKNOWN,
        packageName: String? = null
    ): ClassificationResult {
        val priorLogit = ln(Priors.P_TRANSACTION / (1.0 - Priors.P_TRANSACTION))
        var logit = priorLogit

        val contributions = mutableListOf<Contribution>()

        val gate = gateModelProvider?.model()
        if (gate != null) {
            // Trained path: every weight, including the intercept shift, comes from the
            // artifact. The n-gram block is scored inside the model.
            val gateScore = gate.score(evidence, rawText)
            logit = gateScore.logit
            contributions.addAll(gateScore.contributions)
        } else {
            suspend fun apply(name: String, present: Boolean, baseLR: Double) {
                if (!present) return
                val lr = calibrator.calibratedLR(name, baseLR)
                val delta = ln(lr)
                logit += delta
                contributions.add(Contribution(name, delta, lr))
            }

            apply("amount_in_context", evidence.amountInTransactionContext, LikelihoodRatios.AMOUNT_IN_CONTEXT)
            apply("amount_debit_sent", evidence.amountAndDebitSameSentence, LikelihoodRatios.AMOUNT_AND_DEBIT_SAME_SENT)
            apply("amount_credit_sent", evidence.amountAndCreditSameSentence, LikelihoodRatios.AMOUNT_AND_CREDIT_SAME_SENT)
            apply("amount_forbidden_shape", evidence.amountHasForbiddenShape, LikelihoodRatios.AMOUNT_FORBIDDEN_SHAPE)
            apply("amount", evidence.hasAmount && !evidence.amountInTransactionContext, LikelihoodRatios.AMOUNT_ANYWHERE)
            apply("account", evidence.hasAccount, LikelihoodRatios.ACCOUNT_PRESENT)
            apply("utr", evidence.hasUtr, LikelihoodRatios.UTR_PRESENT)
            apply("bank_sender", evidence.senderLooksBank, LikelihoodRatios.BANK_SENDER_HINT)
            apply("upi_handle", evidence.hasUpiHandle, LikelihoodRatios.UPI_HANDLE_PRESENT)
            apply("balance", evidence.hasBalanceMention, LikelihoodRatios.BALANCE_MENTIONED)
            apply("merchant_pkg", evidence.fromMerchantPackage, LikelihoodRatios.MERCHANT_APP_PACKAGE)
            apply("success", evidence.hasSuccessWord, LikelihoodRatios.SUCCESS_WORD)
            apply("OTP", evidence.hasOtp, LikelihoodRatios.OTP_KEYWORD)
            apply("promo", evidence.hasPromo, LikelihoodRatios.PROMO_KEYWORD)
            apply("offer", evidence.hasOffer, LikelihoodRatios.OFFER_KEYWORD)
            apply("recent_similar_amount", evidence.recentSimilarAmount, LikelihoodRatios.RECENT_SIMILAR_AMOUNT)
            apply("recent_same_merchant", evidence.recentSameMerchant, LikelihoodRatios.RECENT_SAME_MERCHANT)
            apply("burst_detected", evidence.burstDetected, LikelihoodRatios.BURST_DETECTED)
        }

        if (evidence.tokenCount > 0) {
            logit += evidence.tokenLogOdds
            contributions.add(Contribution("learned_tokens", evidence.tokenLogOdds, exp(evidence.tokenLogOdds)))
        }

        val p = 1.0 / (1.0 + exp(-logit))

        // Qualification Gate: Only extract transaction fields if message is qualified as a candidate
        // (p >= 0.30, matching PolicyEngine's minimum threshold).
        // Non-financial noise (social posts, chat summaries, crypto news, OTPs) is dropped early
        // without running regex amount or merchant extraction.
        val isQualifiedCandidate = p >= 0.30

        val amount = if (isQualifiedCandidate) evidenceExtractor.extractAmount(rawText) else null
        val type = if (isQualifiedCandidate) evidenceExtractor.extractType(rawText) else null
        val accountLast4 = if (isQualifiedCandidate) evidenceExtractor.extractAccountLast4(rawText) else null
        val merchant = if (isQualifiedCandidate) evidenceExtractor.extractMerchant(rawText, packageName) else null
        val bankHint = if (isQualifiedCandidate && evidence.senderLooksBank) "Bank" else null
        val utr = if (isQualifiedCandidate) evidenceExtractor.extractUtr(rawText) else null

        val fieldConfidences = if (isQualifiedCandidate) {
            fieldConfidenceEstimator.estimate(
                rawText = rawText,
                amount = amount,
                type = type,
                accountLast4 = accountLast4,
                merchant = merchant
            )
        } else {
            FieldConfidences(amount = 0.0, type = 0.0, accountLast4 = 0.0, merchant = 0.0)
        }

        return ClassificationResult(
            pTransaction = p,
            fieldConfidences = fieldConfidences,
            evidence = evidence,
            contributions = contributions,
            source = source,
            amount = amount,
            type = type,
            accountLast4 = accountLast4,
            merchant = merchant,
            bankHint = bankHint,
            utr = utr
        )
    }
}
