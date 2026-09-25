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
}