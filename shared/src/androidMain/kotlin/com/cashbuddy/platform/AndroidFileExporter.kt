package com.cashbuddy.platform

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class AndroidFileExporter(private val context: Context) : FileExporter {

    override suspend fun exportFileStream(
        fileName: String,
        mimeType: String,
        writer: suspend (append: (String) -> Unit) -> Unit
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/CashBuddy")
                }

                val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                    ?: return@withContext Result.failure(Exception("Failed to create MediaStore entry"))

                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    val bufferedWriter = outputStream.bufferedWriter(Charsets.UTF_8)
                    writer { chunk ->
                        bufferedWriter.write(chunk)
                    }
                    bufferedWriter.flush()
                } ?: return@withContext Result.failure(Exception("Failed to open output stream"))

                Result.success("Saved to Downloads/CashBuddy/$fileName")
            } else {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val appDir = File(downloadsDir, "CashBuddy").apply { mkdirs() }
                val targetFile = File(appDir, fileName)

                FileOutputStream(targetFile).use { fos ->
                    val bufferedWriter = fos.bufferedWriter(Charsets.UTF_8)
                    writer { chunk ->
                        bufferedWriter.write(chunk)
                    }
                    bufferedWriter.flush()
                }

                Result.success(targetFile.absolutePath)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun exportCsvFile(fileName: String, content: String): Result<String> {
        return exportFileStream(fileName, "text/csv") { write ->
            write(content)
        }
    }
}
