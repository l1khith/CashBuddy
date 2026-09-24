package com.cashbuddy.screenshot

import android.content.Context
import android.net.Uri
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

    /**
     * Run ML Kit OCR on a locally cached image file and parse into structured transaction via Rust core.
     */
    suspend fun parseImageFile(file: File): ScreenshotTransaction? = withContext(Dispatchers.IO) {
        val image = InputImage.fromFilePath(context, Uri.fromFile(file))
        val ocrText = extractText(image)
        if (ocrText.isBlank()) return@withContext null

        parseScreenshotText(ocrText)
    }

    private suspend fun extractText(image: InputImage): String = suspendCancellableCoroutine { continuation ->
        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                continuation.resume(visionText.text)
            }
            .addOnFailureListener { exception ->
                continuation.resumeWithException(exception)
            }
    }
}
