package com.palash.pal.ui.chat

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.palash.pal.domain.model.Message
import com.palash.pal.domain.model.MessageRole
import com.palash.pal.domain.model.MessageStatus
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ChatScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun showsEmptyStateWhenThereAreNoMessages() {
        composeRule.setContent {
            ChatScreen(
                uiState = ChatUiState(isLoading = false),
                onDraftChanged = {},
                onSendClicked = {},
                onOpenSettings = {}
            )
        }

        composeRule.onNodeWithText("Start a conversation").assertIsDisplayed()
    }

    @Test
    fun rendersMessagesAndSupportsSending() {
        var sendClicked = false
        var settingsClicked = false

        composeRule.setContent {
            var draft by remember { mutableStateOf("") }
            ChatScreen(
                uiState = ChatUiState(
                    isLoading = false,
                    draftMessage = draft,
                    messages = listOf(
                        Message(
                            id = "message-1",
                            conversationId = "conversation-1",
                            role = MessageRole.USER,
                            content = "Hello PAL",
                            createdAt = 1L,
                            status = MessageStatus.SENT
                        ),
                        Message(
                            id = "message-2",
                            conversationId = "conversation-1",
                            role = MessageRole.ASSISTANT,
                            content = "Hi there",
                            createdAt = 2L,
                            status = MessageStatus.COMPLETED
                        )
                    )
                ),
                onDraftChanged = {
                    draft = it
                },
                onSendClicked = {
                    sendClicked = true
                },
                onOpenSettings = {
                    settingsClicked = true
                }
            )
        }

        composeRule.onNodeWithText("Hello PAL").assertIsDisplayed()
        composeRule.onNodeWithText("Hi there").assertIsDisplayed()

        composeRule.onNodeWithContentDescription("Open settings").performClick()
        assertTrue(settingsClicked)

        composeRule.onNode(hasSetTextAction()).performTextInput("Test message")
        composeRule.onNodeWithContentDescription("Send").performClick()

        assertTrue(sendClicked)
        composeRule.onNodeWithText("Test message").assertIsDisplayed()
    }
}
