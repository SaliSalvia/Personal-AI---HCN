package com.example.data.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiProviderCatalogTest {
    @Test
    fun catalogContainsHcnsecAndCommonFreeTierProfiles() {
        val providers = AiProvider.catalog
        assertTrue(providers.contains(AiProvider.HCNSEC))
        assertTrue(providers.contains(AiProvider.GROQ))
        assertTrue(providers.contains(AiProvider.OPEN_ROUTER))
        assertTrue(providers.contains(AiProvider.CEREBRAS))
        assertTrue(providers.contains(AiProvider.CUSTOM))
        assertEquals("https://api.cerebras.ai/v1", AiProvider.CEREBRAS.defaultBaseUrl)
    }

    @Test
    fun commonKeyPrefixesAreDetectedWithoutExposingTheKey() {
        assertEquals(AiProvider.GOOGLE_AI_STUDIO, AiProvider.detectFromKey("  AIza-test-key"))
        assertEquals(AiProvider.GROQ, AiProvider.detectFromKey("gsk_test-key"))
        assertEquals(AiProvider.OPEN_ROUTER, AiProvider.detectFromKey("sk-or-test-key"))
    }
}
