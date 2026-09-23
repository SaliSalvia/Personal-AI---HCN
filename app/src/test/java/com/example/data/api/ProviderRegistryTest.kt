package com.example.data.api

import com.example.domain.provider.ProviderDescriptor
import com.example.domain.provider.ProviderStreamEvent
import com.example.domain.provider.ProviderModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderRegistryTest {
    @Test
    fun `registry defaults to HCNSEC id`() {
        val registry = DefaultAiProviderRegistry()
        assertEquals(AiProvider.HCNSEC.name, registry.defaultProviderId)
    }

    @Test
    fun `register and resolve provider client`() {
        val registry = DefaultAiProviderRegistry()
        val client = FakeClient(
            descriptor = ProviderDescriptor(
                id = AiProvider.HCNSEC.name,
                displayName = "HCNSEC",
                description = "Official HCNSEC OpenAI-compatible API"
            )
        )
        registry.register(client)
        assertEquals(client, registry.get(AiProvider.HCNSEC.name))
        assertTrue(registry.all().any { it.id == AiProvider.HCNSEC.name })
    }

    @Test
    fun `unregister removes provider client`() {
        val registry = DefaultAiProviderRegistry()
        val descriptor = ProviderDescriptor(
            id = AiProvider.OPENAI.name,
            displayName = "OpenAI",
            description = "Official OpenAI API"
        )
        registry.register(FakeClient(descriptor))
        assertTrue(registry.get(AiProvider.OPENAI.name) != null)
        registry.unregister(AiProvider.OPENAI.name)
        assertTrue(registry.get(AiProvider.OPENAI.name) == null)
    }

    @Test
    fun `all providers sorted by display name`() {
        val registry = DefaultAiProviderRegistry()
        registry.register(FakeClient(ProviderDescriptor(
            id = AiProvider.OPENAI.name,
            displayName = "Z-OpenAI",
            description = "Official OpenAI API"
        )))
        registry.register(FakeClient(ProviderDescriptor(
            id = AiProvider.GROQ.name,
            displayName = "A-Groq",
            description = "Fast OpenAI-compatible inference"
        )))
        val names = registry.all().map { it.displayName }
        assertEquals(listOf("A-Groq", "Z-OpenAI"), names)
    }

    @Test
    fun `default client returns registered client for default id`() {
        val registry = DefaultAiProviderRegistry()
        val hcnsecClient = FakeClient(ProviderDescriptor(
            id = AiProvider.HCNSEC.name,
            displayName = "HCNSEC",
            description = "Official HCNSEC OpenAI-compatible API"
        ))
        registry.register(hcnsecClient)
        assertEquals(hcnsecClient, registry.defaultClient())
    }

    @Test
    fun `provider model contracts are stable`() {
        val model = ProviderModel(
            id = "test-model",
            displayName = "Test Model",
            capabilities = emptySet(),
            description = "A model"
        )
        assertNotNull(model.id)
        assertEquals("Test Model", model.displayName)
    }

    @Test
    fun `stream event contracts are stable`() {
        val content = ProviderStreamEvent.Content("hello")
        val reasoning = ProviderStreamEvent.Reasoning("thinking")
        val completed = ProviderStreamEvent.Completed("stop", 100)
        val error = ProviderStreamEvent.Error("fail", 500)
        assertTrue(content is ProviderStreamEvent.Content)
        assertTrue(reasoning is ProviderStreamEvent.Reasoning)
        assertTrue(completed is ProviderStreamEvent.Completed)
        assertTrue(error is ProviderStreamEvent.Error)
    }

    @Test
    fun `stream contract is a flow of events`() {
        val registry = DefaultAiProviderRegistry()
        registry.register(FakeClient(ProviderDescriptor(
            id = AiProvider.HCNSEC.name,
            displayName = "HCNSEC",
            description = "Official HCNSEC OpenAI-compatible API"
        )))
        val client = registry.get(AiProvider.HCNSEC.name)!!
        val events = client.streamChat(
            modelId = "model",
            messages = listOf(
                com.example.domain.provider.ProviderMessage("user", "hi")
            ),
            temperature = 0.7
        )
        assertTrue(events is Flow<ProviderStreamEvent>)
    }

    class FakeClient(
        override val descriptor: ProviderDescriptor
    ) : com.example.domain.provider.AiProviderClient {
        override suspend fun listModels(): Result<List<ProviderModel>> =
            Result.success(listOf(ProviderModel(id = "model")))

        override fun streamChat(
            modelId: String,
            messages: List<com.example.domain.provider.ProviderMessage>,
            temperature: Double
        ): Flow<ProviderStreamEvent> = flowOf(
            ProviderStreamEvent.Content("ok"),
            ProviderStreamEvent.Completed("stop", 10)
        )
    }
}
