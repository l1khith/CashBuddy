package com.cashbuddy.benchmark

import android.content.Intent
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
        // --- Journey 1: Regular App Opening -> Scroll Main Feed ---
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

        // --- Journey 2: Direct Notification Review Box Entry Point ---
        pressHome()
        startActivityAndWait { intent ->
            intent.putExtra("skip_permission_prompts", true)
            intent.putExtra("EXTRA_NAVIGATE_TO", "review_inbox")
        }
        device.waitForIdle()

        // --- Journey 3: OCR / Share Sheet Receipt Intake Entry Point ---
        pressHome()
        startActivityAndWait { intent ->
            intent.action = Intent.ACTION_SEND
            intent.type = "text/plain"
            intent.putExtra(Intent.EXTRA_TEXT, "Paid Rs. 450 to Swiggy on 20-Oct-2026 Ref UPI/429819201928")
            intent.putExtra("skip_permission_prompts", true)
        }
        device.waitForIdle()
    }
}
