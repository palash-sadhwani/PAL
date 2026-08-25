package com.palash.pal.ui.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test

class SettingsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun showsPrivacyAndAiInformation() {
        composeRule.setContent {
            SettingsScreen(
                uiState = SettingsUiState(),
                onBack = {}
            )
        }

        composeRule.onNodeWithText("PAL").assertIsDisplayed()
        composeRule.onNodeWithText("Foundation Mode").assertIsDisplayed()
        composeRule.onNodeWithText("Not configured").assertIsDisplayed()
    }
}
