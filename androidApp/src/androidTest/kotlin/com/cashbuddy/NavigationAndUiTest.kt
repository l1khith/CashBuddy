package com.cashbuddy

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NavigationAndUiTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun test_BottomNavigation_TabSwitching() {
        // 1. Verify Floating Pill navigation bar is present
        composeTestRule.onNodeWithContentDescription("Home").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Stats").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("History").assertIsDisplayed()

        // Click "Home" tab -> Verify Home screen is displayed
        composeTestRule.onNodeWithContentDescription("Home").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNode(hasText("Recent Transactions", substring = true)).assertIsDisplayed()

        // Click "Stats" tab -> Verify Stats screen title is displayed
        composeTestRule.onNodeWithContentDescription("Stats").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNode(hasText("Analytics & Insights", substring = true)).assertIsDisplayed()

        // Click "History" tab -> Verify History screen title is displayed
        composeTestRule.onNodeWithContentDescription("History").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNode(hasText("Transactions", substring = true)).assertIsDisplayed()
    }

    @Test
    fun test_AddTransaction_SaveFlow() {
        // 1. Tap the "+" FAB
        composeTestRule.onNodeWithContentDescription("Add Transaction").performClick()
        composeTestRule.waitForIdle()

        // 2. Enter Amount "150" using custom numpad
        composeTestRule.onNodeWithText("1").performClick()
        composeTestRule.onNodeWithText("5").performClick()
        composeTestRule.onNodeWithText("0").performClick()

        // 3. Enter Merchant "Swiggy"
        composeTestRule.onNode(hasText("Merchant / Description", substring = true))
            .performTextInput("Swiggy")

        // 4. Select Category "Food & Dining"
        composeTestRule.onNodeWithText("Food & Dining").performClick()

        // 5. Tap "Save"
        composeTestRule.onNodeWithText("Save Transaction").performClick()
        composeTestRule.waitForIdle()

        // 6. Verify the app navigates back and the transaction "Swiggy" appears in the transaction list
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodes(hasText("Swiggy", substring = true))
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNode(hasText("Swiggy", substring = true)).assertIsDisplayed()
    }

    @Test
    fun test_SettingsScreen_ToggleHealthIcon() {
        // 1. Navigate to Settings
        composeTestRule.onNodeWithContentDescription("Settings").performClick()
        composeTestRule.waitForIdle()

        // Verify Settings screen loaded
        composeTestRule.onNode(hasText("Settings", substring = true)).assertIsDisplayed()

        // 2. Toggle "Show Health Icon in Top Bar" ON
        composeTestRule.onNodeWithText("Show Health Icon in Top Bar").assertIsDisplayed()
        // Click the toggle switch in the diagnostics section
        composeTestRule.onNode(isToggleable()).performClick()
        composeTestRule.waitForIdle()

        // 3. Navigate back to Home
        composeTestRule.activityRule.scenario.onActivity {
            it.onBackPressedDispatcher.onBackPressed()
        }
        composeTestRule.waitForIdle()

        // 4. Verify the Health Shield icon is present in the Top Bar
        composeTestRule.onNode(
            hasContentDescription("System Health", substring = true) or
                hasContentDescription("Notification Listener", substring = true) or
                hasContentDescription("System Alert", substring = true)
        ).assertIsDisplayed()
    }
}
