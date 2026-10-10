package com.example.data.repository

import com.example.data.api.AiProvider
import com.example.data.local.dao.CustomModelDao
import com.example.data.local.entity.CustomModelEntity
import com.example.data.repository.ModelRepository
import com.example.data.security.ApiKeyRepository
import com.example.domain.model.AiModel
import com.example.domain.model.ModelCapability
import com.example.domain.provider.AiProviderClient
import com.example.domain.provider.ProviderDescriptor
import com.example.domain.provider.ProviderModel
import com.example.domain.provider.ProviderStreamEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.atomic.AtomicReference

/** Contract tests for [ModelRepository] over a fake provider registry and in-memory DAO. */
class ModelRepositoryTest {

    private lateinit var repository: ModelRepository
    private lateinit var providerRegistry: FakeProviderRegistry
    private lateinit var customModelDao: FakeCustomModelDao
    private lateinit var apiKeyRepository: FakeApiKeyRepository

    @Before
    fun setUp() {
        providerRegistry = FakeProviderRegistry()
        customModelDao = FakeCustomModelDao()
        apiKeyRepository = FakeApiKeyRepository()
        repository = ModelRepository(
            providerRegistry = providerRegistry,
            customModelDao = customModelDao,
            apiKeyRepository = apiKeyRepository
        )
    }

    @Test
    fun refreshModelsReturnsEmptyWhenNoProviderIsConfigured() = runBlocking {
        val result = repository.refreshModels()
        assertFalse(result.isSuccess)
        assertTrue(repository.isLoading.value)
        assertTrue(repository.allModels.value.isEmpty())
    }

    @Test
    fun refreshModelsCollectsModelsFromConfiguredProviders() = runBlocking {
        val hcnsecClient = FakeClient(
            descriptor = ProviderDescriptor(
                id = AiProvider.HCNSEC.name,
                displayName = "HCNSEC",
                description = "Official HCNSEC OpenAI-compatible API"
            ),
            models = listOf(
                ProviderModel("hcnsec-reasoner", displayName = "HCNSEC Reasoner", capabilities = setOf(ModelCapability.REASONING)),
                ProviderModel("hcnsec-chat", displayName = "HCNSEC Chat", capabilities = setOf(ModelCapability.FAST_CHAT))
            )
        )
        providerRegistry.register(hcnsecClient)
        apiKeyRepository.configure(AiProvider.HCNSEC, "hc-test-key")

        val result = repository.refreshModels()
        assertTrue(result.isSuccess)
        assertEquals(2, result.getOrDefault(emptyList()).size)
        assertEquals(2, repository.allModels.value.size)
        assertTrue(repository.allModels.value.any { it.providerId == AiProvider.HCNSEC.name })
    }

    @Test
    fun oneFailedProviderDoesNotHideModelsFromOtherProviders() = runBlocking {
        val routerClient = FakeClient(
            descriptor = ProviderDescriptor(
                id = AiProvider.OPEN_ROUTER.name,
                displayName = "OpenRouter",
                description = "Many free and paid models through one API"
            ),
            models = listOf(
                ProviderModel("openrouter-pro", displayName = "OpenRouter Pro", capabilities = setOf(ModelCapability.CODING))
            )
        )
        providerRegistry.register(routerClient)
        apiKeyRepository.configure(AiProvider.OPEN_ROUTER, "or-test-key")

        val failingClient = FakeClient(
            descriptor = ProviderDescriptor(
                id = AiProvider.GROQ.name,
                displayName = "Groq",
                description = "Fast OpenAI-compatible inference"
            ),
            models = emptyList(),
            listModelsFailure = RuntimeException("network blip")
        )
        providerRegistry.register(failingClient)
        apiKeyRepository.configure(AiProvider.GROQ, "g-roq-key")

        val result = repository.refreshModels()
        assertTrue(result.isSuccess)
        assertEquals(1, repository.allModels.value.size)
        assertEquals("openrouter-pro", repository.allModels.value.first().id)
        assertTrue(repository.errorMessage.value?.contains("Groq") == true)
    }

