package com.cashbuddy.platform

class IosFileExporter : FileExporter {
    override suspend fun exportCsvFile(fileName: String, content: String): Result<String> {
        return Result.success("Exported: $fileName")
    }

    override suspend fun exportFileStream(
        fileName: String,
        mimeType: String,
        writer: suspend (append: (String) -> Unit) -> Unit
    ): Result<String> {
        writer {}
        return Result.success("Exported: $fileName")
    }
}
