package com.cashbuddy.benchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val baselineRule = BaselineProfileRule()

    @Test
    fun generateBaselineProfile() = baselineRule.collect(
        packageName = "com.cashbuddy"
    ) {
        pressHome()
        startActivityAndWait { intent ->
            intent.putExtra("skip_permission_prompts", true)
        }

        val startX = device.displayWidth / 2
        val startY = (device.displayHeight * 0.7).toInt()
        val endY = (device.displayHeight * 0.3).toInt()

        device.swipe(startX, startY, startX, endY, 20)
        device.waitForIdle()
        device.swipe(startX, endY, startX, startY, 20)
        device.waitForIdle()
    }
}
