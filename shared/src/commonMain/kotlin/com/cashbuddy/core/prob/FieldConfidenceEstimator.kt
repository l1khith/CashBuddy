// NO-NETWORK
package com.cashbuddy.core.prob

import com.cashbuddy.domain.model.TransactionType

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class FieldConfidences(
    val amount: Double,
    val type: Double,
    val accountLast4: Double,
    val merchant: Double
) {
    fun toJson(): String = jsonInstance.encodeToString(this)

    companion object {
        private val jsonInstance = Json { ignoreUnknownKeys = true; prettyPrint = false }
        fun fromJson(json: String): FieldConfidences = jsonInstance.decodeFromString(json)
    }
}

class FieldConfidenceEstimator {
    fun estimate(
        rawText: String,
        amount: Double?,
        type: TransactionType?,
        accountLast4: String?,
        merchant: String?,
        categoryMatchConfidence: Float = 0.5f
    ): FieldConfidences {
        val amountConf = if (amount != null && amount > 0.0) {
            if (rawText.contains('₹') || rawText.contains("Rs", ignoreCase = true) || rawText.contains("INR", ignoreCase = true)) {
                0.98
            } else {
                0.80
            }
        } else {
            0.0
        }

        val typeConf = if (type != null) {
            val tLower = rawText.lowercase()
            if (tLower.contains("debited") || tLower.contains("credited")) 0.95 else 0.85
        } else {
            0.0
        }

        val accountConf = if (!accountLast4.isNullOrBlank()) {
            0.95
        } else {
            0.0
        }

        val merchantConf = if (!merchant.isNullOrBlank()) {
            if (categoryMatchConfidence >= 0.90f) {
                0.95
            } else if (Regexes.UPI_HANDLE.containsMatchIn(rawText)) {
                0.85
            } else {
                0.70
            }
        } else {
            0.0
        }

        return FieldConfidences(
            amount = amountConf,
            type = typeConf,
            accountLast4 = accountConf,
            merchant = merchantConf
        )
    }
}
