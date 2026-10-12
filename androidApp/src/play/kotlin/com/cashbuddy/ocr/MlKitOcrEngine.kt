package com.cashbuddy.ocr

import android.content.Context
import android.graphics.BitmapFactory
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class MlKitOcrEngine(
    @Suppress("UNUSED_PARAMETER") private val context: Context
) : OcrEngine {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    override val isAvailable: Boolean = true

    override suspend fun recognizeText(imageBytes: ByteArray): OcrResult = withContext(Dispatchers.Default) {
        try {
            val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                ?: return@withContext OcrResult(
                    text = "",
                    success = false,
                    errorMessage = "Failed to decode image bytes into Bitmap"
                )

            val inputImage = InputImage.fromBitmap(bitmap, 0)
            val recognizedText = extractText(inputImage)
            OcrResult(
                text = recognizedText,
                success = true,
                errorMessage = null
            )
        } catch (e: Throwable) {
            Log.e(TAG, "ML Kit OCR failed", e)
            OcrResult(
                text = "",
                success = false,
                errorMessage = e.message ?: "ML Kit text recognition failed"
            )
        }
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

    companion object {
        private const val TAG = "MlKitOcrEngine"
    }
}
