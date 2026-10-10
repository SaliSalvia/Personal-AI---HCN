package com.example.domain.router

import com.example.data.api.AiProvider
import com.example.domain.model.AiModel
import com.example.domain.model.ModelCapability
import com.example.domain.model.TaskCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoModelRouterTest {

    private val router = AutoModelRouter()

    private fun model(id: String, provider: AiProvider, caps: Set<ModelCapability> = emptySet()) =
        AiModel(
            id = id,
            displayName = id,
            capabilities = caps,
            providerId = provider.name
        )

    @Test
    fun fastChatPrefersTheFastestProviderThenSmallestTier() {
        val cerebrasBig = model("gpt-oss-120b", AiProvider.CEREBRAS, setOf(ModelCapability.FAST_CHAT))
        val groqSmall = model("gpt-oss-20b", AiProvider.GROQ, setOf(ModelCapability.FAST_CHAT))
        val openAi = model("gpt-4.1", AiProvider.OPENAI)

        // All are eligible for general chat on some capability tier; ranking must put
        // the fastest provider first even though both are flagged FAST_CHAT.
        val pick = router.selectModel(TaskCategory.GENERAL_CHAT, listOf(cerebrasBig, groqSmall, openAi))
        assertEquals(AiProvider.CEREBRAS.name, pick?.providerId)
    }

    @Test
    fun withinASingleProviderTheFastChatFlagWins() {
        val cerebrasSmall = model("qwen-3.8-27b", AiProvider.CEREBRAS, setOf(ModelCapability.FAST_CHAT))
        val cerebrasBig = model("gpt-oss-120b", AiProvider.CEREBRAS)

        val pick = router.selectModel(TaskCategory.GENERAL_CHAT, listOf(cerebrasBig, cerebrasSmall))
        assertEquals(cerebrasSmall.id, pick?.id)
    }

    @Test
    fun reasoningKeepsReasoningModelsEvenIfSlowerProvider() {
        val groqFastGeneral = model("gpt-oss-20b", AiProvider.GROQ, setOf(ModelCapability.FAST_CHAT))
        val cerebrasReasoning = model("deepseek-r1", AiProvider.CEREBRAS, setOf(ModelCapability.REASONING))

        val pick = router.selectModel(TaskCategory.REASONING, listOf(groqFastGeneral, cerebrasReasoning))
        assertEquals(cerebrasReasoning.id, pick?.id)
    }

    @Test
    fun codingPrefersCodingCapabilityRegardlessOfSpeedTier() {
        val groqFastGeneral = model("gpt-oss-20b", AiProvider.GROQ, setOf(ModelCapability.FAST_CHAT))
        val openAiCoder = model("deepseek-coder", AiProvider.OPENAI, setOf(ModelCapability.CODING))

        val pick = router.selectModel(TaskCategory.CODING, listOf(groqFastGeneral, openAiCoder))
        assertEquals(openAiCoder.id, pick?.id)
    }

    @Test
    fun summaryTasksRouteToFastChatLikeGeneralChat() {
        val fast = model("gemini-2.0-flash", AiProvider.GOOGLE_AI_STUDIO, setOf(ModelCapability.FAST_CHAT))
        val big = model("gemini-1.5-pro", AiProvider.GOOGLE_AI_STUDIO)

        val pick = router.selectModel(TaskCategory.SUMMARIZATION, listOf(big, fast))
        assertEquals(fast.id, pick?.id)
    }

    @Test
    fun returnsNullForAnEmptyCatalog() {
        assertEquals(null, router.selectModel(TaskCategory.GENERAL_CHAT, emptyList()))
    }

    @Test
    fun frontierModelIdsDetectIntendedCapabilities() {
        val gptOssSmall = CapabilityRegistry.detectCapabilities("gpt-oss-20b")
        assertTrue(ModelCapability.FAST_CHAT in gptOssSmall)
        assertTrue(ModelCapability.CODING in gptOssSmall)

        val gptOssLarge = CapabilityRegistry.detectCapabilities("gpt-oss-120b")
        assertTrue(ModelCapability.CODING in gptOssLarge)
        assertTrue(ModelCapability.FAST_CHAT !in gptOssLarge)

        val cerebrasSmall = CapabilityRegistry.detectCapabilities("qwen-3.8-27b")
        assertTrue(ModelCapability.FAST_CHAT in cerebrasSmall)
        assertTrue(ModelCapability.LARGE_CONTEXT in cerebrasSmall)

        val nemotron = CapabilityRegistry.detectCapabilities("nvidia/nemotron-3.5-lightning-30b-a3b")
        assertTrue(ModelCapability.FAST_CHAT in nemotron)
        assertTrue(ModelCapability.LARGE_CONTEXT in nemotron)

        val flashLite = CapabilityRegistry.detectCapabilities("gemini-2.0-flash-lite")
        assertTrue(ModelCapability.FAST_CHAT in flashLite)
        assertTrue(ModelCapability.LARGE_CONTEXT in flashLite)
    }
}
