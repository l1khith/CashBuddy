package com.cashbuddy.screenshot

import android.content.Context
import android.net.Uri
import android.util.Log
import com.cashbuddy.core.ScreenshotTransaction
import com.cashbuddy.core.parseScreenshotText
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class ScreenshotParser(private val context: Context) {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    companion object {
        private const val TAG = "ScreenshotParser"
    }

    /**
     * Run ML Kit OCR on a locally cached image file and parse into structured transaction via Kotlin engine.
     */
    suspend fun parseImageFile(file: File): ScreenshotTransaction? = withContext(Dispatchers.IO) {
        Log.d(TAG, "Starting OCR on file: ${file.name} (${file.length()} bytes)")

        val image = InputImage.fromFilePath(context, Uri.fromFile(file))
        val ocrText = try {
            extractText(image)
        } catch (e: Throwable) {
            Log.e(TAG, "ML Kit OCR failed for ${file.name}", e)
            return@withContext null
        }

        if (ocrText.isBlank()) {
            Log.w(TAG, "OCR returned empty text for ${file.name}")
            return@withContext null
        }

        Log.d(TAG, "OCR extracted ${ocrText.length} chars from ${file.name}")
        Log.d(TAG, "OCR text preview: ${ocrText.take(200)}")

        val result = try {
            parseScreenshotText(ocrText)
        } catch (e: Throwable) {
            Log.e(TAG, "parseScreenshotText failed", e)
            null
        }

        if (result != null) {
            Log.i(TAG, "Parsed screenshot: ₹${result.amount} ${result.transactionType} to ${result.merchant} (${result.appName}, confidence=${result.confidence})")
        } else {
            Log.w(TAG, "Parser returned null for OCR text: ${ocrText.take(100)}")
        }

        result
    }

    private suspend fun extractText(image: InputImage): String = suspendCancellableCoroutine { continuation ->
        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                Log.d(TAG, "ML Kit success: ${visionText.textBlocks.size} text blocks found")
                continuation.resume(visionText.text)
            }
            .addOnFailureListener { exception ->
                Log.e(TAG, "ML Kit processing failed", exception)
                continuation.resumeWithException(exception)
            }
    }
}