    @Test
    fun customModelsAreMergedWithApiModels() = runBlocking {
        val client = FakeClient(
            descriptor = ProviderDescriptor(
                id = AiProvider.HCNSEC.name,
                displayName = "HCNSEC",
                description = "Official HCNSEC OpenAI-compatible API"
            ),
            models = listOf(
                ProviderModel("hcnsec-chat", displayName = "HCNSEC Chat", capabilities = setOf(ModelCapability.FAST_CHAT))
            )
        )
        providerRegistry.register(client)
        apiKeyRepository.configure(AiProvider.HCNSEC, "hc-key")

        customModelDao.insertAll(
            CustomModelEntity("my-custom-model", displayName = "My Custom Model", isFavorite = true),
            CustomModelEntity("shared-id", displayName = "Shared ID", isFavorite = false)
        )
        customModelDao.favorites = setOf("my-custom-model", "shared-id")

        val result = repository.refreshModels()
        assertTrue(result.isSuccess)
        val models = repository.allModels.value
        assertEquals(3, models.size)
        val custom = models.first { it.id == "my-custom-model" }
        assertTrue(custom.isCustom)
        assertTrue(custom.isFavorite)
        assertEquals(AiProvider.CUSTOM.name, custom.providerId)
    }

    @Test
    fun duplicateModelIdsAcrossProvidersAreDeduplicatedBySelectionKey() = runBlocking {
        val providerA = FakeClient(
            descriptor = ProviderDescriptor(
                id = AiProvider.GROQ.name,
                displayName = "Groq",
                description = "Fast OpenAI-compatible inference"
            ),
            models = listOf(ProviderModel("llama-3.1-8b", displayName = "Llama 3.1 8B", capabilities = setOf(ModelCapability.FAST_CHAT)))
        )
        val providerB = FakeClient(
            descriptor = ProviderDescriptor(
                id = AiProvider.OPEN_ROUTER.name,
                displayName = "OpenRouter",
                description = "Many free and paid models through one API"
            ),
            models = listOf(ProviderModel("llama-3.1-8b", displayName = "Llama 3.1 8B", capabilities = setOf(ModelCapability.CODING)))
        )
        providerRegistry.register(providerA)
        providerRegistry.register(providerB)
        apiKeyRepository.configure(AiProvider.GROQ, "g-key")
        apiKeyRepository.configure(AiProvider.OPEN_ROUTER, "or-key")

        val result = repository.refreshModels()
        assertTrue(result.isSuccess)
        assertEquals(2, repository.allModels.value.size)
        assertEquals("GROQ::llama-3.1-8b", repository.allModels.value.first { it.providerId == AiProvider.GROQ.name }.selectionKey)
        assertEquals("OPEN_ROUTER::llama-3.1-8b", repository.allModels.value.first { it.providerId == AiProvider.OPEN_ROUTER.name }.selectionKey)
    }

    @Test
    fun customModelTableDoesNotDuplicateProviderListedModel() = runBlocking {
        val client = FakeClient(
            descriptor = ProviderDescriptor(
                id = AiProvider.HCNSEC.name,
                displayName = "HCNSEC",
                description = "Official HCNSEC OpenAI-compatible API"
            ),
            models = listOf(ProviderModel("hcnsec-chat", displayName = "HCNSEC Chat", capabilities = emptySet()))
        )
        providerRegistry.register(client)
        apiKeyRepository.configure(AiProvider.HCNSEC, "hc-key")
        customModelDao.insertAll(CustomModelEntity("hcnsec-chat", displayName = "Manual copy", isFavorite = false))

        val result = repository.refreshModels()
        assertTrue(result.isSuccess)
        assertEquals(1, repository.allModels.value.size)
        assertEquals("hcnsec-chat", repository.allModels.value.first().id)
        assertFalse(repository.allModels.value.first().isCustom)
    }

    @Test
    fun addCustomModelPersistsAndBecomesSelectable() = runBlocking {
        repository.addCustomModel("my-test-model", "My Test Model")
        val models = repository.allModels.value
        assertEquals(1, models.size)
        assertEquals("my-test-model", models.first().id)
        assertEquals("My Test Model", models.first().displayName)
    }

