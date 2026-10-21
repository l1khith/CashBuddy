package com.cashbuddy.benchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScrollBenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun scrollHomeScreen() = benchmarkRule.measureRepeated(
        packageName = "com.cashbuddy",
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.DEFAULT,
        startupMode = StartupMode.WARM,
        iterations = 5,
        setupBlock = {
            pressHome()
            startActivityAndWait { intent ->
                intent.putExtra("skip_permission_prompts", true)
            }
        }
    ) {
        val startX = device.displayWidth / 2
        val startY = (device.displayHeight * 0.7).toInt()
        val endY = (device.displayHeight * 0.3).toInt()

        // Drag / swipe up to scroll down
        device.swipe(startX, startY, startX, endY, 20)
        device.waitForIdle()

        // Drag / swipe down to scroll back up
        device.swipe(startX, endY, startX, startY, 20)
        device.waitForIdle()
    }
}
