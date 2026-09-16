package com.example.security

import com.example.data.api.hcnsec.HcnsecProviderConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Static secret-leakage audit (Phase 4.2 §16/§17/§20).
 *
 * These assertions are structural: they fail the build if a credential-shaped literal, a plaintext
 * endpoint, a TLS bypass or a log statement in provider code is ever introduced.
 *
 * No real credential is used here — the detectors are built from concatenated fragments so that the
 * audit file cannot match itself.
 */
class SecretLeakageAuditTest {

    private val credentialPatterns: List<Pair<String, Regex>> = listOf(
        "openai-style key" to Regex("sk" + "-[A-Za-z0-9]{16,}"),
        "hard-coded bearer token" to Regex("Bearer " + "[A-Za-z0-9._-]{20,}"),
        "aws access key id" to Regex("AKIA" + "[0-9A-Z]{16}"),
        "private key block" to Regex("-----BEGIN " + "PRIVATE KEY-----"),
        "assigned api key literal" to Regex("(?i)api[_-]?key" + """\s*[:=]\s*["'][A-Za-z0-9]{16,}"""),
    )

    @Test
    fun `main sources contain no credential shaped literal`() {
        assertNoCredentialPattern(SourceAudit.mainSourceRoot, "main")
    }

    @Test
    fun `test sources contain no credential shaped literal`() {
        // mainSourceRoot is `<module>/src/main/java`, so the test tree is `<module>/src/test/java`.
        val sourceSetRoot = SourceAudit.mainSourceRoot.parentFile?.parentFile
            ?: error("unexpected source layout: ${SourceAudit.mainSourceRoot}")
        val testRoot = java.io.File(sourceSetRoot, "test/java")

        assertTrue("expected the unit test source set at $testRoot", testRoot.isDirectory)
        assertNoCredentialPattern(testRoot, "test")
    }

    @Test
    fun `provider code never logs`() {
        val banned = listOf("Log.", "println(", "System.out", "System.err", "printStackTrace()")

        val providerFiles = SourceAudit.sourcesIn("domain/provider") +
            SourceAudit.sourcesIn("data/api/hcnsec") +
            SourceAudit.sourcesIn("data/network")

        assertTrue("the provider layer should contain files", providerFiles.isNotEmpty())

        for (file in providerFiles) {
            val text = file.readText()
            for (token in banned) {
                assertFalse(
                    "${SourceAudit.relativePath(file)} must not contain $token (it could log a credential)",
                    text.contains(token),
                )
            }
        }
    }

    @Test
    fun `no http body logging interceptor is configured anywhere`() {
        for (file in SourceAudit.kotlinSources()) {
            assertFalse(
                "${SourceAudit.relativePath(file)} must not use HttpLoggingInterceptor",
                file.readText().contains("HttpLoggingInterceptor"),
            )
        }
    }

    @Test
    fun `tls validation is never bypassed`() {
        val banned = listOf(
            "sslSocketFactory",
            "hostnameVerifier",
            "checkServerTrusted",
            "TrustAllCerts",
            "X509TrustManager",
        )

        for (file in SourceAudit.kotlinSources()) {
            // Comments are documentation; only executable references can actually bypass validation.
            val code = SourceAudit.code(file.readText())
            for (token in banned) {
                assertFalse(
                    "${SourceAudit.relativePath(file)} must not reference $token",
                    code.contains(token),
                )
            }
        }
    }

    @Test
    fun `provider endpoints are https only`() {
        assertTrue(HcnsecProviderConfig.DEFAULT_BASE_URL.startsWith("https://"))
        assertTrue(HcnsecProviderConfig().chatCompletionsUrl.startsWith("https://"))

        for (file in SourceAudit.kotlinSources()) {
            val text = file.readText()
            // `http://` (not `https://`) would be a plaintext endpoint.
            assertFalse(
                "${SourceAudit.relativePath(file)} must not contain a plaintext http endpoint",
                Regex("""["']http://""").containsMatchIn(text),
            )
        }
    }

    @Test
    fun `the manifest does not relax cleartext network policy`() {
        val manifest = File(SourceAudit.repoRoot, "app/src/main/AndroidManifest.xml")
        assertTrue("AndroidManifest.xml should exist", manifest.isFile)

        val text = manifest.readText()
        assertFalse("cleartext traffic must not be enabled", text.contains("usesCleartextTraffic=\"true\""))
        assertFalse("a custom network security config is not expected in this phase", text.contains("networkSecurityConfig"))
        assertTrue("the app needs internet permission", text.contains("android.permission.INTERNET"))
    }

    @Test
    fun `the hcnsec endpoint is defined in exactly one place`() {
        val filesWithEndpoint = SourceAudit.kotlinSources()
            .filter { it.readText().contains("api.hcnsec.cn") }
            .map { SourceAudit.relativePath(it) }

        assertEquals(
            "the HCNSEC base URL must live only in the provider config",
            listOf("com/example/data/api/hcnsec/HcnsecProviderConfig.kt"),
            filesWithEndpoint,
        )
    }

    @Test
    fun `the bearer credential is attached in exactly one place`() {
        val constructionSites = SourceAudit.kotlinSources()
            .filter { it.readText().contains("\"Authorization\" to \"Bearer ") }
            .map { SourceAudit.relativePath(it) }

        assertEquals(
            "the credential must only ever be attached by the HCNSEC adapter",
            listOf("com/example/data/api/hcnsec/HcnsecProviderAdapter.kt"),
            constructionSites,
        )
    }

    @Test
    fun `only the adapter and the redactor may name authorization headers`() {
        val filesMentioningAuth = SourceAudit.kotlinSources()
            .filter { it.readText().contains("Authorization") }
            .map { SourceAudit.relativePath(it) }
            .sorted()

        assertEquals(
            "ProviderError only names these tokens in order to redact them; no other file may handle them",
            listOf(
                "com/example/data/api/hcnsec/HcnsecProviderAdapter.kt",
                "com/example/domain/provider/ProviderError.kt",
            ),
            filesMentioningAuth,
        )
    }

    @Test
    fun `env files are git ignored and contain no real credentials`() {
        val gitignore = File(SourceAudit.repoRoot, ".gitignore").readText()
        assertTrue(".env must stay out of git", gitignore.contains(".env"))

        val envFile = File(SourceAudit.repoRoot, ".env")
        if (envFile.isFile) {
            assertNoCredentialInText(envFile.readText(), ".env")
        }

        val envExample = File(SourceAudit.repoRoot, ".env.example")
        if (envExample.isFile) {
            assertNoCredentialInText(envExample.readText(), ".env.example")
        }
    }

    private fun assertNoCredentialPattern(root: File, label: String) {
        val sources = root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        assertTrue("expected to find $label sources under $root", sources.isNotEmpty())

        for (file in sources) {
            assertNoCredentialInText(file.readText(), file.relativeTo(root).path)
        }
    }

    private fun assertNoCredentialInText(text: String, label: String) {
        for ((description, pattern) in credentialPatterns) {
            assertFalse(
                "$label must not contain a $description",
                pattern.containsMatchIn(text),
            )
        }
    }
}
