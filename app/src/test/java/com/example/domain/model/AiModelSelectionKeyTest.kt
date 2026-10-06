package com.example.domain.model

import com.example.data.api.AiProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class AiModelSelectionKeyTest {

    @Test
    fun sameModelIdOnDifferentProvidersDoesNotCollide() {
        val groq = AiModel(id = "llama-3.1-8b", displayName = "Llama 3.1 8B", providerId = AiProvider.GROQ.name)
        val openRouter = AiModel(id = "llama-3.1-8b", displayName = "Llama 3.1 8B", providerId = AiProvider.OPEN_ROUTER.name)

        assertNotEquals(groq.selectionKey, openRouter.selectionKey)
        assertEquals("GROQ::llama-3.1-8b", groq.selectionKey)
        assertEquals("OPEN_ROUTER::llama-3.1-8b", openRouter.selectionKey)
    }

    @Test
    fun blankProviderIdFallsBackToPlainModelId() {
        val legacy = AiModel(id = "deepseek-chat", displayName = "DeepSeek Chat")
        assertEquals("deepseek-chat", legacy.selectionKey)
    }
}
