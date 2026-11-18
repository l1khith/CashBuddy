// NO-NETWORK
package com.cashbuddy.core.prob.gate

/**
 * Hashed n-gram weights exported by `trainingdata/train_gate.py`.
 *
 * Binary layout (little endian):
 * ```
 * "CBG1"                        4 bytes magic
 * uint32 entryCount             number of (bucket, weight) pairs
 * entryCount x { uint32 bucket, int16 weight }
 * ```
 * Entries are sorted by bucket so lookup is a binary search over an [IntArray] with a
 * parallel [ShortArray] — no boxing, ~200 KB for the default 14k-entry table.
 *
 * A bucket's weight is the trained logit contribution of every token hashed into it;
 * dividing the summed int16 values by [scale] reproduces the trainer's float weight to
 * within 1/scale.
 */
class GateNgrams private constructor(
    private val buckets: IntArray,
    private val weights: ShortArray,
    val scale: Int
) {
    val size: Int get() = buckets.size

    /** Additive logit contribution of the tokens in [buckets] (duplicates are ignored). */
    fun score(buckets: Set<Int>): Double {
        var sum = 0
        for (bucket in buckets) {
            val index = indexOf(bucket)
            if (index >= 0) sum += weights[index].toInt()
        }
        return sum.toDouble() / scale.toDouble()
    }

    private fun indexOf(bucket: Int): Int {
        var low = 0
        var high = buckets.size - 1
        while (low <= high) {
            val mid = (low + high).ushr(1)
            val value = buckets[mid]
            when {
                value < bucket -> low = mid + 1
                value > bucket -> high = mid - 1
                else -> return mid
            }
        }
        return -1
    }

    companion object {
        private const val HEADER_BYTES = 8
        private const val ENTRY_BYTES = 6

        /**
         * Parses the artifact. Returns null for any structural problem so the caller can
         * degrade to feature-only scoring instead of scoring garbage.
         */
        fun parse(bytes: ByteArray, expectedBuckets: Int): GateNgrams? {
            if (bytes.size < HEADER_BYTES) return null
            if (bytes[0] != 0x43.toByte() || bytes[1] != 0x42.toByte() ||
                bytes[2] != 0x47.toByte() || bytes[3] != 0x31.toByte()
            ) {
                return null
            }
            val count = readUInt32(bytes, 4)
            if (count <= 0 || bytes.size != HEADER_BYTES + count * ENTRY_BYTES) return null

            val bucketArray = IntArray(count)
            val weightArray = ShortArray(count)
            var offset = HEADER_BYTES
            var previous = -1
            for (index in 0 until count) {
                val bucket = readUInt32(bytes, offset)
                if (bucket <= previous || bucket >= expectedBuckets) return null
                previous = bucket
                bucketArray[index] = bucket
                weightArray[index] = readInt16(bytes, offset + 4)
                offset += ENTRY_BYTES
            }
            return GateNgrams(bucketArray, weightArray, NGRAM_SCALE)
        }

        /** Fixed-point divisor used by the trainer (`NGRAM_SCALE`). */
        const val NGRAM_SCALE = 4096

        private fun readUInt32(bytes: ByteArray, offset: Int): Int =
            (bytes[offset].toInt() and 0xFF) or
                ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
                ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
                ((bytes[offset + 3].toInt() and 0xFF) shl 24)

        private fun readInt16(bytes: ByteArray, offset: Int): Short =
            ((bytes[offset].toInt() and 0xFF) or (bytes[offset + 1].toInt() shl 8)).toShort()
    }
}
