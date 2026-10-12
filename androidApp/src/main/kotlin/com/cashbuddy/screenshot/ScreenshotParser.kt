package com.cashbuddy.screenshot

import android.util.Log
import com.cashbuddy.core.ScreenshotTransaction
import com.cashbuddy.core.parseScreenshotText
import com.cashbuddy.ocr.OcrEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class ScreenshotParser(
    private val ocrEngine: OcrEngine
) {

    companion object {
        private const val TAG = "ScreenshotParser"
    }

    /**
     * Run OCR via OcrEngine on a locally cached image file and parse into structured transaction via Kotlin engine.
     */
    suspend fun parseImageFile(file: File): ScreenshotTransaction? = withContext(Dispatchers.IO) {
        if (!ocrEngine.isAvailable) {
            Log.w(TAG, "OCR engine is not available in this build flavor")
            return@withContext null
        }

        Log.d(TAG, "Starting OCR on file: ${file.name} (${file.length()} bytes)")

        val imageBytes = try {
            file.readBytes()
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to read screenshot file: ${file.name}", e)
            return@withContext null
        }

        val ocrResult = ocrEngine.recognizeText(imageBytes)
        if (!ocrResult.success) {
            Log.e(TAG, "OCR failed for ${file.name}: ${ocrResult.errorMessage}")
            return@withContext null
        }

        val ocrText = ocrResult.text
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
}
