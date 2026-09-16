package com.example.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Provider-boundary enforcement (Phase 4.2 §3/§4/§12/§14).
 *
 * Guarantees enforced structurally:
 * - the provider contract layer is framework-free;
 * - adapters reach credentials only through the credential abstraction;
 * - provider wire types never leak to the application layer;
 * - nothing contacts the provider because the app or a screen opened;
 * - provider definitions live only in the registry/composition root.
 */
class ProviderBoundaryTest {

    private val wireTypeNames = listOf(
        "HcnsecChatCompletionRequest",
        "HcnsecChatCompletionResponse",
        "HcnsecChatMessage",
        "HcnsecStreamChunkDto",
        "HcnsecStreamChoiceDto",
        "HcnsecStreamDeltaDto",
        "HcnsecChoiceDto",
        "HcnsecResponseMessageDto",
        "HcnsecUsageDto",
        "HcnsecApiErrorResponse",
        "HcnsecApiErrorDetail",
        "HcnsecModelDto",
        "HcnsecModelListResponse",
    )

    @Test
    fun `provider contract layer has no android or storage dependency`() {
        val files = SourceAudit.sourcesIn("domain/provider")
        assertTrue("the provider contract layer should contain files", files.isNotEmpty())

        val banned = listOf(
            "import android.",
            "SharedPreferences",
            "AndroidKeyStore",
            "KeystoreManager",
            "ApiKeyRepository",
            "SecureStore",
            "okhttp",
        )

        for (file in files) {
            val text = file.readText()
            for (token in banned) {
                assertFalse(
                    "${SourceAudit.relativePath(file)} must stay provider neutral (found $token)",
                    text.contains(token),
                )
            }
        }
    }

    @Test
    fun `hcnsec adapter reaches credentials only through the abstraction`() {
        val files = SourceAudit.sourcesIn("data/api/hcnsec")
        assertTrue(files.isNotEmpty())

        val banned = listOf("ApiKeyRepository", "KeystoreManager", "SharedPreferences", "AndroidKeyStore", "import android.")

        for (file in files) {
            val text = file.readText()
            for (token in banned) {
                assertFalse(
                    "${SourceAudit.relativePath(file)} must not touch secure storage directly (found $token)",
                    text.contains(token),
                )
            }
        }

        val usesAbstraction = files.any { it.readText().contains("ProviderCredentialSource") }
        assertTrue("the adapter must depend on the credential abstraction", usesAbstraction)
    }

    @Test
    fun `provider wire types never leave the adapter package`() {
        for (file in SourceAudit.kotlinSources()) {
            val relative = SourceAudit.relativePath(file)
            if (relative.contains("data/api/hcnsec/")) continue

            val text = file.readText()
            for (type in wireTypeNames) {
                assertFalse(
                    "$relative must not reference the provider wire type $type",
                    text.contains(type),
                )
            }
        }
    }

    @Test
    fun `http client types stay inside the transport layer`() {
        for (file in SourceAudit.kotlinSources()) {
            val relative = SourceAudit.relativePath(file)
            if (relative.contains("data/network/")) continue

            assertFalse(
                "$relative must not import okhttp directly; providers use the transport seam",
                file.readText().contains("import okhttp3."),
            )
        }
    }

    @Test
    fun `nothing calls the provider because the app opened`() {
        val applicationLayer = listOf(
            "com/example/MainActivity.kt",
            "com/example/SaliApplication.kt",
            "com/example/ui/navigation/AppNavGraph.kt",
        )

        val automaticCallTokens = listOf(
            ".testConnection(",
            ".listModels(",
            ".streamChat(",
            ".verifyEphemeralCredential(",
            ".refreshModels(",
        )

        for (path in applicationLayer) {
            val text = SourceAudit.read(path)
            for (token in automaticCallTokens) {
                assertFalse("$path must not call $token automatically", text.contains(token))
            }
        }
    }

    @Test
    fun `the chat view model does no work at construction time`() {
        val text = SourceAudit.read("com/example/ui/screens/chat/ChatViewModel.kt")

        assertFalse(
            "ChatViewModel must not perform init-time work (that used to trigger a provider call on app open)",
            text.contains("init {"),
        )
        assertTrue(
            "the model catalogue must be refreshable from a user action instead",
            text.contains("refreshModelsIfNeeded"),
        )
    }

    @Test
    fun `the api center init block performs no provider call`() {
        val text = SourceAudit.read("com/example/ui/screens/settings/SettingsViewModel.kt")
        val block = initBlockOf(text)

        assertTrue("an init block is expected for local state only", block.isNotEmpty())
        assertFalse("init must not call the provider gateway", block.contains("providerGateway."))
        assertFalse("init must not refresh models", block.contains("refreshModels"))
        assertFalse("init must not test the connection", block.contains("testConnection"))
    }

    @Test
    fun `provider definitions live only in the registry and composition root`() {
        val filesWithEntry = SourceAudit.kotlinSources()
            .filter { it.readText().contains("ProviderEntry(") }
            .map { SourceAudit.relativePath(it) }
            .sorted()

        assertEquals(
            "only the registry and the composition root may construct provider entries",
            listOf(
                "com/example/di/AppContainer.kt",
                "com/example/domain/provider/ProviderRegistry.kt",
            ),
            filesWithEntry,
        )
    }

    private fun initBlockOf(source: String): String {
        val start = source.indexOf("init {")
        if (start < 0) return ""
        val end = source.indexOf('}', start)
        if (end < 0) return ""
        return source.substring(start, end)
    }
}
