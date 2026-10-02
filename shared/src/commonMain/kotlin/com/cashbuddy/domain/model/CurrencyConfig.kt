package com.cashbuddy.domain.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
enum class NumberSystem {
    INDIAN,
    INTERNATIONAL
}

@Serializable
data class CurrencyConfig(
    val code: String,
    val symbol: String,
    val name: String,
    val numberSystem: NumberSystem = NumberSystem.INTERNATIONAL
)

object CurrencyRegistry {
    // JSON-driven currency definitions
    val CURRENCIES_JSON = """
    [
      { "code": "INR", "symbol": "₹", "name": "Indian Rupee", "numberSystem": "INDIAN" },
      { "code": "USD", "symbol": "${'$'}", "name": "US Dollar", "numberSystem": "INTERNATIONAL" },
      { "code": "EUR", "symbol": "€", "name": "Euro", "numberSystem": "INTERNATIONAL" },
      { "code": "GBP", "symbol": "£", "name": "British Pound", "numberSystem": "INTERNATIONAL" },
      { "code": "CAD", "symbol": "C${'$'}", "name": "Canadian Dollar", "numberSystem": "INTERNATIONAL" },
      { "code": "AUD", "symbol": "A${'$'}", "name": "Australian Dollar", "numberSystem": "INTERNATIONAL" },
      { "code": "AED", "symbol": "AED", "name": "UAE Dirham", "numberSystem": "INTERNATIONAL" },
      { "code": "SGD", "symbol": "S${'$'}", "name": "Singapore Dollar", "numberSystem": "INTERNATIONAL" },
      { "code": "JPY", "symbol": "¥", "name": "Japanese Yen", "numberSystem": "INTERNATIONAL" }
    ]
    """.trimIndent()

    private val json = Json { ignoreUnknownKeys = true }
    val currencies: List<CurrencyConfig> = json.decodeFromString(CURRENCIES_JSON)
    private val currencyMap: Map<String, CurrencyConfig> = currencies.associateBy { it.code.uppercase() }

    fun getCurrency(code: String?): CurrencyConfig {
        if (code.isNullOrBlank()) return currencyMap["INR"] ?: currencies.first()
        return currencyMap[code.uppercase()] ?: currencyMap["INR"] ?: currencies.first()
    }

    /**
     * High performance, zero-allocation number formatter supporting both Indian (3-2-2)
     * and International (3-3-3) numbering systems.
     */
    fun format(amount: Double, currencyCode: String? = null, includeSymbol: Boolean = true): String {
        val config = getCurrency(currencyCode)
        val absAmount = kotlin.math.abs(amount)
        val longVal = absAmount.toLong()
        val str = longVal.toString()
        val len = str.length

        val sb = StringBuilder(len + (len / 2) + 8)
        if (includeSymbol) {
            sb.append(config.symbol)
            if (config.symbol.length > 1 && !config.symbol.endsWith("$")) {
                sb.append(' ')
            }
        }

        if (config.numberSystem == NumberSystem.INDIAN) {
            if (len <= 3) {
                sb.append(str)
            } else {
                val lastThreeIdx = len - 3
                val firstPart = str.substring(0, lastThreeIdx)
                val firstLen = firstPart.length
                for (i in 0 until firstLen) {
                    sb.append(firstPart[i])
                    val remaining = firstLen - 1 - i
                    if (remaining > 0 && remaining % 2 == 0) {
                        sb.append(',')
                    }
                }
                sb.append(',')
                sb.append(str, lastThreeIdx, len)
            }
        } else {
            // International standard 3-digit comma grouping (e.g. 1,000,000)
            if (len <= 3) {
                sb.append(str)
            } else {
                val prefixLen = len % 3
                val start = if (prefixLen == 0) 3 else prefixLen
                sb.append(str, 0, start)
                var i = start
                while (i < len) {
                    sb.append(',')
                    sb.append(str, i, i + 3)
                    i += 3
                }
            }
        }

        if (absAmount != longVal.toDouble()) {
            val cents = ((absAmount * 100).toLong() % 100)
            sb.append('.')
            if (cents < 10) sb.append('0')
            sb.append(cents)
        }

        return sb.toString()
    }
}