    @Test
    fun deleteCustomModelRemovesItFromThePicker() = runBlocking {
        repository.addCustomModel("to-delete", "To Delete")
        repository.deleteCustomModel("to-delete")
        assertTrue(repository.allModels.value.none { it.id == "to-delete" })
    }

    @Test
    fun toggleFavoritePersistsSeparateFromCustomTableForApiModels() = runBlocking {
        val client = FakeClient(
            descriptor = ProviderDescriptor(
                id = AiProvider.HCNSEC.name,
                displayName = "HCNSEC",
                description = "Official HCNSEC OpenAI-compatible API"
            ),
            models = listOf(ProviderModel("hcnsec-chat", displayName = "HCNSEC Chat", capabilities = emptySet()))
        )
        providerRegistry.register(client)
        apiKeyRepository.configure(AiProvider.HCNSEC, "hc-key")
        repository.refreshModels()

        val model = repository.allModels.value.first { it.id == "hcnsec-chat" }
        assertFalse(model.isFavorite)
        repository.toggleFavorite(model)
        val after = repository.allModels.value.first { it.id == "hcnsec-chat" }
        assertTrue(after.isFavorite)
    }

    @Test
    fun modelsAreSortedByProviderDisplayNameThenModelName() = runBlocking {
        val c = FakeClient(
            descriptor = ProviderDescriptor(
                id = AiProvider.CEREBRAS.name,
                displayName = "Cerebras",
                description = "Very fast inference for open models"
            ),
            models = listOf(ProviderModel("c-model", displayName = "Z-Cerebras Model", capabilities = emptySet()))
        )
        val a = FakeClient(
            descriptor = ProviderDescriptor(
                id = AiProvider.OPENAI.name,
                displayName = "A-OpenAI",
                description = "Official OpenAI API"
            ),
            models = listOf(ProviderModel("a-model", displayName = "A-OpenAI Model", capabilities = emptySet()))
        )
        providerRegistry.register(a)
        providerRegistry.register(c)
        apiKeyRepository.configure(AiProvider.CEREBRAS, "c-key")
        apiKeyRepository.configure(AiProvider.OPENAI, "a-key")

        repository.refreshModels()
        val sorted = repository.allModels.value.map { it.displayName }
        assertEquals(listOf("A-OpenAI Model", "Z-Cerebras Model"), sorted)
    }

    private class FakeProviderRegistry : com.example.domain.provider.AiProviderRegistry {
        private val clients = mutableMapOf<String, AiProviderClient>()
        override val defaultProviderId: String = ""
        override fun register(client: AiProviderClient) { clients[client.descriptor.id] = client }
        override fun unregister(providerId: String) { clients.remove(providerId) }
        override fun get(providerId: String): AiProviderClient? = clients[providerId]
        override fun all(): List<com.example.domain.provider.ProviderDescriptor> =
            clients.values.map { it.descriptor }.sortedBy { it.displayName }
        override fun defaultClient(): AiProviderClient? = clients.values.minByOrNull { it.descriptor.displayName }
    }

    private class FakeCustomModelDao : CustomModelDao {
        private val items = mutableListOf<CustomModelEntity>()
        var favorites: Set<String> = emptySet()
        override fun getAllCustomModels(): Flow<List<CustomModelEntity>> = flowOf(items.toList())
        override suspend fun getCustomModelsList(): List<CustomModelEntity> = items.toList()
        override suspend fun insert(model: CustomModelEntity) {
            val existing = items.indexOfFirst { it.id == model.id }
            if (existing >= 0) items[existing] = model else items.add(model)
        }
        override suspend fun insertAll(vararg models: CustomModelEntity) { models.forEach { insert(it) } }
        override suspend fun update(model: CustomModelEntity) { insert(model) }
        override suspend fun deleteById(id: String) { items.removeAll { it.id == id } }
    }

    /** Implementable fake of [ApiKeyRepository] with no Android dependency. */
    private class FakeApiKeyRepository : ApiKeyRepository {
        private val keys = mutableMapOf<String, String>()
        private val customBaseUrl = AtomicReference<String?>(null)
        private val customModel = AtomicReference<String?>(null)
        private val favorites = AtomicReference<Set<String>>(emptySet())

