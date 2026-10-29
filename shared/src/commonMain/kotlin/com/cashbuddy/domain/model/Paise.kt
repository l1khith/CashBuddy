package com.cashbuddy.domain.model

import kotlin.jvm.JvmInline
import kotlin.math.roundToLong

/**
 * Exact integer representation of currency in Indian Paise (1 Rupee = 100 Paise).
 * Eliminates IEEE-754 binary floating-point rounding errors across ledgers and budgets.
 */
@JvmInline
value class Paise(val value: Long) : Comparable<Paise> {

    operator fun plus(other: Paise): Paise = Paise(value + other.value)
    operator fun minus(other: Paise): Paise = Paise(value - other.value)
    operator fun unaryMinus(): Paise = Paise(-value)
    operator fun times(factor: Long): Paise = Paise(value * factor)

    /**
     * Integer division returning Paise.
     */
    operator fun div(divisor: Long): Paise {
        require(divisor != 0L) { "Divisor cannot be zero" }
        return Paise(value / divisor)
    }

    /**
     * Floating point division used strictly for ratios and percentages.
     */
    fun ratio(other: Paise): Double {
        if (other.value == 0L) return 0.0
        return value.toDouble() / other.value.toDouble()
    }

    override fun compareTo(other: Paise): Int = value.compareTo(other.value)

    fun toRupees(): Double = value / 100.0

    override fun toString(): String = "${value / 100}.${(kotlin.math.abs(value) % 100).toString().padStart(2, '0')}"

    companion object {
        val ZERO = Paise(0L)

        fun fromRupees(rupees: Double): Paise {
            return Paise((rupees * 100.0).roundToLong())
        }

        fun fromPaise(paise: Long): Paise = Paise(paise)
    }
}
