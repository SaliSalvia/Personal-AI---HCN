package com.example.di

import android.content.Context
import com.example.data.api.AiProvider
import com.example.data.api.DefaultAiProviderRegistry
import com.example.data.api.HcnsecApiClient
import com.example.data.api.HcnsecProviderClient
import com.example.data.local.AppDatabase
import com.example.data.repository.ChatRepository
import com.example.data.repository.ModelRepository
import com.example.data.repository.WorkspaceRepository
import com.example.data.repository.SourceDocumentRepository
import com.example.data.security.ApiKeyRepository
import com.example.data.settings.LanguageRepository
import com.example.data.workspace.ZipWorkspaceManager
import com.example.domain.router.AutoModelRouter

class AppContainer(context: Context) {
    val appContext = context.applicationContext

    val apiKeyRepository by lazy {
        ApiKeyRepository(appContext)
    }

    val languageRepository by lazy {
        LanguageRepository(appContext)
    }

    val apiClient by lazy {
        HcnsecApiClient(apiKeyRepository)
    }

    /** Provider-neutral runtime registry. Add a new client here or from a feature module. */
    val providerRegistry by lazy {
        DefaultAiProviderRegistry().also { registry ->
            AiProvider.catalog.forEach { provider ->
                registry.register(HcnsecProviderClient(apiClient, provider))
            }
        }
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
            providerRegistry = providerRegistry,
            customModelDao = database.customModelDao(),
            apiKeyRepository = apiKeyRepository
        )
    }

    val chatRepository by lazy {
        ChatRepository(
            conversationDao = database.conversationDao(),
            messageDao = database.messageDao(),
            providerRegistry = providerRegistry,
            apiKeyRepository = apiKeyRepository,
            modelRouter = modelRouter,
            zipWorkspaceManager = zipWorkspaceManager
        )
    }

    val workspaceRepository by lazy {
        WorkspaceRepository(
            workspaceDao = database.workspaceDao(),
            zipWorkspaceManager = zipWorkspaceManager
        )
    }

    val sourceDocumentRepository by lazy {
        SourceDocumentRepository(appContext, database.sourceDocumentDao())
    }
}
