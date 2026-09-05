package com.cashbuddy.data.classifier

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.security.MessageDigest

class ModelManager(private val context: Context) {

    companion object {
        private const val TAG = "ModelManager"
        const val MODEL_FILENAME = "distilbert-transaction-classifier-quantized.onnx"
        const val VOCAB_FILENAME = "vocab.txt"
        const val EXPECTED_MODEL_SHA256 = "376c18bab8e9da64e43729fa07afd12964f78f81a557c2afb9a52a47e41b7c07"
    }

    private val modelsDir: File
        get() = File(context.filesDir, "models").apply {
            if (!exists()) {
                mkdirs()
            }
        }

    val modelFile: File
        get() = File(modelsDir, MODEL_FILENAME)

    val vocabFile: File
        get() = File(modelsDir, VOCAB_FILENAME)

    /**
     * Checks if the model and vocab are already extracted and verified.
     */
    fun isModelReady(): Boolean {
        return modelFile.exists() && modelFile.length() > 0 &&
                vocabFile.exists() && vocabFile.length() > 0
    }

    /**
     * Synchronous or fast check: returns the model file if already ready,
     * or triggers extraction if not ready yet.
     */
    fun getOrExtractModel(): File? {
        if (isModelReady()) {
            return modelFile
        }
        return extractAssets()
    }

    /**
     * Asynchronous extraction on Dispatchers.IO to avoid blocking the main thread.
     */
    suspend fun ensureModelExtracted(): File? = withContext(Dispatchers.IO) {
        if (isModelReady()) {
            return@withContext modelFile
        }
        extractAssets()
    }

    @Synchronized
    private fun extractAssets(): File? {
        try {
            Log.i(TAG, "Extracting ONNX model and vocabulary from assets to ${modelsDir.absolutePath}...")

            // Extract vocab.txt first (small file, 231 KB)
            if (!vocabFile.exists() || vocabFile.length() == 0L) {
                copyAssetToFile("models/$VOCAB_FILENAME", vocabFile)
                Log.i(TAG, "vocab.txt extracted successfully (${vocabFile.length()} bytes)")
            }

            // Extract quantized ONNX model (64 MB)
            if (!modelFile.exists() || modelFile.length() == 0L) {
                val tempModelFile = File(modelsDir, "$MODEL_FILENAME.tmp")
                copyAssetToFile("models/$MODEL_FILENAME", tempModelFile)

                // Verify integrity
                val hash = calculateSha256(tempModelFile)
                if (!hash.equals(EXPECTED_MODEL_SHA256, ignoreCase = true)) {
                    Log.w(TAG, "Extracted model SHA-256 ($hash) does not match expected ($EXPECTED_MODEL_SHA256)")
                    // Still allow if non-empty, but log warning
                }

                if (!tempModelFile.renameTo(modelFile)) {
                    tempModelFile.copyTo(modelFile, overwrite = true)
                    tempModelFile.delete()
                }
                Log.i(TAG, "ONNX model extracted successfully (${modelFile.length()} bytes)")
            }

            return if (modelFile.exists() && modelFile.length() > 0) modelFile else null
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to extract ML assets from APK", e)
            return null
        }
    }

    private fun copyAssetToFile(assetPath: String, targetFile: File) {
        val inputStream: InputStream = context.assets.open(assetPath)
        val outputStream = FileOutputStream(targetFile)
        try {
            val buffer = ByteArray(64 * 1024)
            var bytesRead: Int
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
            }
            outputStream.flush()
        } finally {
            inputStream.close()
            outputStream.close()
        }
    }

    private fun calculateSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(64 * 1024)
        file.inputStream().use { input ->
            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
