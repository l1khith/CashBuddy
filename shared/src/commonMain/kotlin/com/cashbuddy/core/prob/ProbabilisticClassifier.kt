// NO-NETWORK
package com.cashbuddy.core.prob

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
    val bankHint: String?
)

class ProbabilisticClassifier(
    private val calibrator: Calibrator = NoOpCalibrator,
    private val evidenceExtractor: EvidenceExtractor = EvidenceExtractor(),
    private val fieldConfidenceEstimator: FieldConfidenceEstimator = FieldConfidenceEstimator()
) {

    fun classify(
        evidence: Evidence,
        rawText: String = "",
        source: NotificationSource = NotificationSource.UNKNOWN
    ): ClassificationResult {
        val priorLogit = ln(Priors.P_TRANSACTION / (1.0 - Priors.P_TRANSACTION))
        var logit = priorLogit

        val contributions = mutableListOf<Contribution>()

        fun apply(name: String, present: Boolean, baseLR: Double) {
            if (!present) return
            val lr = baseLR
            val delta = ln(lr)
            logit += delta
            contributions.add(Contribution(name, delta, lr))
        }

        apply("amount", evidence.hasAmount, LikelihoodRatios.AMOUNT_PRESENT)
        apply("account", evidence.hasAccount, LikelihoodRatios.ACCOUNT_PRESENT)
        apply("utr", evidence.hasUtr, LikelihoodRatios.UTR_PRESENT)
        apply("debit", evidence.hasDebit, LikelihoodRatios.DEBIT_KEYWORD)
        apply("credit", evidence.hasCredit, LikelihoodRatios.CREDIT_KEYWORD)
        apply("amount_type", evidence.hasAmount && (evidence.hasDebit || evidence.hasCredit), LikelihoodRatios.AMOUNT_AND_TYPE)
        apply("bank_sender", evidence.senderLooksBank, LikelihoodRatios.BANK_SENDER_HINT)
        apply("upi_handle", evidence.hasUpiHandle, LikelihoodRatios.UPI_HANDLE_PRESENT)
        apply("balance", evidence.hasBalanceMention, LikelihoodRatios.BALANCE_MENTIONED)
        apply("verb", evidence.hasTransactionVerb, LikelihoodRatios.TRANSACTION_VERB)
        apply("merchant_pkg", evidence.fromMerchantPackage, LikelihoodRatios.MERCHANT_APP_PACKAGE)
        apply("success", evidence.hasSuccessWord, LikelihoodRatios.SUCCESS_WORD)
        apply("OTP", evidence.hasOtp, LikelihoodRatios.OTP_KEYWORD)
        apply("promo", evidence.hasPromo, LikelihoodRatios.PROMO_KEYWORD)
        apply("offer", evidence.hasOffer, LikelihoodRatios.OFFER_KEYWORD)
        apply("recent_amount", evidence.recentSameAmount, LikelihoodRatios.SAME_AMOUNT_RECENT)
        apply("recent_merchant", evidence.recentSameMerchant, LikelihoodRatios.SAME_MERCHANT_RECENT)
        apply("velocity", evidence.velocityHigh, LikelihoodRatios.VELOCITY_HIGH)

        val p = 1.0 / (1.0 + exp(-logit))

        val amount = evidenceExtractor.extractAmount(rawText)
        val type = evidenceExtractor.extractType(rawText)
        val accountLast4 = evidenceExtractor.extractAccountLast4(rawText)
        val merchant = evidenceExtractor.extractMerchant(rawText)
        val bankHint = if (evidence.senderLooksBank) "Bank" else null

        val fieldConfidences = fieldConfidenceEstimator.estimate(
            rawText = rawText,
            amount = amount,
            type = type,
            accountLast4 = accountLast4,
            merchant = merchant
        )

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
            bankHint = bankHint
        )
    }

    suspend fun classifySuspending(
        evidence: Evidence,
        rawText: String = "",
        source: NotificationSource = NotificationSource.UNKNOWN
    ): ClassificationResult {
        val priorLogit = ln(Priors.P_TRANSACTION / (1.0 - Priors.P_TRANSACTION))
        var logit = priorLogit

        val contributions = mutableListOf<Contribution>()

        suspend fun apply(name: String, present: Boolean, baseLR: Double) {
            if (!present) return
            val lr = calibrator.calibratedLR(name, baseLR)
            val delta = ln(lr)
            logit += delta
            contributions.add(Contribution(name, delta, lr))
        }

        apply("amount", evidence.hasAmount, LikelihoodRatios.AMOUNT_PRESENT)
        apply("account", evidence.hasAccount, LikelihoodRatios.ACCOUNT_PRESENT)
        apply("utr", evidence.hasUtr, LikelihoodRatios.UTR_PRESENT)
        apply("debit", evidence.hasDebit, LikelihoodRatios.DEBIT_KEYWORD)
        apply("credit", evidence.hasCredit, LikelihoodRatios.CREDIT_KEYWORD)
        apply("amount_type", evidence.hasAmount && (evidence.hasDebit || evidence.hasCredit), LikelihoodRatios.AMOUNT_AND_TYPE)
        apply("bank_sender", evidence.senderLooksBank, LikelihoodRatios.BANK_SENDER_HINT)
        apply("upi_handle", evidence.hasUpiHandle, LikelihoodRatios.UPI_HANDLE_PRESENT)
        apply("balance", evidence.hasBalanceMention, LikelihoodRatios.BALANCE_MENTIONED)
        apply("verb", evidence.hasTransactionVerb, LikelihoodRatios.TRANSACTION_VERB)
        apply("merchant_pkg", evidence.fromMerchantPackage, LikelihoodRatios.MERCHANT_APP_PACKAGE)
        apply("success", evidence.hasSuccessWord, LikelihoodRatios.SUCCESS_WORD)
        apply("OTP", evidence.hasOtp, LikelihoodRatios.OTP_KEYWORD)
        apply("promo", evidence.hasPromo, LikelihoodRatios.PROMO_KEYWORD)
        apply("offer", evidence.hasOffer, LikelihoodRatios.OFFER_KEYWORD)
        apply("recent_amount", evidence.recentSameAmount, LikelihoodRatios.SAME_AMOUNT_RECENT)
        apply("recent_merchant", evidence.recentSameMerchant, LikelihoodRatios.SAME_MERCHANT_RECENT)
        apply("velocity", evidence.velocityHigh, LikelihoodRatios.VELOCITY_HIGH)

        val p = 1.0 / (1.0 + exp(-logit))

        val amount = evidenceExtractor.extractAmount(rawText)
        val type = evidenceExtractor.extractType(rawText)
        val accountLast4 = evidenceExtractor.extractAccountLast4(rawText)
        val merchant = evidenceExtractor.extractMerchant(rawText)
        val bankHint = if (evidence.senderLooksBank) "Bank" else null

        val fieldConfidences = fieldConfidenceEstimator.estimate(
            rawText = rawText,
            amount = amount,
            type = type,
            accountLast4 = accountLast4,
            merchant = merchant
        )

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
            bankHint = bankHint
        )
    }
}
