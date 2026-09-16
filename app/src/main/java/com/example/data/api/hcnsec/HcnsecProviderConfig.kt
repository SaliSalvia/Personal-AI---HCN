package com.example.data.api.hcnsec

/**
 * HCNSEC provider configuration.
 *
 * The base URL is fixed to the documented HCNSEC OpenAI-compatible endpoint. HTTPS is mandatory
 * and enforced in the constructor, so a plaintext endpoint cannot be configured at all
 * (Phase 4.2 §17).
 *
 * Model identifiers are configuration, never hard-coded requirements: `defaultModel` comes from
 * the existing app configuration surface (the user's selected model) and any additional
 * identifiers can be supplied through [configuredModels].
 */
data class HcnsecProviderConfig(
    val baseUrl: String = DEFAULT_BASE_URL,
    val defaultModel: String? = null,
    val configuredModels: List<String> = emptyList(),
    val requestTimeoutMillis: Long = DEFAULT_REQUEST_TIMEOUT_MILLIS,
    val connectionTestTimeoutMillis: Long = DEFAULT_CONNECTION_TEST_TIMEOUT_MILLIS,
) {
    init {
        require(baseUrl.startsWith(HTTPS_PREFIX, ignoreCase = true)) {
            "HCNSEC base URL must use HTTPS; plaintext HTTP fallback is not permitted."
        }
        require(requestTimeoutMillis > 0) { "requestTimeoutMillis must be greater than zero." }
        require(connectionTestTimeoutMillis > 0) { "connectionTestTimeoutMillis must be greater than zero." }
    }

    /** `POST` endpoint for chat completions. */
    val chatCompletionsUrl: String get() = trimmedBaseUrl + CHAT_COMPLETIONS_PATH

    /** `GET` endpoint used to list the models the account can access. */
    val modelsUrl: String get() = trimmedBaseUrl + MODELS_PATH

    private val trimmedBaseUrl: String get() = baseUrl.trimEnd('/')

    /**
     * Resolves the model identifier to send.
     *
     * Precedence: explicit request model, then the configured default model, then the first
     * configured identifier. The router sentinel `"auto"` is treated as "not specified" so that it
     * can never be transmitted as a literal model id. Returns null when nothing is configured —
     * callers must then fail with a normalized "not configured" error instead of inventing a
     * model.
     */
    fun resolveModel(requested: String?): String? =
        sanitize(requested)
            ?: sanitize(defaultModel)
            ?: configuredModels.asSequence().mapNotNull { sanitize(it) }.firstOrNull()

    private fun sanitize(candidate: String?): String? = candidate
        ?.trim()
        ?.takeIf { it.isNotEmpty() && !it.equals(AUTO_MODEL, ignoreCase = true) }

    companion object {
        const val DEFAULT_BASE_URL: String = "https://api.hcnsec.cn/v1"
        const val CHAT_COMPLETIONS_PATH: String = "/chat/completions"
        const val MODELS_PATH: String = "/models"
        const val AUTO_MODEL: String = "auto"
        const val DEFAULT_REQUEST_TIMEOUT_MILLIS: Long = 120_000L
        const val DEFAULT_CONNECTION_TEST_TIMEOUT_MILLIS: Long = 10_000L

        private const val HTTPS_PREFIX = "https://"
    }
}
