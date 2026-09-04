package com.cashbuddy.platform

interface FileExporter {
    /**
     * Exports content to a CSV file in user-accessible storage using Scoped Storage.
     * @return Result containing the saved file location or user-facing path.
     */
    suspend fun exportCsvFile(fileName: String, content: String): Result<String>
}
