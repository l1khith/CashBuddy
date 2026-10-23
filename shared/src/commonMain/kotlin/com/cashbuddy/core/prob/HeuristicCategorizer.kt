// NO-NETWORK
package com.cashbuddy.core.prob

import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Pure contextual and structural heuristics engine.
 * Eliminates hardcoded brand lists (e.g. Swiggy, Zomato, Uber) in favor of:
 * 1. Structural VPA (UPI handle) grammar keywords.
 * 2. Temporal & monetary contextual priors (Time + Amount) as Review Inbox suggestions.
 */
object HeuristicCategorizer {

    data class HeuristicMatch(
        val category: String,
        val confidence: Float,
        val reason: String
    )

    private val VPA_FOOD_KEYWORDS = listOf(
        "food", "cafe", "dine", "kitchen", "bake", "bakery", "restaurant", "bistro", "eat", "canteen", "dhaba", "chai", "tea", "coffee"
    )

    private val VPA_TRANSPORT_KEYWORDS = listOf(
        "cab", "ride", "auto", "metro", "toll", "fastag", "fuel", "petrol", "transit", "bmtc", "bus", "ticket", "transport"
    )

    private val VPA_GROCERY_KEYWORDS = listOf(
        "mart", "kirana", "grocer", "supermarket", "store"
    )

    private val VPA_HEALTH_KEYWORDS = listOf(
        "pharma", "pharmacy", "med", "medical", "clinic", "hospital", "care"
    )

    /**
     * Tier 2: Structural VPA handle inspection (Confidence: 0.85).
     * Inspects username tokens before the '@' sign.
     */
    fun matchVpa(vpa: String?): HeuristicMatch? {
        if (vpa.isNullOrBlank()) return null
        val handle = vpa.lowercase().substringBefore('@').trim()
        if (handle.isEmpty() || handle.all { it.isDigit() }) return null

        val tokens = handle.split('.', '_', '-', '*', '+').filter { it.length >= 3 }
        val allParts = tokens + listOf(handle)

        for (part in allParts) {
            if (VPA_FOOD_KEYWORDS.any { part.contains(it) }) {
                return HeuristicMatch("Food & Dining", 0.85f, "based on UPI handle keyword")
            }
            if (VPA_TRANSPORT_KEYWORDS.any { part.contains(it) }) {
                return HeuristicMatch("Transportation", 0.85f, "based on UPI handle keyword")
            }
            if (VPA_GROCERY_KEYWORDS.any { part.contains(it) }) {
                return HeuristicMatch("Groceries", 0.85f, "based on UPI handle keyword")
            }
            if (VPA_HEALTH_KEYWORDS.any { part.contains(it) }) {
                return HeuristicMatch("Healthcare & Medical", 0.85f, "based on UPI handle keyword")
            }
        }
        return null
    }

    /**
     * Tier 3: Contextual Prior based on Time of Day & Amount (Confidence: 0.60).
     * Used exclusively to pre-select smart suggestions in the Review Inbox.
     * Does NOT silently auto-log without user confirmation.
     */
    fun matchContext(timestamp: Long, amount: Double): HeuristicMatch? {
        if (amount <= 0.0) return null
        val hour = getHourOfDay(timestamp)

        // Morning commute: 7:00 AM - 10:59 AM and small ticket (< ₹150)
        if (hour in 7..10 && amount < 150.0) {
            return HeuristicMatch("Transportation", 0.60f, "based on morning commute time")
        }

        // Lunch: 12:00 PM - 3:59 PM and standard dining amount (₹50 - ₹600)
        if (hour in 12..15 && amount in 50.0..600.0) {
            return HeuristicMatch("Food & Dining", 0.60f, "based on lunch time")
        }

        // Evening Dinner / Groceries: 7:00 PM - 10:59 PM and dining amount (₹100 - ₹1200)
        if (hour in 19..22 && amount in 100.0..1200.0) {
            return HeuristicMatch("Food & Dining", 0.60f, "based on dinner time")
        }

        return null
    }

    private fun getHourOfDay(timestamp: Long): Int {
        return try {
            val instant = Instant.fromEpochMilliseconds(timestamp)
            instant.toLocalDateTime(TimeZone.currentSystemDefault()).hour
        } catch (_: Throwable) {
            12
        }
    }
}
