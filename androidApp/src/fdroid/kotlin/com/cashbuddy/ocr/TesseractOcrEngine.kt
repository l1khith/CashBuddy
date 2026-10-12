package com.cashbuddy.ocr

import android.content.Context
import android.graphics.BitmapFactory
import android.util.Log
import com.googlecode.tesseract.android.TessBaseAPI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * F-Droid flavor OCR implementation powered by Tesseract4Android (Apache-2.0 License).
 *
 * Bundles `eng.traineddata` (English) and `hin.traineddata` (Hindi/Devanagari) from
 * `tesseract-ocr/tessdata_fast` (Apache-2.0 License) inside `src/fdroid/assets/tessdata/`.
 * All text recognition executes 100% offline on-device with zero network permissions.
 */
class TesseractOcrEngine(
    private val context: Context
) : OcrEngine {

    override val isAvailable: Boolean = true

    private val mutex = Mutex()
    private var baseApi: TessBaseAPI? = null
    private var isInitialized: Boolean = false

    /**
     * Copies bundled `assets/tessdata/` `.traineddata` files into `context.filesDir/tessdata/`
     * if they do not exist or if their file size differs from the bundled asset.
     */
    suspend fun ensureTessDataCopied(): String = withContext(Dispatchers.IO) {
        val parentDir = context.filesDir
        val tessDataDir = File(parentDir, TESSDATA_SUBDIR)
        if (!tessDataDir.exists()) {
            tessDataDir.mkdirs()
        }

        val assetFiles = context.assets.list(TESSDATA_SUBDIR) ?: emptyArray()
        for (fileName in assetFiles) {
            if (!fileName.endsWith(".traineddata")) continue
            val assetPath = "$TESSDATA_SUBDIR/$fileName"
            val destFile = File(tessDataDir, fileName)

            val assetBytes = context.assets.open(assetPath).use { it.readBytes() }
            if (!destFile.exists() || destFile.length() != assetBytes.size.toLong()) {
                FileOutputStream(destFile).use { output ->
                    output.write(assetBytes)
                    output.flush()
                }
                Log.d(TAG, "Copied $fileName (${assetBytes.size} bytes) to ${destFile.absolutePath}")
            }
        }

        // Tesseract expects the parent directory path that CONTAINS the "tessdata" subdirectory
        parentDir.absolutePath
    }

    override suspend fun recognizeText(imageBytes: ByteArray): OcrResult {
        val dataPath = try {
            ensureTessDataCopied()
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to prepare tessdata directory", e)
            return OcrResult(
                text = "",
                success = false,
                errorMessage = e.message ?: "Failed to copy Tesseract traineddata from assets"
            )
        }

        return withContext(Dispatchers.Default) {
            mutex.withLock {
                var bitmap: android.graphics.Bitmap? = null
                try {
                    bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                        ?: return@withLock OcrResult(
                            text = "",
                            success = false,
                            errorMessage = "Failed to decode image bytes into Bitmap"
                        )

                    val api = getOrInitApi(dataPath)
                    api.setImage(bitmap)
                    val recognizedText = api.utF8Text ?: ""
                    Log.d(TAG, "Tesseract OCR extracted ${recognizedText.length} chars")
                    OcrResult(
                        text = recognizedText,
                        success = true,
                        errorMessage = null
                    )
                } catch (e: Throwable) {
                    Log.e(TAG, "Tesseract OCR failed, resetting engine instance", e)
                    resetApi()
                    OcrResult(
                        text = "",
                        success = false,
                        errorMessage = e.message ?: "Tesseract text recognition failed"
                    )
                } finally {
                    try {
                        baseApi?.clear()
                    } catch (_: Throwable) {
                    }
                    bitmap?.recycle()
                }
            }
        }
    }

    private fun getOrInitApi(dataPath: String): TessBaseAPI {
        val current = baseApi
        if (current != null && isInitialized) {
            return current
        }

        resetApi()
        val newApi = TessBaseAPI()
        val initialized = newApi.init(dataPath, LANGUAGES)
        if (!initialized) {
            newApi.recycle()
            throw IllegalStateException("TessBaseAPI.init failed for path=$dataPath languages=$LANGUAGES")
        }
        baseApi = newApi
        isInitialized = true
        return newApi
    }

    private fun resetApi() {
        try {
            baseApi?.recycle()
        } catch (_: Throwable) {
        }
        baseApi = null
        isInitialized = false
    }

    companion object {
        private const val TAG = "TesseractOcrEngine"
        private const val TESSDATA_SUBDIR = "tessdata"
        private const val LANGUAGES = "eng+hin"
    }
}
