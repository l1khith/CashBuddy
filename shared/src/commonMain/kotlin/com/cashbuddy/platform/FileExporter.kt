package com.cashbuddy.platform

interface FileExporter {
    /**
     * Exports content to a CSV file in user-accessible storage using Scoped Storage.
     * @return Result containing the saved file location or user-facing path.
     */
    suspend fun exportCsvFile(fileName: String, content: String): Result<String>

    /**
     * Streams content to a file in user-accessible storage using Scoped Storage.
     * The [writer] lambda receives an append function `(String) -> Unit` that writes
     * chunks to the underlying file stream without holding the entire content in memory.
     */
    suspend fun exportFileStream(
        fileName: String,
        mimeType: String = "text/csv",
        writer: suspend (append: (String) -> Unit) -> Unit
    ): Result<String> = exportCsvFile(fileName, "")
}
