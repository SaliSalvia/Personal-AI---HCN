package com.example.ui.screens.chat

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.example.ui.screens.chat.components.ChatInputBar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ChatInputBarTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun chatInputBar_displaysInputAndTriggersSend() {
        var enteredText by mutableStateOf("")
        var sendClicked = false

        composeTestRule.setContent {
            ChatInputBar(
                inputText = enteredText,
                onInputChanged = { enteredText = it },
                isGenerating = false,
                selectedModelDisplay = "DeepSeek-Chat",
                onOpenModelSelector = {},
                onAttachFile = {},
                onAttachZip = {},
                onSend = { sendClicked = true },
                onStop = {}
            )
        }

        // Verify elements exist
        composeTestRule.onNodeWithTag("chat_input_bar").assertIsDisplayed()
        composeTestRule.onNodeWithTag("chat_text_input").assertIsDisplayed()
        composeTestRule.onNodeWithTag("send_button").assertIsDisplayed()

        // Type text
        composeTestRule.onNodeWithTag("chat_text_input").performTextInput("Hello AI Assistant")
        assertEquals("Hello AI Assistant", enteredText)

        // Click send button
        composeTestRule.onNodeWithTag("send_button").performClick()
        assertTrue(sendClicked)
    }

    @Test
    fun chatInputBar_displaysStopButtonWhenGenerating() {
        var stopClicked = false

        composeTestRule.setContent {
            ChatInputBar(
                inputText = "",
                onInputChanged = {},
                isGenerating = true,
                selectedModelDisplay = "DeepSeek-R1",
                onOpenModelSelector = {},
                onAttachFile = {},
                onAttachZip = {},
                onSend = {},
                onStop = { stopClicked = true }
            )
        }

        composeTestRule.onNodeWithTag("stop_generation_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("stop_generation_button").performClick()
        assertTrue(stopClicked)
    }
}
