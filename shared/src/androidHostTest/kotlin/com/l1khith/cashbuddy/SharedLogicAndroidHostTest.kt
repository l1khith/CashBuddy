package com.l1khith.cashbuddy

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse

class SharedLogicAndroidHostTest {

    @Test
    fun `no hardcoded bank sender IDs anywhere in repo`() {
        val forbidden = listOf(
            "HDFCBK", "ICICIB", "SBIINB", "SBICRD", "AXISBK", "KOTAKB",
            "INDUSB", "YESBNK", "PNBSMS", "CANBNK", "UNIONB",
            "IDFCFB", "BOISMS", "CBISMS", "UCOBNK",
            "FEDBNK", "RBLBNK", "SCISMS", "CITIBK", "HSBCIN", "AUBANK", "BANDHN",
            "JUPITR", "FIBANK", "ONECRD", "SLICEC", "TATANEU"
        )

        var current = File(".").canonicalFile
        while (current.parentFile != null && !File(current, "settings.gradle.kts").exists()) {
            current = current.parentFile!!
        }

        val scanDirs = listOf(
            File(current, "shared/src"),
            File(current, "androidApp/src")
        ).filter { it.exists() }

        for (dir in scanDirs) {
            dir.walkTopDown()
                .filter { it.isFile && (it.extension == "kt" || it.extension == "java") }
                .forEach { file ->
                    // Exclude this guard test file itself
                    if (file.name == "SharedLogicAndroidHostTest.kt") return@forEach

                    val content = file.readText()
                    forbidden.forEach { token ->
                        assertFalse(
                            content.contains("\"$token\""),
                            "Forbidden sender ID '$token' found in ${file.path}. " +
                            "Sender IDs must never be hardcoded. Use SenderFingerprint + Evidence."
                        )
                    }
                }
        }
    }

    @Test
    fun testNoSourceFilesHaveTxtExtension() {
        var current = File(".").canonicalFile
        while (current.parentFile != null && !File(current, "settings.gradle.kts").exists()) {
            current = current.parentFile!!
        }

        val rootDirs = listOf(
            File(current, "shared/src"),
            File(current, "androidApp/src")
        ).filter { it.exists() }

        val badFiles = rootDirs.flatMap { dir ->
            dir.walkTopDown()
                .filter { it.isFile && it.extension.equals("txt", ignoreCase = true) }
                .toList()
        }
        kotlin.test.assertTrue(badFiles.isEmpty(), "Found source files with .txt extension: $badFiles")
    }

    @Test
    fun testAccuracyDatasetInSyncWithDocsCsv() {
        var current = File(".").canonicalFile
        while (current.parentFile != null && !File(current, "settings.gradle.kts").exists()) {
            current = current.parentFile!!
        }
        val csvFile = File(current, "docs/accuracy/labelled_v1.csv")
        kotlin.test.assertTrue(csvFile.exists(), "docs/accuracy/labelled_v1.csv must exist")
        val diskContent = csvFile.readText().replace("\r\n", "\n").trim()
        val embeddedContent = com.cashbuddy.core.LABELLED_V1_CSV.replace("\r\n", "\n").trim()
        kotlin.test.assertEquals(diskContent, embeddedContent, "AccuracyHarnessDataset.kt must match docs/accuracy/labelled_v1.csv")
    }
}