        fun configure(provider: AiProvider, key: String) { keys[provider.name] = key }

        override fun saveApiKey(apiKey: String) = saveProviderKey(AiProvider.HCNSEC, apiKey)
        override fun getApiKey(): String? = getProviderKey(AiProvider.HCNSEC)
        override fun hasApiKey(): Boolean = hasProviderKey(AiProvider.HCNSEC)
        override fun getMaskedApiKey(): String = getMaskedProviderKey(AiProvider.HCNSEC)

        override fun saveProviderKey(provider: AiProvider, apiKey: String) {
            val trimmed = apiKey.trim()
            if (trimmed.isEmpty()) return
            keys[provider.name] = trimmed
        }

        override fun getProviderKey(provider: AiProvider): String? = keys[provider.name]

        override fun hasProviderKey(provider: AiProvider): Boolean = !getProviderKey(provider).isNullOrBlank()

        override fun getConfiguredProviders(): List<AiProvider> =
            AiProvider.catalog.filter { isProviderConfigured(it) }

        override fun isProviderConfigured(provider: AiProvider): Boolean = when (provider) {
            AiProvider.CUSTOM -> !getCustomBaseUrl().isNullOrBlank() && hasProviderKey(provider)
            else -> hasProviderKey(provider)
        }

        override fun hasAnyConfiguredProvider(): Boolean = getConfiguredProviders().isNotEmpty()

        override fun getActiveProvider(): AiProvider = getConfiguredProviders().firstOrNull() ?: AiProvider.HCNSEC

        override fun setActiveProvider(provider: AiProvider) {}

        override fun getProviderBaseUrl(provider: AiProvider): String? = when (provider) {
            AiProvider.CUSTOM -> getCustomBaseUrl()
            else -> provider.defaultBaseUrl
        }

        override fun getCustomBaseUrl(): String? = customBaseUrl.get()

        override fun getCustomModel(): String = customModel.get().orEmpty()

        override fun saveCustomProvider(baseUrl: String, model: String?) {
            val normalized = baseUrl.trim().removeSuffix("/")
            require(normalized.startsWith("https://")) { "Custom endpoint must use HTTPS." }
            require(normalized.length <= 240) { "Custom endpoint URL is too long." }
            customBaseUrl.set(normalized)
            customModel.set(model?.trim().orEmpty())
        }

        override fun clearCustomProvider() {
            customBaseUrl.set(null)
            customModel.set(null)
            keys.remove(AiProvider.CUSTOM.name)
        }

        override fun clearApiKey() = clearProviderKey(AiProvider.HCNSEC)

        override fun clearProviderKey(provider: AiProvider) { keys.remove(provider.name) }

        override fun getMaskedProviderKey(provider: AiProvider): String =
            getProviderKey(provider)?.let { "••••${it.takeLast(4)}" } ?: "No key configured"

        override fun isAutoRoutingEnabled(): Boolean = true

        override fun setAutoRoutingEnabled(enabled: Boolean) {}

        override fun getDefaultModel(): String = "auto"

        override fun setDefaultModel(modelId: String) {}

        override fun getFavoriteModelKeys(): Set<String> = favorites.get()

        override fun isFavoriteModelKey(key: String): Boolean = key in favorites.get()

        override fun toggleFavoriteModelKey(key: String): Boolean {
            val next = favorites.get().toMutableSet()
            val nowFavorite = key !in next
            if (nowFavorite) next.add(key) else next.remove(key)
            favorites.set(next)
            return nowFavorite
        }
    }

    private class FakeClient(
        override val descriptor: ProviderDescriptor,
        private val models: List<ProviderModel> = emptyList(),
        private val listModelsFailure: Throwable? = null
    ) : AiProviderClient {
        override suspend fun listModels(): Result<List<ProviderModel>> =
            if (listModelsFailure != null) Result.failure(listModelsFailure) else Result.success(models)

        override fun streamChat(
            modelId: String,
            messages: List<com.example.domain.provider.ProviderMessage>,
            temperature: Double
        ): Flow<ProviderStreamEvent> = flowOf(ProviderStreamEvent.Completed("stop", 10))
    }
}
