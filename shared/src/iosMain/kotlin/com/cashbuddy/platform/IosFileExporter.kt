package com.cashbuddy.platform

class IosFileExporter : FileExporter {
    override suspend fun exportCsvFile(fileName: String, content: String): Result<String> {
        return Result.success("Exported: $fileName")
    }
}
