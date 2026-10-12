// NO-NETWORK
package com.cashbuddy.ocr

data class OcrResult(
    val text: String,
    val success: Boolean,
    val errorMessage: String? = null
)

interface OcrEngine {
    val isAvailable: Boolean
    suspend fun recognizeText(imageBytes: ByteArray): OcrResult
}
