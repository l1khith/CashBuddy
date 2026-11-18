// NO-NETWORK
package com.cashbuddy.core.prob.gate

/**
 * Normalised token -> hash-bucket projection, byte-for-byte compatible with
 * `ngram_buckets()` / `fnv1a32()` in `trainingdata/train_gate.py`.
 *
 * Normalisation: lowercase, digit runs -> `num`, currency words -> `cur`, everything that
 * is not `[a-z0-9@]` becomes a space. Tokens: word unigrams (`w:`), word bigrams (`b:`)
 * and character 3/4/5-grams of `^word$` (`c:`). Every token is hashed with FNV-1a 32 and
 * masked into the artifact's bucket space; duplicates per message collapse to one bucket.
 *
 * Keeping this in commonMain (not in the trainer only) is deliberate: the Python side is
 * tested against this implementation by a golden parity test, and any divergence shows up
 * as a test failure rather than as silently wrong scores on the phone.
 */
object GateTokenizer {
    private val DIGITS = Regex("[0-9]+")
    private val CURRENCY_WORDS = Regex("""₹|rs\.?|inr""")
    private val NON_TOKEN = Regex("[^a-z0-9@]+")
    private const val FNV_OFFSET_BASIS = -2128831035 // 2166136261 as a signed Int
    private const val FNV_PRIME = 16777619

    fun buckets(text: String, bucketMask: Int): Set<Int> {
        if (text.isEmpty() || bucketMask <= 0) return emptySet()
        val normalized = NON_TOKEN.replace(
            CURRENCY_WORDS.replace(DIGITS.replace(text.lowercase(), " num "), " cur "),
            " "
        ).trim()
        if (normalized.isEmpty()) return emptySet()

        val words = normalized.split(' ').filter { it.isNotEmpty() }
        val out = HashSet<Int>(words.size * 4)
        for (word in words) out.add(fnv1a32("w:$word") and bucketMask)
        for (index in 0 until words.size - 1) {
            out.add(fnv1a32("b:${words[index]}_${words[index + 1]}") and bucketMask)
        }
        for (word in words) {
            val padded = "^$word$"
            for (size in 3..5) {
                if (padded.length < size) continue
                for (start in 0..padded.length - size) {
                    out.add(fnv1a32("c:" + padded.substring(start, start + size)) and bucketMask)
                }
            }
        }
        return out
    }

    /** FNV-1a over UTF-8 bytes, wrapping at 32 bits exactly like the Python original. */
    fun fnv1a32(token: String): Int {
        var hash = FNV_OFFSET_BASIS
        for (byte in token.encodeToByteArray()) {
            hash = hash xor (byte.toInt() and 0xFF)
            hash *= FNV_PRIME
        }
        return hash
    }
}
