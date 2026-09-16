package com.example.di

import android.content.Context
import com.example.data.api.hcnsec.HcnsecProviderAdapter
import com.example.data.api.hcnsec.HcnsecProviderConfig
import com.example.data.api.hcnsec.HcnsecProviderGateway
import com.example.data.local.AppDatabase
import com.example.data.network.OkHttpProviderTransport
import com.example.data.network.ProviderHttpTransport
import com.example.data.repository.ChatRepository
import com.example.data.repository.ModelRepository
import com.example.data.repository.WorkspaceRepository
import com.example.data.security.ApiKeyRepository
import com.example.data.security.ProviderCredentialSource
import com.example.data.security.StoredApiKeyCredentialSource
import com.example.data.workspace.ZipWorkspaceManager
import com.example.domain.provider.ProviderCircuitBreaker
import com.example.domain.provider.ProviderEntry
import com.example.domain.provider.ProviderHealthTracker
import com.example.domain.provider.ProviderId
import com.example.domain.provider.ProviderRegistry
import com.example.domain.provider.TestProviderConnection
import com.example.domain.router.AutoModelRouter

/**
 * Dependency graph for the app.
 *
 * Phase 4.2 wiring: the HCNSEC provider is constructed with its credential abstraction, HTTPS
 * transport, health tracker and circuit breaker, then registered in the single
 * [ProviderRegistry]. Later-phase providers are registered as reserved, disabled slots.
 */
class AppContainer(context: Context) {
    val appContext = context.applicationContext

    val apiKeyRepository by lazy {
        ApiKeyRepository(appContext)
    }

    /**
     * Credential boundary: the only bridge between provider code and hardware-backed storage.
     * Provider adapters depend on this abstraction and never on [ApiKeyRepository] directly.
     */
    val credentialSource: ProviderCredentialSource by lazy {
        StoredApiKeyCredentialSource(apiKeyRepository)
    }

    val hcnsecConfig: HcnsecProviderConfig by lazy {
        HcnsecProviderConfig(
            // The user's selected model drives the provider default; "auto" stays a router
            // sentinel and is never sent as a literal model id.
            defaultModel = apiKeyRepository.getDefaultModel(),
        )
    }

    /** HTTPS-only transport with strict timeouts. No logging interceptor, no TLS overrides. */
    val providerTransport: ProviderHttpTransport by lazy {
        OkHttpProviderTransport()
    }

    val hcnsecHealth by lazy { ProviderHealthTracker() }

    val hcnsecCircuitBreaker by lazy { ProviderCircuitBreaker() }

    val hcnsecAdapter by lazy {
        HcnsecProviderAdapter(
            credentials = credentialSource,
            transport = providerTransport,
            health = hcnsecHealth,
            circuitBreaker = hcnsecCircuitBreaker,
            config = hcnsecConfig,
        )
    }

    /** Single source of truth for provider definitions (Phase 4.2 §12). */
    val providerRegistry: ProviderRegistry by lazy {
        ProviderRegistry()
            .register(
                ProviderEntry(
                    id = ProviderId.HCNSEC,
                    priority = 0,
                    enabled = true,
                    adapter = hcnsecAdapter,
                    capabilities = hcnsecAdapter.capabilities,
                    health = hcnsecHealth,
                    circuitBreaker = hcnsecCircuitBreaker,
                    isConfigured = { apiKeyRepository.hasApiKey() },
                    configuredModel = {
                        apiKeyRepository.getDefaultModel()
                            .takeIf { it.isNotBlank() && it != HcnsecProviderConfig.AUTO_MODEL }
                    },
                ),
            )
            // Reserved foundation-only slots for later phases: identity and enabled state are
            // preserved, but they have no adapter and are permanently disabled.
            .registerReserved(ProviderId.GEMINI, priority = 10)
            .registerReserved(ProviderId.GROQ, priority = 20)
            .registerReserved(ProviderId.MISTRAL, priority = 30)
            .registerReserved(ProviderId.YOU_COM, priority = 40)
    }

    val testProviderConnection by lazy {
        TestProviderConnection(registry = providerRegistry)
    }

    val hcnsecGateway by lazy {
        HcnsecProviderGateway(
            registry = providerRegistry,
            hcnsecAdapter = hcnsecAdapter,
            connectionTester = testProviderConnection,
        )
    }

    val database by lazy {
        AppDatabase.getInstance(appContext)
    }

    val modelRouter by lazy {
        AutoModelRouter()
    }

    val zipWorkspaceManager by lazy {
        ZipWorkspaceManager(appContext)
    }

    val modelRepository by lazy {
        ModelRepository(
            providerGateway = hcnsecGateway,
            customModelDao = database.customModelDao(),
        )
    }

    val chatRepository by lazy {
        ChatRepository(
            conversationDao = database.conversationDao(),
            messageDao = database.messageDao(),
            providerGateway = hcnsecGateway,
            modelRouter = modelRouter,
            zipWorkspaceManager = zipWorkspaceManager,
        )
    }

    val workspaceRepository by lazy {
        WorkspaceRepository(
            workspaceDao = database.workspaceDao(),
            zipWorkspaceManager = zipWorkspaceManager,
        )
    }
}
