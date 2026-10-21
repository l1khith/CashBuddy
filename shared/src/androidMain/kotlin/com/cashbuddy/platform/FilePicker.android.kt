package com.cashbuddy.platform

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun rememberFilePicker(
    mimeType: String,
    onFilePicked: (content: String?, fileName: String?) -> Unit
): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) {
            return@rememberLauncherForActivityResult
        }
        try {
            var fileName: String? = null
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1 && cursor.moveToFirst()) {
                    fileName = cursor.getString(nameIndex)
                }
            }
            val content = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                inputStream.bufferedReader(Charsets.UTF_8).readText()
            }
            onFilePicked(content, fileName)
        } catch (e: Exception) {
            android.util.Log.e("FilePicker", "Failed to read picked file", e)
            onFilePicked(null, null)
        }
    }

    return {
        launcher.launch(
            arrayOf(
                "text/csv",
                "text/comma-separated-values",
                "text/plain",
                "application/vnd.ms-excel",
                "*/*"
            )
        )
    }
}
